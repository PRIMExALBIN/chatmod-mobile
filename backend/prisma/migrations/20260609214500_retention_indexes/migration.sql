-- Retention pruning indexes for free-tier Postgres storage control.
CREATE INDEX "StreamSession_endedAt_idx" ON "StreamSession"("endedAt");
CREATE INDEX "ChatMessageLog_sessionId_createdAt_idx" ON "ChatMessageLog"("sessionId", "createdAt");
CREATE INDEX "ModerationActionLog_sessionId_createdAt_idx" ON "ModerationActionLog"("sessionId", "createdAt");
CREATE INDEX "BotRuntimeEvent_sessionId_createdAt_idx" ON "BotRuntimeEvent"("sessionId", "createdAt");
CREATE INDEX "ApiError_createdAt_idx" ON "ApiError"("createdAt");
CREATE INDEX "Backup_profileId_createdAt_idx" ON "Backup"("profileId", "createdAt");
CREATE INDEX "SupportEvent_createdAt_idx" ON "SupportEvent"("createdAt");
