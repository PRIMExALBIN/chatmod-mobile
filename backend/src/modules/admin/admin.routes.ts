import type { Prisma, SubscriptionStatus } from "@prisma/client";
import type { FastifyInstance, FastifyReply, FastifyRequest } from "fastify";
import { timingSafeEqual } from "node:crypto";
import { z } from "zod";
import { getPrismaClient, shouldUsePrisma } from "../../db/prisma.js";
import type { AuthContext } from "../auth/sessionToken.js";
import { planForProduct, snapshotForPlan, starterSnapshot } from "../entitlements/entitlementPlans.js";
import { createEntitlementStore, type EntitlementStore } from "../entitlements/entitlementStore.js";
import { createApiErrorStore, type ApiErrorStore } from "../logs/apiErrorStore.js";
import { createLogStore, type LogStore } from "../logs/logStore.js";

const adminKeyHeader = "x-admin-api-key";

const supportLookupQuerySchema = z.object({
  deviceId: z.string().min(1),
  installId: z.string().min(1).optional()
});

const manualEntitlementSchema = z.object({
  deviceId: z.string().min(1),
  installId: z.string().min(1).optional(),
  plan: z.enum(["starter", "pro", "creator"]),
  status: z.enum(["trialing", "active", "past_due", "canceled", "expired"]).default("active"),
  currentPeriodEndsAt: z.string().datetime().nullable().optional(),
  productId: z.string().min(1).optional(),
  ticketId: z.string().min(1).max(120).optional(),
  note: z.string().max(500).optional()
});

const supportTicketMetadataSchema = z.object({
  deviceId: z.string().min(1),
  installId: z.string().min(1).optional(),
  ticketId: z.string().min(1).max(120),
  status: z.enum(["open", "triaging", "waiting", "closed"]).default("open"),
  priority: z.enum(["p0", "p1", "p2", "p3"]).default("p2"),
  tags: z.array(z.string().min(1).max(40)).max(12).default([]),
  note: z.string().max(1000).optional()
});

export interface AdminRoutesOptions {
  adminApiKey: string;
  entitlementStore?: EntitlementStore;
  supportStore?: LogStore;
  apiErrorStore?: ApiErrorStore;
}

export async function adminRoutes(app: FastifyInstance, options: AdminRoutesOptions): Promise<void> {
  const entitlementStore = options.entitlementStore ?? createEntitlementStore();
  const supportStore = options.supportStore ?? createLogStore();
  const apiErrorStore = options.apiErrorStore ?? createApiErrorStore();
  const requireAdmin = createAdminPreHandler(options.adminApiKey);

  app.get("/support/users", { preHandler: requireAdmin }, async (request) => {
    const query = supportLookupQuerySchema.parse(request.query);
    return supportSnapshot({
      auth: adminAuthContext(query.deviceId, query.installId),
      entitlementStore,
      supportStore,
      apiErrorStore
    });
  });

  app.get("/support/devices/:deviceId", { preHandler: requireAdmin }, async (request) => {
    const params = z.object({ deviceId: z.string().min(1) }).parse(request.params);
    return supportSnapshot({
      auth: adminAuthContext(params.deviceId),
      entitlementStore,
      supportStore,
      apiErrorStore
    });
  });

  app.get("/support/subscriptions/:deviceId", { preHandler: requireAdmin }, async (request) => {
    const params = z.object({ deviceId: z.string().min(1) }).parse(request.params);
    const auth = adminAuthContext(params.deviceId);
    const lookup = await prismaDeviceLookup(params.deviceId);

    return {
      deviceId: params.deviceId,
      userId: lookup?.user.id ?? null,
      entitlement: lookup?.subscription
        ? snapshotForPlan({
          plan: planForProduct(lookup.subscription.productId),
          status: lookup.subscription.status.toLowerCase() as ReturnType<typeof starterSnapshot>["status"],
          source: lookup.subscription.source,
          productId: lookup.subscription.productId,
          currentPeriodEndsAt: lookup.subscription.currentPeriodEndsAt
        })
        : await entitlementForLookup(auth, entitlementStore, lookup)
    };
  });

  app.post("/support/entitlements/manual-adjust", { preHandler: requireAdmin }, async (request, reply) => {
    const body = manualEntitlementSchema.parse(request.body);
    const auth = adminAuthContext(body.deviceId, body.installId);
    const entitlement = await entitlementStore.upsert(auth, {
      source: "admin-manual",
      status: toSubscriptionStatus(body.status),
      productId: body.productId ?? productIdForManualPlan(body.plan),
      currentPeriodEndsAt: body.currentPeriodEndsAt ? new Date(body.currentPeriodEndsAt) : null
    });

    await supportStore.create(auth, {
      severity: "warning",
      message: `Manual entitlement adjusted to ${entitlement.plan}`,
      details: {
        eventType: "admin_entitlement_adjustment",
        plan: body.plan,
        effectivePlan: entitlement.plan,
        status: body.status,
        productId: body.productId ?? productIdForManualPlan(body.plan),
        ticketId: body.ticketId ?? null,
        note: body.note ?? null
      }
    });
    await recordEntitlementAudit(body.deviceId, {
      plan: body.plan,
      effectivePlan: entitlement.plan,
      status: body.status,
      ticketId: body.ticketId ?? null,
      note: body.note ?? null
    });

    return reply.status(200).send({
      deviceId: body.deviceId,
      entitlement
    });
  });

  app.post("/support/tickets/metadata", { preHandler: requireAdmin }, async (request, reply) => {
    const body = supportTicketMetadataSchema.parse(request.body);
    const auth = adminAuthContext(body.deviceId, body.installId);
    const event = await supportStore.create(auth, {
      severity: body.priority === "p0" || body.priority === "p1" ? "warning" : "info",
      message: `Support ticket ${body.ticketId} metadata updated`,
      details: {
        eventType: "support_ticket_metadata",
        ticketId: body.ticketId,
        status: body.status,
        priority: body.priority,
        tags: body.tags,
        note: body.note ?? null
      }
    });

    return reply.status(201).send({ ticket: event });
  });
}

function createAdminPreHandler(adminApiKey: string) {
  return async (request: FastifyRequest, reply: FastifyReply): Promise<void> => {
    const provided = request.headers[adminKeyHeader];
    const providedKey = Array.isArray(provided) ? provided[0] : provided;
    if (!providedKey || !safeEqual(providedKey, adminApiKey)) {
      return reply.status(401).send({
        error: "UNAUTHORIZED",
        message: "Admin API key is required."
      });
    }
  };
}

async function supportSnapshot(input: {
  auth: AuthContext;
  entitlementStore: EntitlementStore;
  supportStore: LogStore;
  apiErrorStore: ApiErrorStore;
}) {
  const [supportEvents, apiErrors, lookup] = await Promise.all([
    input.supportStore.list(input.auth),
    input.apiErrorStore.list(input.auth),
    prismaDeviceLookup(input.auth.deviceId)
  ]);

  return {
    deviceId: input.auth.deviceId,
    installId: lookup?.device.installId ?? input.auth.installId,
    user: lookup
      ? {
        id: lookup.user.id,
        displayName: lookup.user.displayName,
        email: lookup.user.email,
        createdAt: lookup.user.createdAt.toISOString(),
        updatedAt: lookup.user.updatedAt.toISOString()
      }
      : null,
    devices: lookup?.user.devices.map((device) => ({
      id: device.id,
      deviceId: device.deviceId,
      installId: device.installId,
      appVersion: device.appVersion,
      lastSeenAt: device.lastSeenAt.toISOString(),
      createdAt: device.createdAt.toISOString()
    })) ?? [],
    linkedAccounts: lookup?.user.linkedAccounts.map((account) => ({
      id: account.id,
      provider: account.provider,
      providerAccountId: account.providerAccountId,
      channelId: account.channelId,
      channelTitle: account.channelTitle,
      hasAccessToken: Boolean(account.encryptedAccess),
      hasRefreshToken: Boolean(account.encryptedRefresh),
      createdAt: account.createdAt.toISOString(),
      updatedAt: account.updatedAt.toISOString()
    })) ?? [],
    profileCount: lookup?.user.profiles.length ?? 0,
    subscription: lookup?.subscription
      ? {
        id: lookup.subscription.id,
        source: lookup.subscription.source,
        status: lookup.subscription.status.toLowerCase(),
        productId: lookup.subscription.productId,
        currentPeriodEndsAt: lookup.subscription.currentPeriodEndsAt?.toISOString() ?? null,
        updatedAt: lookup.subscription.updatedAt.toISOString()
      }
      : null,
    entitlement: lookup?.subscription
      ? snapshotForPlan({
        plan: planForProduct(lookup.subscription.productId),
        status: lookup.subscription.status.toLowerCase() as ReturnType<typeof starterSnapshot>["status"],
        source: lookup.subscription.source,
        productId: lookup.subscription.productId,
        currentPeriodEndsAt: lookup.subscription.currentPeriodEndsAt
      })
      : await entitlementForLookup(input.auth, input.entitlementStore, lookup),
    supportEvents: supportEvents.slice(0, 20),
    apiErrors: apiErrors.slice(0, 20)
  };
}

async function entitlementForLookup(
  auth: AuthContext,
  entitlementStore: EntitlementStore,
  lookup: Awaited<ReturnType<typeof prismaDeviceLookup>>
) {
  if (shouldUsePrisma() && !lookup) {
    return starterSnapshot("admin-lookup");
  }

  return entitlementStore.current(auth);
}

async function prismaDeviceLookup(deviceId: string) {
  if (!shouldUsePrisma()) {
    return null;
  }

  const prisma = getPrismaClient();
  const device = await prisma.device.findUnique({
    where: { deviceId },
    include: {
      user: {
        include: {
          devices: { orderBy: { lastSeenAt: "desc" } },
          linkedAccounts: { orderBy: { updatedAt: "desc" } },
          profiles: { select: { id: true } },
          subscription: true
        }
      }
    }
  });

  if (!device) {
    return null;
  }

  return {
    device,
    user: device.user,
    subscription: device.user.subscription
  };
}

async function recordEntitlementAudit(
  deviceId: string,
  metadata: Record<string, unknown>
): Promise<void> {
  if (!shouldUsePrisma()) {
    return;
  }

  const prisma = getPrismaClient();
  const device = await prisma.device.findUnique({
    where: { deviceId },
    select: { userId: true }
  });
  if (!device) {
    return;
  }

  await prisma.auditLog.create({
    data: {
      userId: device.userId,
      action: "ENTITLEMENT_CHANGED",
      metadata: metadata as Prisma.InputJsonValue
    }
  });
}

function adminAuthContext(deviceId: string, installId = "admin-support-lookup"): AuthContext {
  return {
    subject: deviceId,
    deviceId,
    installId
  };
}

function productIdForManualPlan(plan: "starter" | "pro" | "creator"): string {
  return `chatmod_${plan}_manual`;
}

function toSubscriptionStatus(status: z.infer<typeof manualEntitlementSchema>["status"]): SubscriptionStatus {
  return status.toUpperCase() as SubscriptionStatus;
}

function safeEqual(left: string, right: string): boolean {
  const leftBuffer = Buffer.from(left);
  const rightBuffer = Buffer.from(right);
  return leftBuffer.length === rightBuffer.length && timingSafeEqual(leftBuffer, rightBuffer);
}
