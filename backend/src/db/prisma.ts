import { PrismaClient } from "@prisma/client";
import { env } from "../config/env.js";

const globalForPrisma = globalThis as unknown as {
  chatmodPrisma?: PrismaClient;
};

export function shouldUsePrisma(): boolean {
  return env.NODE_ENV !== "test" && Boolean(env.DATABASE_URL);
}

export function getPrismaClient(): PrismaClient {
  if (!globalForPrisma.chatmodPrisma) {
    globalForPrisma.chatmodPrisma = new PrismaClient();
  }

  return globalForPrisma.chatmodPrisma;
}
