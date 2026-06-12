# Rule Presets Guide

Rule presets are saved moderation profiles for a channel profile. They let the creator keep a known default rule set and tune it during a stream without rebuilding the whole configuration.

## Current App Behavior

- The backend stores rule presets per authenticated account/profile.
- Only one preset should be default for a profile.
- The backend exposes curated rule-preset templates for Family friendly, Gaming default, Education/Q&A, Music/live performance, and High-security raid mode.
- Family friendly, Gaming default, Education/Q&A, and Music/live performance use Starter-safe fields: blocked terms, link policy, caps threshold, and repeated-character threshold.
- High-security raid mode uses advanced filters and requires a Pro or Creator entitlement before the backend will save it.
- The Android Rules tab can load templates, apply one as the active backend default preset, save a custom preset copy, and switch the backend default preset.
- The Queue tab can quick-block a phrase from a flagged message into the selected/default preset's blocked terms.
- The Android dashboard caches the active/default preset in DataStore so the foreground bot can apply it locally during polling.
- Temporary trusted-channel entries are stored inside the preset as expiring rules and ignored by the rule engines after expiry.
- Raid-mode and first-time chatter burst settings are stored in the preset and round-trip through the backend API for Pro/Creator entitlements.
- The Android runtime uses those settings to flag sudden bursts of new chatters and to tighten repeated-message/flood thresholds when raid mode is active.
- Rule-engine matches are surfaced as first-class `Rule match` entries in the Android live feed and Logs tab.
- The Logs tab ranks top triggered rules from those local rule-match entries so creators can see which rule categories fired most during moderation.
- Non-destructive `flagForReview` rule matches appear in the Logs tab false-positive review list.
- Creators can mark a review candidate as a false positive from the Logs tab. The local Room log keeps the reviewed row for audit, removes it from the unresolved review queue, and excludes it from active top-rule counts.
- When the action log has synced with the backend, the same false-positive review status is patched into the stream-session audit API for account export and support review.
- Review candidates also offer **Tune preset** when ChatMod can infer a safe adjustment: adding an allowed domain, relaxing link policy, raising caps/repeated-character/emoji/mention/symbol thresholds, or raising the new-chatter burst threshold. Starter-safe tuning can save immediately; advanced tuning requires the backend advanced-filter entitlement.
- Pro/Creator presets can be exported as a versioned ChatMod JSON bundle from Android and imported back into the active profile by pasting that bundle. Import creates fresh preset IDs, preserves the imported default flag when one exists, and still runs backend advanced-filter entitlement checks.

## Curated Templates

- Family friendly: balanced protection for younger audiences, schools, and brand-safe streams.
- Gaming default: fast-chat defaults with room for hype and emotes.
- Education/Q&A: tighter signal-to-noise for lessons, workshops, and question-heavy streams.
- Music/live performance: more permissive repeat thresholds for applause and live reactions.
- High-security raid mode: stricter link, spam, symbol, and new-chatter burst thresholds for emergency response; requires Pro/Creator advanced filters.

Applying a template saves it through the normal rule-preset API with a stable client ID, marks it as the profile default, caches it in Android DataStore, and makes it available to the foreground runtime without a paid third-party service.

## Quick Block

Quick block is meant for live moderation pressure. From a queue row, the creator can add one extracted phrase from the selected message to blocked terms. The app:

- Removes links and punctuation before extracting the phrase.
- Avoids common filler words.
- Preserves existing preset settings when the backend returns an active/default preset.
- Avoids duplicate blocked terms by case-insensitive matching.
- Saves the updated preset through the backend rule-preset API.
- Removes the handled message from the local queue after the preset save succeeds.
- Later matches from the saved rule appear in the local rule-match log with the matched reason.
- Those matches also roll up into the local top-triggered-rules summary and the completed runtime session metadata.
- If a rule only flags a message for review, the entry remains available in the false-positive review list until the creator marks it reviewed.

## Production Notes

Quick block should stay conservative. It should add a short phrase, not an entire chat message, because broad rules create false positives. More detailed editing belongs in the Rules tab.

Preset import/export uses the `presetBundles` entitlement, authenticated backend routes (`GET /rule-presets/export` and `POST /rule-presets/import`), and Android native sharing/paste controls. It does not require paid object storage, external file permissions, or a third-party sync service.

## Still Planned

- Full preset editor for blocked terms, regex, domains, thresholds, and trusted-user behavior.
- Full manual preset editor for every rule field.
- Emergency-only preset variants.
