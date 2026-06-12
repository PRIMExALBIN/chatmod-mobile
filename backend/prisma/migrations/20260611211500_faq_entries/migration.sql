CREATE TABLE "FaqEntry" (
    "id" TEXT NOT NULL,
    "profileId" TEXT NOT NULL,
    "question" TEXT NOT NULL,
    "answer" TEXT NOT NULL,
    "keywordsJson" JSONB,
    "enabled" BOOLEAN NOT NULL DEFAULT true,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "FaqEntry_pkey" PRIMARY KEY ("id")
);

CREATE INDEX "FaqEntry_profileId_enabled_idx" ON "FaqEntry"("profileId", "enabled");

ALTER TABLE "FaqEntry"
ADD CONSTRAINT "FaqEntry_profileId_fkey"
FOREIGN KEY ("profileId") REFERENCES "ChannelProfile"("id") ON DELETE CASCADE ON UPDATE CASCADE;
