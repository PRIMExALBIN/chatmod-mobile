# Beta Feedback Loop

ChatMod Mobile uses a first-party beta feedback channel instead of a paid product-feedback SDK during early testing.

## Android Flow

The Support tab includes a beta feedback form with:

- Category chips for bug, idea, confusing, pricing, and other.
- A 1000-character creator-written note.
- Small app context such as selected tab, bot running state, sync status, command count, timer count, queue count, and billing plan.
- A recent feedback list loaded from the backend for the current device session.

The app does not automatically attach YouTube chat message bodies, OAuth tokens, emails, URLs, or private account secrets to feedback context.

## Backend Flow

- `POST /feedback/beta` records authenticated beta feedback.
- `GET /feedback/beta` lists beta feedback for the current device.
- `POST /feedback/beta-interest` records public launch-site beta interest without requiring an app session.
- Feedback is stored through the existing support-event persistence path with `details.eventType = "beta_feedback"`.
- Launch-site beta interest is stored through the same support-event persistence path with `details.eventType = "beta_interest"` and the synthetic device id `launch-site-beta-interest`.
- `/logs/support-events` filters beta feedback out of diagnostics so support snapshots remain separate from product notes.
- Account export and deletion cover authenticated in-app feedback because it lives in the same backend support-event data path.
- Public beta-interest leads are limited to submitted email, optional creator name, optional YouTube channel URL, optional note, source, timestamp, and truncated user agent.

## Beta Triage

Use this loop during closed beta:

1. Ask the creator to submit feedback from the Support tab.
2. Ask them to send a diagnostic too if the note describes a bug or broken YouTube action.
3. Review feedback by account/device in the backend store.
4. Link bug reports to support diagnostics or API request IDs when available.
5. Promote repeated feedback themes into `BUILD_CHECKLIST.md` before implementation.
6. Review launch-site beta interest through the admin support snapshot for `launch-site-beta-interest`.

This gives the beta a real support channel without adding another hosted database, analytics SDK, or paid feedback service.
