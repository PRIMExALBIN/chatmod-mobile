import type { FastifyInstance } from "fastify";
import { ZodError } from "zod";
import type { ApiErrorStore } from "../modules/logs/apiErrorStore.js";

export interface ErrorHandlerOptions {
  apiErrorStore?: ApiErrorStore;
}

export function registerErrorHandler(app: FastifyInstance, options: ErrorHandlerOptions = {}): void {
  app.setErrorHandler(async (error, request, reply) => {
    const requestId = String(request.id);
    reply.header("x-request-id", requestId);

    if (error instanceof ZodError) {
      await recordApiError(options.apiErrorStore, {
        requestId,
        deviceId: request.auth?.deviceId,
        installId: request.auth?.installId,
        statusCode: 400,
        code: "VALIDATION_ERROR",
        message: "Request payload did not match the expected schema.",
        method: request.method,
        url: request.url,
        metadata: {
          issueCount: error.issues.length
        }
      });
      return reply.status(400).send({
        error: "VALIDATION_ERROR",
        message: "Request payload did not match the expected schema.",
        requestId,
        issues: error.issues
      });
    }

    const statusCode = getStatusCode(error);
    const message = error instanceof Error ? error.message : "Request failed.";
    if (statusCode >= 500) {
      request.log.error({ err: error, requestId }, "Unhandled request error");
    } else {
      request.log.warn({ err: error, requestId, statusCode }, "Request error");
    }
    const explicitCode = getPublicCode(error);
    const responseCode = explicitCode ?? (statusCode >= 500 ? "INTERNAL_SERVER_ERROR" : "REQUEST_ERROR");
    const responseMessage = explicitCode || statusCode < 500 ? message : "Something went wrong.";
    const retryAfterMillis = getRetryAfterMillis(error);
    if (retryAfterMillis !== null) {
      reply.header("retry-after", Math.ceil(retryAfterMillis / 1000).toString());
    }
    await recordApiError(options.apiErrorStore, {
      requestId,
      deviceId: request.auth?.deviceId,
      installId: request.auth?.installId,
      statusCode,
      provider: getProvider(error),
      code: responseCode,
      message: responseMessage,
      method: request.method,
      url: request.url
    });

    return reply.status(statusCode).send({
      error: responseCode,
      message: responseMessage,
      requestId
    });
  });
}

async function recordApiError(
  store: ApiErrorStore | undefined,
  input: {
    requestId: string;
    deviceId?: string;
    installId?: string;
    statusCode: number;
    provider?: string;
    code: string;
    message: string;
    method: string;
    url: string;
    metadata?: Record<string, unknown>;
  }
): Promise<void> {
  if (!store || !input.deviceId) {
    return;
  }

  await store.create({
    provider: input.provider ?? "backend",
    code: input.code,
    message: input.message,
    metadata: {
      requestId: input.requestId,
      deviceId: input.deviceId,
      installId: input.installId,
      statusCode: input.statusCode,
      method: input.method,
      url: input.url,
      ...input.metadata
    }
  }).catch(() => undefined);
}

function getStatusCode(error: unknown): number {
  if (typeof error !== "object" || error === null || !("statusCode" in error)) {
    return 500;
  }

  const statusCode = Number((error as { statusCode?: unknown }).statusCode);
  return Number.isInteger(statusCode) && statusCode >= 400 ? statusCode : 500;
}

function getPublicCode(error: unknown): string | null {
  return getStringProperty(error, "publicCode");
}

function getProvider(error: unknown): string {
  return getStringProperty(error, "provider") ?? "backend";
}

function getRetryAfterMillis(error: unknown): number | null {
  const retryAfterMillis = Number((error as { retryAfterMillis?: unknown })?.retryAfterMillis);
  return Number.isFinite(retryAfterMillis) && retryAfterMillis >= 0 ? retryAfterMillis : null;
}

function getStringProperty(error: unknown, key: string): string | null {
  if (typeof error !== "object" || error === null || !(key in error)) {
    return null;
  }

  const value = (error as Record<string, unknown>)[key];
  return typeof value === "string" && value.length > 0 ? value : null;
}
