import type { PrismaClient } from "@prisma/client";
import type { AuthContext } from "../auth/sessionToken.js";

export async function findUserIdForDevice(prisma: PrismaClient, auth: AuthContext): Promise<string | null> {
  const existingDevice = await prisma.device.findUnique({
    where: { deviceId: auth.deviceId },
    select: { userId: true }
  });

  return existingDevice?.userId ?? null;
}

export async function resolveUserIdForDevice(prisma: PrismaClient, auth: AuthContext): Promise<string> {
  const existingDevice = await prisma.device.findUnique({
    where: { deviceId: auth.deviceId },
    select: { userId: true }
  });

  if (existingDevice) {
    await prisma.device.update({
      where: { deviceId: auth.deviceId },
      data: {
        installId: auth.installId,
        lastSeenAt: new Date()
      }
    });
    return existingDevice.userId;
  }

  const user = await prisma.user.create({
    data: {
      displayName: "Local creator",
      devices: {
        create: {
          deviceId: auth.deviceId,
          installId: auth.installId
        }
      }
    },
    select: { id: true }
  });

  return user.id;
}
