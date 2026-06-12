ALTER TABLE "UserModerationAction"
  ADD COLUMN "liveChatBanId" TEXT;

CREATE INDEX "UserModerationAction_liveChatBanId_idx" ON "UserModerationAction"("liveChatBanId");
