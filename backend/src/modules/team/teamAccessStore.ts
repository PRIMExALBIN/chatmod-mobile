import { createHash, randomBytes } from "node:crypto";
import type { Prisma, PrismaClient } from "@prisma/client";
import { z } from "zod";
import { getPrismaClient, shouldUsePrisma } from "../../db/prisma.js";
import { HttpError, notFound } from "../../lib/httpErrors.js";
import { findUserIdForDevice } from "../accounts/deviceUser.js";
import type { AuthContext } from "../auth/sessionToken.js";

export const teamMemberPermissionsSchema = z.object({
  viewQueue: z.boolean().default(true),
  moderate: z.boolean().default(true),
  manageWarnings: z.boolean().default(true),
  viewAnalytics: z.boolean().default(false)
});

export const teamInviteCreateSchema = z.object({
  displayName: z.string().trim().min(1).max(80),
  role: z.enum(["moderator", "producer"]).default("moderator"),
  permissions: teamMemberPermissionsSchema.default({})
});

export const teamInviteRedeemSchema = z.object({
  inviteCode: z.string().trim().min(12).max(120),
  displayName: z.string().trim().min(1).max(80).optional()
});

export type TeamMemberPermissions = z.infer<typeof teamMemberPermissionsSchema>;
export type TeamInviteCreateInput = z.infer<typeof teamInviteCreateSchema>;
export type TeamInviteRedeemInput = z.infer<typeof teamInviteRedeemSchema>;
export type TeamMemberStatus = "invited" | "active" | "revoked";

export interface TeamMemberRecord {
  id: string;
  profileId: string;
  displayName: string;
  role: "moderator" | "producer";
  status: TeamMemberStatus;
  inviteCodePreview: string;
  memberDeviceId: string | null;
  permissions: TeamMemberPermissions;
  acceptedAt: string | null;
  revokedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface TeamInviteResult {
  member: TeamMemberRecord;
  inviteCode: string;
}

export interface TeamMembershipRecord extends TeamMemberRecord {
  profileName: string;
  channelId: string;
  ownerDeviceId: string;
}

export interface TeamAccessStore {
  listForProfile(auth: AuthContext, profileId: string): Promise<TeamMemberRecord[]>;
  countOpenSeats(auth: AuthContext, profileId: string): Promise<number>;
  createInvite(auth: AuthContext, profileId: string, input: TeamInviteCreateInput): Promise<TeamInviteResult>;
  revoke(auth: AuthContext, profileId: string, memberId: string): Promise<TeamMemberRecord>;
  redeem(auth: AuthContext, input: TeamInviteRedeemInput): Promise<TeamMembershipRecord>;
  memberships(auth: AuthContext): Promise<TeamMembershipRecord[]>;
}

export function createTeamAccessStore(): TeamAccessStore {
  return shouldUsePrisma() ? new PrismaTeamAccessStore(getPrismaClient()) : new InMemoryTeamAccessStore();
}

class InMemoryTeamAccessStore implements TeamAccessStore {
  private members = new Map<string, StoredTeamMember>();
  private inviteIndex = new Map<string, string>();

  async listForProfile(auth: AuthContext, profileId: string): Promise<TeamMemberRecord[]> {
    return Array.from(this.members.values())
      .filter((member) => member.profileId === profileId && member.ownerDeviceId === auth.deviceId)
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
      .map(publicMember);
  }

  async countOpenSeats(auth: AuthContext, profileId: string): Promise<number> {
    return Array.from(this.members.values())
      .filter((member) => member.profileId === profileId && member.ownerDeviceId === auth.deviceId)
      .filter((member) => member.status !== "revoked")
      .length;
  }

  async createInvite(auth: AuthContext, profileId: string, input: TeamInviteCreateInput): Promise<TeamInviteResult> {
    const inviteCode = generateInviteCode();
    const now = new Date().toISOString();
    const member: StoredTeamMember = {
      id: `team-${randomBytes(8).toString("hex")}`,
      profileId,
      profileName: "Team profile",
      channelId: profileId,
      ownerDeviceId: auth.deviceId,
      ownerInstallId: auth.installId,
      displayName: input.displayName,
      role: input.role,
      status: "invited",
      inviteCodeHash: hashInviteCode(inviteCode),
      inviteCodePreview: invitePreview(inviteCode),
      memberDeviceId: null,
      memberInstallId: null,
      permissions: normalizePermissions(input.permissions),
      acceptedAt: null,
      revokedAt: null,
      createdAt: now,
      updatedAt: now
    };
    this.members.set(member.id, member);
    this.inviteIndex.set(member.inviteCodeHash, member.id);
    return { member: publicMember(member), inviteCode };
  }

  async revoke(auth: AuthContext, profileId: string, memberId: string): Promise<TeamMemberRecord> {
    const existing = this.members.get(memberId);
    if (!existing || existing.profileId !== profileId || existing.ownerDeviceId !== auth.deviceId) {
      throw notFound("Team member not found.");
    }

    const now = new Date().toISOString();
    const updated: StoredTeamMember = {
      ...existing,
      status: "revoked",
      revokedAt: now,
      updatedAt: now
    };
    this.members.set(updated.id, updated);
    return publicMember(updated);
  }

  async redeem(auth: AuthContext, input: TeamInviteRedeemInput): Promise<TeamMembershipRecord> {
    const id = this.inviteIndex.get(hashInviteCode(input.inviteCode));
    const existing = id ? this.members.get(id) : null;
    if (!existing || existing.status === "revoked") {
      throw notFound("Team invite not found.");
    }
    if (existing.status === "active" && existing.memberDeviceId !== auth.deviceId) {
      throw new HttpError(409, "Team invite has already been redeemed.");
    }

    const now = new Date().toISOString();
    const updated: StoredTeamMember = {
      ...existing,
      status: "active",
      displayName: input.displayName ?? existing.displayName,
      memberDeviceId: auth.deviceId,
      memberInstallId: auth.installId,
      acceptedAt: existing.acceptedAt ?? now,
      updatedAt: now
    };
    this.members.set(updated.id, updated);
    return membershipRecord(updated);
  }

  async memberships(auth: AuthContext): Promise<TeamMembershipRecord[]> {
    return Array.from(this.members.values())
      .filter((member) => member.memberDeviceId === auth.deviceId && member.status === "active")
      .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
      .map(membershipRecord);
  }
}

class PrismaTeamAccessStore implements TeamAccessStore {
  constructor(private readonly prisma: PrismaClient) {}

  async listForProfile(auth: AuthContext, profileId: string): Promise<TeamMemberRecord[]> {
    await this.assertProfileOwner(auth, profileId);
    const rows = await this.prisma.teamMember.findMany({
      where: { profileId },
      orderBy: { createdAt: "desc" }
    });
    return rows.map(mapPrismaMember);
  }

  async countOpenSeats(auth: AuthContext, profileId: string): Promise<number> {
    await this.assertProfileOwner(auth, profileId);
    return this.prisma.teamMember.count({
      where: {
        profileId,
        status: {
          in: ["invited", "active"]
        }
      }
    });
  }

  async createInvite(auth: AuthContext, profileId: string, input: TeamInviteCreateInput): Promise<TeamInviteResult> {
    await this.assertProfileOwner(auth, profileId);
    const inviteCode = generateInviteCode();
    const row = await this.prisma.teamMember.create({
      data: {
        profileId,
        ownerDeviceId: auth.deviceId,
        ownerInstallId: auth.installId,
        displayName: input.displayName,
        role: input.role,
        inviteCodeHash: hashInviteCode(inviteCode),
        inviteCodePreview: invitePreview(inviteCode),
        permissionsJson: normalizePermissions(input.permissions) as Prisma.InputJsonValue
      }
    });

    return {
      member: mapPrismaMember(row),
      inviteCode
    };
  }

  async revoke(auth: AuthContext, profileId: string, memberId: string): Promise<TeamMemberRecord> {
    await this.assertProfileOwner(auth, profileId);
    const existing = await this.prisma.teamMember.findFirst({
      where: { id: memberId, profileId },
      select: { id: true }
    });
    if (!existing) {
      throw notFound("Team member not found.");
    }

    const row = await this.prisma.teamMember.update({
      where: { id: memberId },
      data: {
        status: "revoked",
        revokedAt: new Date()
      }
    });
    return mapPrismaMember(row);
  }

  async redeem(auth: AuthContext, input: TeamInviteRedeemInput): Promise<TeamMembershipRecord> {
    const existing = await this.prisma.teamMember.findUnique({
      where: { inviteCodeHash: hashInviteCode(input.inviteCode) },
      include: { profile: true }
    });
    if (!existing || existing.status === "revoked") {
      throw notFound("Team invite not found.");
    }
    if (existing.status === "active" && existing.memberDeviceId !== auth.deviceId) {
      throw new HttpError(409, "Team invite has already been redeemed.");
    }

    const row = await this.prisma.teamMember.update({
      where: { id: existing.id },
      data: {
        status: "active",
        displayName: input.displayName ?? existing.displayName,
        memberDeviceId: auth.deviceId,
        memberInstallId: auth.installId,
        acceptedAt: existing.acceptedAt ?? new Date()
      },
      include: { profile: true }
    });

    return mapPrismaMembership(row);
  }

  async memberships(auth: AuthContext): Promise<TeamMembershipRecord[]> {
    const rows = await this.prisma.teamMember.findMany({
      where: {
        memberDeviceId: auth.deviceId,
        status: "active"
      },
      include: { profile: true },
      orderBy: { updatedAt: "desc" }
    });
    return rows.map(mapPrismaMembership);
  }

  private async assertProfileOwner(auth: AuthContext, profileId: string): Promise<void> {
    const userId = await findUserIdForDevice(this.prisma, auth);
    if (!userId) {
      throw notFound("Channel profile not found.");
    }

    const profile = await this.prisma.channelProfile.findFirst({
      where: { id: profileId, userId },
      select: { id: true }
    });
    if (!profile) {
      throw notFound("Channel profile not found.");
    }
  }
}

interface StoredTeamMember {
  id: string;
  profileId: string;
  profileName: string;
  channelId: string;
  ownerDeviceId: string;
  ownerInstallId: string;
  memberDeviceId: string | null;
  memberInstallId: string | null;
  displayName: string;
  role: "moderator" | "producer";
  status: TeamMemberStatus;
  inviteCodeHash: string;
  inviteCodePreview: string;
  permissions: TeamMemberPermissions;
  acceptedAt: string | null;
  revokedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

function publicMember(member: StoredTeamMember): TeamMemberRecord {
  return {
    id: member.id,
    profileId: member.profileId,
    displayName: member.displayName,
    role: member.role,
    status: member.status,
    inviteCodePreview: member.inviteCodePreview,
    memberDeviceId: member.memberDeviceId,
    permissions: member.permissions,
    acceptedAt: member.acceptedAt,
    revokedAt: member.revokedAt,
    createdAt: member.createdAt,
    updatedAt: member.updatedAt
  };
}

function membershipRecord(member: StoredTeamMember): TeamMembershipRecord {
  return {
    ...publicMember(member),
    profileName: member.profileName,
    channelId: member.channelId,
    ownerDeviceId: member.ownerDeviceId
  };
}

function mapPrismaMember(row: {
  id: string;
  profileId: string;
  memberDeviceId: string | null;
  displayName: string;
  role: string;
  status: string;
  inviteCodePreview: string;
  permissionsJson: unknown;
  acceptedAt: Date | null;
  revokedAt: Date | null;
  createdAt: Date;
  updatedAt: Date;
}): TeamMemberRecord {
  return {
    id: row.id,
    profileId: row.profileId,
    displayName: row.displayName,
    role: row.role === "producer" ? "producer" : "moderator",
    status: teamMemberStatus(row.status),
    inviteCodePreview: row.inviteCodePreview,
    memberDeviceId: row.memberDeviceId,
    permissions: parsePermissions(row.permissionsJson),
    acceptedAt: row.acceptedAt?.toISOString() ?? null,
    revokedAt: row.revokedAt?.toISOString() ?? null,
    createdAt: row.createdAt.toISOString(),
    updatedAt: row.updatedAt.toISOString()
  };
}

function mapPrismaMembership(row: {
  id: string;
  profileId: string;
  ownerDeviceId: string;
  memberDeviceId: string | null;
  displayName: string;
  role: string;
  status: string;
  inviteCodePreview: string;
  permissionsJson: unknown;
  acceptedAt: Date | null;
  revokedAt: Date | null;
  createdAt: Date;
  updatedAt: Date;
  profile: {
    channelId: string;
    name: string;
  };
}): TeamMembershipRecord {
  return {
    ...mapPrismaMember(row),
    profileName: row.profile.name,
    channelId: row.profile.channelId,
    ownerDeviceId: row.ownerDeviceId
  };
}

function parsePermissions(value: unknown): TeamMemberPermissions {
  const parsed = teamMemberPermissionsSchema.safeParse(value);
  return parsed.success ? parsed.data : normalizePermissions({});
}

function normalizePermissions(value: Partial<TeamMemberPermissions>): TeamMemberPermissions {
  return teamMemberPermissionsSchema.parse(value);
}

function teamMemberStatus(value: string): TeamMemberStatus {
  if (value === "active" || value === "revoked") {
    return value;
  }
  return "invited";
}

function generateInviteCode(): string {
  return `cmt_${randomBytes(24).toString("base64url")}`;
}

function hashInviteCode(inviteCode: string): string {
  return createHash("sha256").update(inviteCode.trim(), "utf8").digest("hex");
}

function invitePreview(inviteCode: string): string {
  return `${inviteCode.slice(0, 8)}...${inviteCode.slice(-4)}`;
}
