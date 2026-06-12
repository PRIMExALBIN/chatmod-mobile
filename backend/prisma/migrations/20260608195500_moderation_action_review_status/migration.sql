ALTER TABLE "ModerationActionLog"
  ADD COLUMN "reviewStatus" TEXT,
  ADD COLUMN "reviewedAt" TIMESTAMP(3),
  ADD COLUMN "reviewNote" TEXT;

CREATE INDEX "ModerationActionLog_reviewStatus_idx" ON "ModerationActionLog"("reviewStatus");
