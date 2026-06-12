# AI-Assisted Moderation

ChatMod Mobile now has a source-complete moderation suggestion layer for Creator-plan review workflows. The beta implementation uses a free local heuristic provider, `local-heuristic`, so it does not require OpenAI, Gemini, hosted inference, vector storage, or another paid model service.

The same local provider also supports after-stream chat summaries from synced stream-session logs. These summaries help creators review repeated questions, moderation pressure, and follow-up tasks after a live stream ends.

Creator FAQ replies are now backed by a creator-provided knowledge base. The app stores the creator's saved question, answer, and keyword rows, then suggests manual replies when a queued viewer message matches one of those entries.

## Product Rules

- Suggestions support moderation; they do not replace the creator.
- Every suggestion requires manual approval.
- Destructive suggestions never execute automatically from the suggestion endpoint.
- The UI must show confidence and an explanation before the creator acts.
- The default provider must remain free/local until usage proves a paid model is worth adding.
- Creator entitlement gates the backend route through `aiSuggestions`.
- Creator plans include a daily UTC usage cap through `aiSuggestionDailyLimit`.
- After-stream summaries are generated from synced messages and audit logs; they do not invent unseen chat context.
- FAQ replies must come from creator-provided entries; ChatMod does not invent answers outside the saved knowledge base.

## Backend Contract

Endpoint:

```http
POST /moderation/suggestions/evaluate
```

The request accepts:

- A `ChatMessage`.
- A `ModerationProfile`.
- Optional recent chat context for repeated-question detection.
- A confidence threshold, defaulting to `0.65`.

The response includes:

- `provider`: currently `local-heuristic`.
- `manualApprovalRequired`: always `true`.
- `suggestedAction`: `allow`, `flagForReview`, `deleteMessage`, `timeoutUser`, or `hideUser`.
- `classification`: `spam`, `toxicity`, `repeated_question`, `policy`, or `safe`.
- `confidence` and `confidenceThreshold`.
- Ranked reasons with labels, details, and confidence values.
- A plain-language explanation.
- `usage`: `used`, `limit`, `remaining`, and `resetAt` for the current UTC day.

Starter and Pro plans receive `AI_SUGGESTIONS_REQUIRED`. Creator plans can use the endpoint up to the daily plan limit. When the limit is reached, the backend returns `AI_SUGGESTION_LIMIT_REACHED` with status `429`, a `Retry-After` header, and the same usage shape.

Creator FAQ knowledge base endpoints:

```http
GET /faq-entries?profileId={profileId}
PUT /faq-entries/{id}
DELETE /faq-entries/{id}
POST /faq-entries/suggest-reply
```

FAQ entries include:

- `question`: creator-authored viewer question.
- `answer`: creator-authored reply text, validated as safe live-chat text.
- `keywords`: creator-provided matching hints.
- `enabled`: whether the entry participates in suggestions.

Reply suggestions return `matched`, `replyText`, `confidence`, `matchedKeywords`, and `manualApprovalRequired: true`. Starter and Pro plans receive `AI_FAQ_REQUIRED`.

After-stream summary endpoint:

```http
GET /stream-sessions/{id}/ai-summary
```

Creator plans receive a deterministic summary with:

- `summary`: short local chat recap.
- `highlights`: repeated themes, active chatters, or moderation pressure.
- `topQuestions`: repeated questions worth turning into FAQ replies.
- `moderationNotes`: moderation action and false-positive notes.
- `suggestedFollowUps`: creator tasks for the next stream.

Starter and Pro plans receive `AI_CHAT_SUMMARY_REQUIRED`.

## Android UX

The Queue row exposes a review-assistant action. When available, it calls the backend suggestion endpoint and renders:

- Suggested action.
- Suggested FAQ reply when the creator knowledge base matches.
- Top reason.
- Confidence percent.
- Classification chips.
- A manual-approval chip.
- Explanation text.

When the feature is unavailable, the dashboard records that Creator is required instead of pretending the suggestion ran.

The Logs tab loads the newest synced stream summary alongside Pro trends. It shows the summary, message/chatter/action counts, repeated questions, and follow-up suggestions without adding paid analytics infrastructure.

The Commands tab includes a FAQ replies panel where Creator users can add, edit, disable, refresh, and delete knowledge-base entries.

## Free-Tier Posture

This implementation is intentionally deterministic and local by default. It keeps beta costs predictable and avoids silently adding paid AI infrastructure. A future model-backed provider can be added only after:

- Manual approval remains the default.
- Per-plan usage limits are enforced.
- Logs explain which provider produced the suggestion.
- The privacy policy and Play Data Safety answers are updated.
