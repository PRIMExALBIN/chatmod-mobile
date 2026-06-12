import { google } from "googleapis";
import type { SubscriptionStatus } from "@prisma/client";
import { env } from "../../config/env.js";

export interface GooglePlayValidationRequest {
  packageName?: string;
  productId: string;
  purchaseToken: string;
}

export interface GooglePlayValidationResult {
  productId: string;
  purchaseToken: string;
  status: SubscriptionStatus;
  source: "google-play";
  currentPeriodEndsAt: Date | null;
  orderId: string | null;
  autoRenewing: boolean | null;
}

export function isGooglePlayBillingConfigured(): boolean {
  return Boolean(env.GOOGLE_PLAY_PACKAGE_NAME && env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON_BASE64);
}

export function missingGooglePlayEnv(): string[] {
  const missing: string[] = [];

  if (!env.GOOGLE_PLAY_PACKAGE_NAME) {
    missing.push("GOOGLE_PLAY_PACKAGE_NAME");
  }
  if (!env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON_BASE64) {
    missing.push("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON_BASE64");
  }

  return missing;
}

export class GooglePlayBillingValidator {
  async validateSubscription(request: GooglePlayValidationRequest): Promise<GooglePlayValidationResult> {
    if (!isGooglePlayBillingConfigured()) {
      throw new Error(`Google Play billing is not configured: ${missingGooglePlayEnv().join(", ")}`);
    }

    const auth = new google.auth.GoogleAuth({
      credentials: serviceAccountCredentials(),
      scopes: ["https://www.googleapis.com/auth/androidpublisher"]
    });
    const androidpublisher = google.androidpublisher({
      version: "v3",
      auth
    });
    const packageName = request.packageName ?? env.GOOGLE_PLAY_PACKAGE_NAME!;
    const response = await androidpublisher.purchases.subscriptions.get({
      packageName,
      subscriptionId: request.productId,
      token: request.purchaseToken
    });
    const expiryTimeMillis = response.data.expiryTimeMillis ? Number(response.data.expiryTimeMillis) : null;
    const currentPeriodEndsAt = expiryTimeMillis ? new Date(expiryTimeMillis) : null;

    return {
      productId: request.productId,
      purchaseToken: request.purchaseToken,
      status: statusFromPurchase({
        currentPeriodEndsAt,
        cancelReason: response.data.cancelReason,
        paymentState: response.data.paymentState
      }),
      source: "google-play",
      currentPeriodEndsAt,
      orderId: response.data.orderId ?? null,
      autoRenewing: response.data.autoRenewing ?? null
    };
  }
}

export function statusFromPurchase(input: {
  currentPeriodEndsAt: Date | null;
  cancelReason?: number | null;
  paymentState?: number | null;
  now?: Date;
}): SubscriptionStatus {
  const now = input.now?.getTime() ?? Date.now();

  if (!input.currentPeriodEndsAt || input.currentPeriodEndsAt.getTime() <= now) {
    return "EXPIRED";
  }

  if (input.cancelReason === 1) {
    return "PAST_DUE";
  }

  if (input.cancelReason !== null && input.cancelReason !== undefined) {
    return "CANCELED";
  }

  if (input.paymentState === 2) {
    return "TRIALING";
  }

  if (input.paymentState === 0) {
    return "PAST_DUE";
  }

  return "ACTIVE";
}

function serviceAccountCredentials(): Record<string, unknown> {
  const raw = Buffer.from(env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON_BASE64!, "base64").toString("utf8");
  return JSON.parse(raw) as Record<string, unknown>;
}
