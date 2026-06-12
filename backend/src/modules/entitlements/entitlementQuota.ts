import { HttpError } from "../../lib/httpErrors.js";
import type { AuthContext } from "../auth/sessionToken.js";
import type { EntitlementSnapshot } from "./entitlementPlans.js";
import type { EntitlementStore } from "./entitlementStore.js";

type NumericEntitlementFeature = {
  [Key in keyof EntitlementSnapshot["features"]]: Extract<EntitlementSnapshot["features"][Key], number | null> extends never
    ? never
    : Key
}[keyof EntitlementSnapshot["features"]];

export async function assertWithinEntitlementLimit(input: {
  auth: AuthContext;
  entitlementStore: EntitlementStore;
  feature: NumericEntitlementFeature;
  currentCount: number;
  additionalCount?: number;
  resourceLabel: string;
}): Promise<void> {
  const entitlement = await input.entitlementStore.current(input.auth);
  const limit = entitlement.features[input.feature];
  const additionalCount = input.additionalCount ?? 1;

  if (limit === null) {
    return;
  }

  if (input.currentCount + additionalCount <= limit) {
    return;
  }

  throw new HttpError(
    403,
    `${input.resourceLabel} limit reached for ${entitlement.plan} plan (${limit}).`
  );
}
