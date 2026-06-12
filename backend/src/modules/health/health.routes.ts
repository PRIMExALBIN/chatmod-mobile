import type { FastifyInstance } from "fastify";
import { getPrismaClient, shouldUsePrisma } from "../../db/prisma.js";

export async function healthRoutes(app: FastifyInstance): Promise<void> {
  app.get("/", async () => ({
    status: "ok",
    service: "chatmod-mobile-api",
    version: "0.1.0",
    uptimeSeconds: Math.round(process.uptime())
  }));

  app.get("/ready", async (_request, reply) => {
    if (!shouldUsePrisma()) {
      return {
        status: "ok",
        service: "chatmod-mobile-api",
        dependencies: {
          database: "not_configured"
        }
      };
    }

    try {
      await getPrismaClient().$queryRaw`SELECT 1`;
      return {
        status: "ok",
        service: "chatmod-mobile-api",
        dependencies: {
          database: "ok"
        }
      };
    } catch {
      return reply.status(503).send({
        status: "unavailable",
        service: "chatmod-mobile-api",
        dependencies: {
          database: "unavailable"
        }
      });
    }
  });
}
