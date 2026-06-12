import type { PrismaClient, SubscriptionStatus } from "@prisma/client";
import { getPrismaClient, shouldUsePrisma } from "../../db/prisma.js";
import { resolveUserIdForDevice } from "../accounts/deviceUser.js";
import type { AuthContext } from "../auth/sessionToken.js";
import { planForProduct, snapshotForPlan, starterSnapshot, type EntitlementSnapshot } from "./entitlementPlans.js";

export interface EntitlementUpdate {
  source: string;
  status: SubscriptionStatus;
  productId: string;
  currentPeriodEndsAt: Date | null;
}

export interface EntitlementStore {
  current(auth: AuthContext): Promise<EntitlementSnapshot>;
  upsert(auth: AuthContext, update: EntitlementUpdate): Promise<EntitlementSnapshot>;
}

export function createEntitlementStore(): EntitlementStore {
  return shouldUsePrisma() ? new PrismaEntitlementStore(getPrismaClient()) : new InMemoryEntitlementStore();
}

export class InMemoryEntitlementStore implements EntitlementStore {
  private snapshots = new Map<string, EntitlementSnapshot>();

  async current(auth: AuthContext): Promise<EntitlementSnapshot> {
    return this.snapshots.get(auth.deviceId) ?? starterSnapshot("local-dev");
  }

  async upsert(auth: AuthContext, update: EntitlementUpdate): Promise<EntitlementSnapshot> {
    const snapshot = snapshotForPlan({
      plan: planForProduct(update.productId),
      status: update.status.toLowerCase() as EntitlementSnapshot["status"],
      source: update.source,
      productId: update.productId,
      currentPeriodEndsAt: update.currentPeriodEndsAt
    });

    this.snapshots.set(auth.deviceId, snapshot);
    return snapshot;
  }
}

class PrismaEntitlementStore implements EntitlementStore {
  constructor(private readonly prisma: PrismaClient) {}

  async current(auth: AuthContext): Promise<EntitlementSnapshot> {
    const userId = await resolveUserIdForDevice(this.prisma, auth);
    const subscription = await this.prisma.subscription.findUnique({
      where: { userId }
    });

    if (!subscription) {
      return starterSnapshot("local-dev");
    }

    return snapshotForPlan({
      plan: planForProduct(subscription.productId),
      status: subscription.status.toLowerCase() as EntitlementSnapshot["status"],
      source: subscription.source,
      productId: subscription.productId,
      currentPeriodEndsAt: subscription.currentPeriodEndsAt
    });
  }

  async upsert(auth: AuthContext, update: EntitlementUpdate): Promise<EntitlementSnapshot> {
    const userId = await resolveUserIdForDevice(this.prisma, auth);
    const subscription = await this.prisma.subscription.upsert({
      where: { userId },
      create: {
        userId,
        source: update.source,
        status: update.status,
        productId: update.productId,
        currentPeriodEndsAt: update.currentPeriodEndsAt
      },
      update: {
        source: update.source,
        status: update.status,
        productId: update.productId,
        currentPeriodEndsAt: update.currentPeriodEndsAt
      }
    });

    await this.replaceEntitlementRows(subscription.id, planForProduct(subscription.productId), subscription.currentPeriodEndsAt);

    return snapshotForPlan({
      plan: planForProduct(subscription.productId),
      status: subscription.status.toLowerCase() as EntitlementSnapshot["status"],
      source: subscription.source,
      productId: subscription.productId,
      currentPeriodEndsAt: subscription.currentPeriodEndsAt
    });
  }

  private async replaceEntitlementRows(subscriptionId: string, plan: "starter" | "pro" | "creator", expiresAt: Date | null): Promise<void> {
    const keys = plan === "creator"
      ? ["custom_bot_name", "cloud_backups", "emergency_mode", "advanced_filters", "preset_bundles", "discord_alerts", "obs_overlay", "longer_local_history", "ai_suggestions", "team_access"]
      : plan === "pro"
        ? ["custom_bot_name", "cloud_backups", "emergency_mode", "advanced_filters", "preset_bundles", "discord_alerts", "obs_overlay", "longer_local_history"]
        : ["custom_bot_name", "cloud_backups"];

    await this.prisma.entitlement.deleteMany({ where: { subscriptionId } });
    await this.prisma.entitlement.createMany({
      data: keys.map((key) => ({
        subscriptionId,
        key,
        enabled: true,
        expiresAt
      }))
    });
  }
}
