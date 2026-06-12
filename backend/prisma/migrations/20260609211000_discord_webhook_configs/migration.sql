CREATE TABLE "DiscordWebhookConfig" (
  "id" TEXT NOT NULL,
  "profileId" TEXT NOT NULL,
  "encryptedWebhookUrl" TEXT,
  "enabled" BOOLEAN NOT NULL DEFAULT false,
  "alertModerationActions" BOOLEAN NOT NULL DEFAULT true,
  "alertRuntimeStatus" BOOLEAN NOT NULL DEFAULT false,
  "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updatedAt" TIMESTAMP(3) NOT NULL,

  CONSTRAINT "DiscordWebhookConfig_pkey" PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX "DiscordWebhookConfig_profileId_key" ON "DiscordWebhookConfig"("profileId");

ALTER TABLE "DiscordWebhookConfig"
ADD CONSTRAINT "DiscordWebhookConfig_profileId_fkey"
FOREIGN KEY ("profileId") REFERENCES "ChannelProfile"("id")
ON DELETE CASCADE ON UPDATE CASCADE;
