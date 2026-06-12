-- CreateTable
CREATE TABLE "TeamMember" (
    "id" TEXT NOT NULL,
    "profileId" TEXT NOT NULL,
    "ownerDeviceId" TEXT NOT NULL,
    "ownerInstallId" TEXT NOT NULL,
    "memberDeviceId" TEXT,
    "memberInstallId" TEXT,
    "displayName" TEXT NOT NULL,
    "role" TEXT NOT NULL DEFAULT 'moderator',
    "status" TEXT NOT NULL DEFAULT 'invited',
    "inviteCodeHash" TEXT NOT NULL,
    "inviteCodePreview" TEXT NOT NULL,
    "permissionsJson" JSONB NOT NULL,
    "acceptedAt" TIMESTAMP(3),
    "revokedAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "TeamMember_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX "TeamMember_inviteCodeHash_key" ON "TeamMember"("inviteCodeHash");

-- CreateIndex
CREATE INDEX "TeamMember_profileId_status_idx" ON "TeamMember"("profileId", "status");

-- CreateIndex
CREATE INDEX "TeamMember_memberDeviceId_status_idx" ON "TeamMember"("memberDeviceId", "status");

-- CreateIndex
CREATE INDEX "TeamMember_ownerDeviceId_idx" ON "TeamMember"("ownerDeviceId");

-- AddForeignKey
ALTER TABLE "TeamMember" ADD CONSTRAINT "TeamMember_profileId_fkey" FOREIGN KEY ("profileId") REFERENCES "ChannelProfile"("id") ON DELETE CASCADE ON UPDATE CASCADE;
