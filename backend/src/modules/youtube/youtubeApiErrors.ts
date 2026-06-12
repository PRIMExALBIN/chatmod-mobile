export type YouTubeApiErrorCode =
  | "YOUTUBE_RATE_LIMITED"
  | "YOUTUBE_QUOTA_EXCEEDED"
  | "YOUTUBE_LIVE_CHAT_ENDED"
  | "YOUTUBE_LIVE_CHAT_UNAVAILABLE"
  | "YOUTUBE_PERMISSION_DENIED"
  | "YOUTUBE_API_UNAVAILABLE"
  | "YOUTUBE_API_ERROR";

export class YouTubeApiError extends Error {
  public readonly provider = "youtube";

  constructor(
    public readonly publicCode: YouTubeApiErrorCode,
    public readonly statusCode: number,
    message: string,
    public readonly reason: string | null = null,
    public readonly retryAfterMillis: number | null = null
  ) {
    super(message);
    this.name = "YouTubeApiError";
  }
}

export function normalizeYouTubeApiError(error: unknown): YouTubeApiError {
  if (error instanceof YouTubeApiError) {
    return error;
  }

  const statusCode = getStatusCode(error);
  const reason = getReason(error);
  const retryAfterMillis = getRetryAfterMillis(error);
  const normalizedReason = reason?.toLowerCase() ?? "";

  if (statusCode === 429 || normalizedReason.includes("ratelimit")) {
    return new YouTubeApiError(
      "YOUTUBE_RATE_LIMITED",
      429,
      "YouTube is rate limiting live chat requests. Wait before trying again.",
      reason,
      retryAfterMillis
    );
  }

  if (normalizedReason.includes("quota") || normalizedReason.includes("dailylimit")) {
    return new YouTubeApiError(
      "YOUTUBE_QUOTA_EXCEEDED",
      429,
      "YouTube API quota is exhausted. Try again after the quota resets.",
      reason,
      retryAfterMillis
    );
  }

  if (normalizedReason.includes("livechatended")) {
    return new YouTubeApiError(
      "YOUTUBE_LIVE_CHAT_ENDED",
      409,
      "This YouTube live chat has ended.",
      reason,
      retryAfterMillis
    );
  }

  if (
    statusCode === 404 ||
    normalizedReason.includes("livechatnotfound") ||
    normalizedReason.includes("livechatdisabled")
  ) {
    return new YouTubeApiError(
      "YOUTUBE_LIVE_CHAT_UNAVAILABLE",
      statusCode === 404 ? 404 : 409,
      "YouTube live chat is not available for this stream.",
      reason,
      retryAfterMillis
    );
  }

  if (statusCode === 401 || statusCode === 403) {
    return new YouTubeApiError(
      "YOUTUBE_PERMISSION_DENIED",
      statusCode,
      "YouTube did not allow this bot action. Reconnect YouTube or check moderator permissions.",
      reason,
      retryAfterMillis
    );
  }

  if (statusCode >= 500) {
    return new YouTubeApiError(
      "YOUTUBE_API_UNAVAILABLE",
      503,
      "YouTube API is temporarily unavailable.",
      reason,
      retryAfterMillis
    );
  }

  return new YouTubeApiError(
    "YOUTUBE_API_ERROR",
    statusCode,
    "YouTube API request failed.",
    reason,
    retryAfterMillis
  );
}

export async function withYouTubeApiErrors<T>(call: () => Promise<T>): Promise<T> {
  try {
    return await call();
  } catch (error) {
    throw normalizeYouTubeApiError(error);
  }
}

function getStatusCode(error: unknown): number {
  const candidate = getNumberProperty(error, "statusCode")
    ?? getNumberProperty(error, "code")
    ?? getNumberProperty(getObjectProperty(error, "response"), "status")
    ?? getNumberProperty(getObjectProperty(error, "response"), "statusCode")
    ?? getNumberProperty(getNestedError(error), "code");

  return candidate && candidate >= 400 ? candidate : 502;
}

function getReason(error: unknown): string | null {
  const directReason = firstReason(getObjectArrayProperty(error, "errors"));
  if (directReason) {
    return directReason;
  }

  const nested = getNestedError(error);
  return firstReason(getObjectArrayProperty(nested, "errors"))
    ?? getStringProperty(nested, "status")
    ?? null;
}

function getRetryAfterMillis(error: unknown): number | null {
  const headers = getObjectProperty(getObjectProperty(error, "response"), "headers");
  const retryAfter = getStringProperty(headers, "retry-after") ?? getStringProperty(headers, "Retry-After");
  if (!retryAfter) {
    return null;
  }

  const seconds = Number(retryAfter);
  if (Number.isFinite(seconds) && seconds >= 0) {
    return seconds * 1000;
  }

  const retryAt = Date.parse(retryAfter);
  if (!Number.isNaN(retryAt)) {
    return Math.max(0, retryAt - Date.now());
  }

  return null;
}

function firstReason(errors: Array<Record<string, unknown>> | null): string | null {
  return errors
    ?.map((entry) => getStringProperty(entry, "reason"))
    .find((reason): reason is string => Boolean(reason)) ?? null;
}

function getNestedError(error: unknown): Record<string, unknown> | null {
  return getObjectProperty(getObjectProperty(error, "response"), "data")
    ? getObjectProperty(getObjectProperty(getObjectProperty(error, "response"), "data"), "error")
    : null;
}

function getObjectProperty(value: unknown, key: string): Record<string, unknown> | null {
  if (typeof value !== "object" || value === null || !(key in value)) {
    return null;
  }

  const property = (value as Record<string, unknown>)[key];
  return typeof property === "object" && property !== null ? property as Record<string, unknown> : null;
}

function getObjectArrayProperty(value: unknown, key: string): Array<Record<string, unknown>> | null {
  if (typeof value !== "object" || value === null || !(key in value)) {
    return null;
  }

  const property = (value as Record<string, unknown>)[key];
  if (!Array.isArray(property)) {
    return null;
  }

  return property.filter((entry): entry is Record<string, unknown> => typeof entry === "object" && entry !== null);
}

function getStringProperty(value: unknown, key: string): string | null {
  if (typeof value !== "object" || value === null || !(key in value)) {
    return null;
  }

  const property = (value as Record<string, unknown>)[key];
  return typeof property === "string" && property.length > 0 ? property : null;
}

function getNumberProperty(value: unknown, key: string): number | null {
  if (typeof value !== "object" || value === null || !(key in value)) {
    return null;
  }

  const property = (value as Record<string, unknown>)[key];
  const numberValue = typeof property === "string" ? Number(property) : property;
  return typeof numberValue === "number" && Number.isFinite(numberValue) ? numberValue : null;
}
