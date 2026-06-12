import type { FastifyReply, FastifyRequest } from "fastify";
import type { AuthContext } from "../modules/auth/sessionToken.js";
import { verifySessionToken } from "../modules/auth/sessionToken.js";

declare module "fastify" {
  interface FastifyRequest {
    auth: AuthContext | null;
  }
}

export async function requireAuth(request: FastifyRequest, reply: FastifyReply): Promise<void> {
  const header = request.headers.authorization;
  const requestId = String(request.id);
  reply.header("x-request-id", requestId);

  if (!header?.startsWith("Bearer ")) {
    await reply.status(401).send({
      error: "UNAUTHORIZED",
      message: "Missing bearer token.",
      requestId
    });
    return;
  }

  try {
    request.auth = await verifySessionToken(header.slice("Bearer ".length));
  } catch {
    await reply.status(401).send({
      error: "UNAUTHORIZED",
      message: "Invalid or expired bearer token.",
      requestId
    });
  }
}
