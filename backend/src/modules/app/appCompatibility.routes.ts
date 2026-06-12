import type { FastifyInstance } from "fastify";
import { z } from "zod";
import { env } from "../../config/env.js";

const compatibilityQuerySchema = z.object({
  platform: z.enum(["android"]),
  versionName: z.string().min(1).default("unknown"),
  versionCode: z.coerce.number().int().min(0)
});

export async function appCompatibilityRoutes(app: FastifyInstance): Promise<void> {
  app.get("/compatibility", async (request) => {
    const query = compatibilityQuerySchema.parse(request.query);
    const minimumSupportedVersionCode = env.ANDROID_MIN_SUPPORTED_VERSION_CODE;
    const latestVersionCode = Math.max(env.ANDROID_LATEST_VERSION_CODE, minimumSupportedVersionCode);
    const updateRequired = query.versionCode < minimumSupportedVersionCode;
    const updateRecommended = !updateRequired && query.versionCode < latestVersionCode;
    const status = updateRequired ? "update_required" : updateRecommended ? "update_recommended" : "compatible";

    return {
      platform: query.platform,
      currentVersionName: query.versionName,
      currentVersionCode: query.versionCode,
      minimumSupportedVersionName: env.ANDROID_MIN_SUPPORTED_VERSION_NAME,
      minimumSupportedVersionCode,
      latestVersionName: env.ANDROID_LATEST_VERSION_NAME,
      latestVersionCode,
      status,
      updateRequired,
      updateRecommended,
      message: compatibilityMessage(status),
      downloadUrl: env.ANDROID_UPDATE_URL ?? null
    };
  });
}

function compatibilityMessage(status: "compatible" | "update_recommended" | "update_required"): string {
  if (status === "update_required") {
    return "This ChatMod Mobile build is no longer supported. Update before starting the bot.";
  }

  if (status === "update_recommended") {
    return "A newer ChatMod Mobile build is available.";
  }

  return "ChatMod Mobile is compatible.";
}
