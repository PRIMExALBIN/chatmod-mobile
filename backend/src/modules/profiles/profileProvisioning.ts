import type { AuthContext } from "../auth/sessionToken.js";
import type { EntitlementStore } from "../entitlements/entitlementStore.js";
import { assertWithinEntitlementLimit } from "../entitlements/entitlementQuota.js";
import type { ProfileStore } from "./profileStore.js";

export async function ensureProfileForSyncedResource(input: {
  auth: AuthContext;
  profileStore: ProfileStore;
  entitlementStore: EntitlementStore;
  profileId: string;
}): Promise<void> {
  const profiles = await input.profileStore.list(input.auth);
  if (profiles.some((profile) => profile.id === input.profileId)) {
    return;
  }

  await assertWithinEntitlementLimit({
    auth: input.auth,
    entitlementStore: input.entitlementStore,
    feature: "channelProfiles",
    currentCount: profiles.length,
    resourceLabel: "Channel profile"
  });
  await input.profileStore.ensureDefault(input.auth, input.profileId);
}
