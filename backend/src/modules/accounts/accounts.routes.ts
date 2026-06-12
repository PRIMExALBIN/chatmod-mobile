import type { FastifyInstance } from "fastify";
import { nanoid } from "nanoid";
import { z } from "zod";
import { requireAuth } from "../../plugins/auth.js";
import { createAccountPrivacyStore } from "./accountPrivacyStore.js";
import { issueSessionToken } from "../auth/sessionToken.js";

const deviceSessionSchema = z.object({
  deviceId: z.string().min(8),
  installId: z.string().min(8),
  appVersion: z.string().optional()
});

export async function accountRoutes(app: FastifyInstance): Promise<void> {
  const privacyStore = createAccountPrivacyStore();

  app.post("/device-session", async (request, reply) => {
    const body = deviceSessionSchema.parse(request.body);
    const issuedAt = new Date();
    const accessToken = await issueSessionToken({
      deviceId: body.deviceId,
      installId: body.installId
    });

    return reply.send({
      accessToken,
      tokenType: "Bearer",
      expiresInSeconds: 60 * 60,
      sessionId: nanoid(),
      device: {
        deviceId: body.deviceId,
        installId: body.installId,
        appVersion: body.appVersion ?? null,
        lastSeenAt: issuedAt.toISOString()
      }
    });
  });

  app.get("/export", { preHandler: requireAuth }, async (request) => privacyStore.exportCurrent(request.auth!));

  app.delete("/current", { preHandler: requireAuth }, async (request) => privacyStore.deleteCurrent(request.auth!));

  app.post("/youtube/disconnect", { preHandler: requireAuth }, async (request) =>
    privacyStore.disconnectYouTube(request.auth!)
  );
}
