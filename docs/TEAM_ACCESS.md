# Team Moderator Access

ChatMod Mobile now has first-party team moderator access for Pro and Creator plans. It uses the existing backend, Postgres, Android Settings panel, and device-session auth. No paid workspace, helpdesk, Firebase, realtime database, or external team-management platform is required for beta.

## What Is Implemented

Creators can manage profile-scoped team access from the Android Settings tab:

- View the plan seat count and extra moderator seats.
- Create a team invite for the selected channel profile.
- See the one-time invite code immediately after creation.
- List invited, active, and revoked team members.
- Revoke a team member or unused invite.

Invited moderators can use the same Settings tab to redeem an invite code on their phone. The backend stores the redeemed device ID and returns the active memberships for that device.

## Entitlement

Team access uses the existing `teamSeats` entitlement:

- Starter: `1` seat, meaning creator only and no extra team moderators.
- Pro: `2` seats, meaning creator plus one extra moderator.
- Creator: `5` seats, meaning creator plus four extra moderators.

The backend enforces the extra-seat limit before creating invites. Invited and active team members count against the limit. Revoked members do not.

## Backend Contract

Creator routes:

- `GET /team/profiles/{profileId}/members`
- `POST /team/profiles/{profileId}/invites`
- `DELETE /team/profiles/{profileId}/members/{memberId}`

Moderator routes:

- `POST /team/invites/redeem`
- `GET /team/memberships`

Invite codes are generated server-side, stored only as SHA-256 hashes, and returned in full only once when the invite is created. Authenticated list responses show only a short preview.

## Current Scope

This is the source-complete team access foundation:

- Profile-scoped invite and membership persistence.
- Seat-limit enforcement.
- Android management and redeem controls.
- Revocation flow.
- OpenAPI and backend tests.

Still external/manual:

- Device QA with two physical or emulator devices.
- Real stream co-moderation rehearsal after Google OAuth and YouTube Live test streams are available.
- Optional later expansion: dedicated moderator runtime shortcuts beyond the current invite, redeem, list, and revoke controls.
