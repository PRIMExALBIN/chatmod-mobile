CREATE TABLE "UserModerationAction" (
  "id" TEXT NOT NULL,
  "userProfileId" TEXT NOT NULL,
  "actionType" TEXT NOT NULL,
  "liveChatId" TEXT,
  "reason" TEXT NOT NULL,
  "durationSeconds" INTEGER,
  "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "expiresAt" TIMESTAMP(3),

  CONSTRAINT "UserModerationAction_pkey" PRIMARY KEY ("id")
);

CREATE INDEX "UserModerationAction_userProfileId_createdAt_idx" ON "UserModerationAction"("userProfileId", "createdAt");

ALTER TABLE "UserModerationAction"
  ADD CONSTRAINT "UserModerationAction_userProfileId_fkey"
  FOREIGN KEY ("userProfileId") REFERENCES "UserProfile"("id") ON DELETE CASCADE ON UPDATE CASCADE;
