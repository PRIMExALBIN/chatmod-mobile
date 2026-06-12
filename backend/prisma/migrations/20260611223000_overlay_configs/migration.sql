-- CreateTable
CREATE TABLE "OverlayConfig" (
    "id" TEXT NOT NULL,
    "profileId" TEXT NOT NULL,
    "ownerDeviceId" TEXT NOT NULL,
    "ownerInstallId" TEXT NOT NULL,
    "publicTokenHash" TEXT NOT NULL,
    "tokenPreview" TEXT NOT NULL,
    "enabled" BOOLEAN NOT NULL DEFAULT false,
    "theme" TEXT NOT NULL DEFAULT 'control_room',
    "activeSessionId" TEXT,
    "showModerationActions" BOOLEAN NOT NULL DEFAULT true,
    "showRuntimeStatus" BOOLEAN NOT NULL DEFAULT true,
    "showViewerStats" BOOLEAN NOT NULL DEFAULT true,
    "showRecentChat" BOOLEAN NOT NULL DEFAULT false,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "OverlayConfig_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX "OverlayConfig_profileId_key" ON "OverlayConfig"("profileId");

-- CreateIndex
CREATE UNIQUE INDEX "OverlayConfig_publicTokenHash_key" ON "OverlayConfig"("publicTokenHash");

-- CreateIndex
CREATE INDEX "OverlayConfig_ownerDeviceId_idx" ON "OverlayConfig"("ownerDeviceId");

-- CreateIndex
CREATE INDEX "OverlayConfig_updatedAt_idx" ON "OverlayConfig"("updatedAt");

-- AddForeignKey
ALTER TABLE "OverlayConfig" ADD CONSTRAINT "OverlayConfig_profileId_fkey" FOREIGN KEY ("profileId") REFERENCES "ChannelProfile"("id") ON DELETE CASCADE ON UPDATE CASCADE;
