import { z } from "zod";
import { chatMessageSchema, evaluateMessage, moderationProfileSchema, type ChatMessage, type ModerationAction } from "./ruleEngine.js";

const suggestionOptionsSchema = z.object({
  confidenceThreshold: z.number().min(0).max(1).default(0.65),
  manualApprovalRequired: z.literal(true).default(true)
}).default({});

const suggestionContextSchema = z.object({
  recentMessages: z.array(chatMessageSchema).max(50).default([])
}).default({});

export const moderationSuggestionRequestSchema = z.object({
  message: chatMessageSchema,
  profile: moderationProfileSchema.default({}),
  context: suggestionContextSchema,
  options: suggestionOptionsSchema
});

export type ModerationSuggestionRequest = z.infer<typeof moderationSuggestionRequestSchema>;

export interface ModerationSuggestionReason {
  code: string;
  label: string;
  detail: string;
  confidence: number;
}

export interface ModerationSuggestionResult {
  provider: "local-heuristic";
  manualApprovalRequired: true;
  suggestedAction: "allow" | "flagForReview" | "deleteMessage" | "timeoutUser" | "hideUser";
  classification: Array<"spam" | "toxicity" | "repeated_question" | "policy" | "safe">;
  confidence: number;
  confidenceThreshold: number;
  reasons: ModerationSuggestionReason[];
  explanation: string;
  usage?: ModerationSuggestionUsage;
}

export interface ModerationSuggestionUsage {
  used: number;
  limit: number;
  remaining: number;
  resetAt: string;
}

const actionPriority: Record<ModerationSuggestionResult["suggestedAction"], number> = {
  allow: 0,
  flagForReview: 1,
  deleteMessage: 2,
  timeoutUser: 3,
  hideUser: 4
};

const toxicPatterns = [
  /\bkill yourself\b/i,
  /\bkys\b/i,
  /\bgo die\b/i,
  /\bidiot\b/i,
  /\bmoron\b/i,
  /\bloser\b/i
];

export function evaluateModerationSuggestion(input: ModerationSuggestionRequest): ModerationSuggestionResult {
  const request = moderationSuggestionRequestSchema.parse(input);
  const ruleDecision = evaluateMessage(request.message, request.profile);
  const ruleReasons = ruleDecision.actions
    .filter((action) => action.type !== "sendAutoReply")
    .map(reasonFromAction);
  const repeatedQuestionReason = repeatedQuestionReasonFor(request.message, request.context.recentMessages);
  const toxicityReason = toxicityReasonFor(request.message);
  const reasons = [...ruleReasons, repeatedQuestionReason, toxicityReason]
    .filter((reason): reason is ModerationSuggestionReason => reason !== null)
    .sort((left, right) => right.confidence - left.confidence);

  const confidence = Math.max(0, ...reasons.map((reason) => reason.confidence));
  const suggestedAction = confidence >= request.options.confidenceThreshold
    ? strongestSuggestedAction(ruleDecision.actions, repeatedQuestionReason, toxicityReason)
    : "allow";
  const classification = classificationFor(reasons, suggestedAction);

  return {
    provider: "local-heuristic",
    manualApprovalRequired: true,
    suggestedAction,
    classification,
    confidence,
    confidenceThreshold: request.options.confidenceThreshold,
    reasons,
    explanation: explanationFor(suggestedAction, reasons, request.options.confidenceThreshold)
  };
}

function strongestSuggestedAction(
  actions: ModerationAction[],
  repeatedQuestionReason: ModerationSuggestionReason | null,
  toxicityReason: ModerationSuggestionReason | null
): ModerationSuggestionResult["suggestedAction"] {
  const candidates = actions
    .filter((action) => action.type !== "sendAutoReply")
    .map((action) => normalizeAction(action.type));

  if (repeatedQuestionReason) {
    candidates.push("flagForReview");
  }
  if (toxicityReason) {
    candidates.push("flagForReview");
  }

  return candidates.sort((left, right) => actionPriority[right] - actionPriority[left])[0] ?? "allow";
}

function normalizeAction(action: ModerationAction["type"]): ModerationSuggestionResult["suggestedAction"] {
  if (action === "sendAutoReply") {
    return "flagForReview";
  }
  return action;
}

function reasonFromAction(action: ModerationAction): ModerationSuggestionReason {
  return {
    code: action.reason,
    label: labelForReason(action.reason),
    detail: detailForReason(action.reason, action.type),
    confidence: roundConfidence(action.confidence)
  };
}

function repeatedQuestionReasonFor(
  message: ChatMessage,
  recentMessages: ChatMessage[]
): ModerationSuggestionReason | null {
  const normalized = normalizeQuestion(message.text);
  if (!normalized) {
    return null;
  }

  const repeats = recentMessages.filter((recent) => normalizeQuestion(recent.text) === normalized).length;
  if (repeats < 2) {
    return null;
  }

  return {
    code: "repeated_question",
    label: "Repeated question",
    detail: `This question appeared ${repeats + 1} times in the recent chat window.`,
    confidence: repeats >= 4 ? 0.82 : 0.72
  };
}

function toxicityReasonFor(message: ChatMessage): ModerationSuggestionReason | null {
  if (!toxicPatterns.some((pattern) => pattern.test(message.text))) {
    return null;
  }

  return {
    code: "toxicity_language",
    label: "Toxicity risk",
    detail: "The message includes harassment or abuse language that should be reviewed before action.",
    confidence: 0.78
  };
}

function classificationFor(
  reasons: ModerationSuggestionReason[],
  suggestedAction: ModerationSuggestionResult["suggestedAction"]
): ModerationSuggestionResult["classification"] {
  if (suggestedAction === "allow" || reasons.length === 0) {
    return ["safe"];
  }

  const classes = new Set<ModerationSuggestionResult["classification"][number]>();
  for (const reason of reasons) {
    if (reason.code === "repeated_question") {
      classes.add("repeated_question");
    } else if (reason.code.includes("toxicity")) {
      classes.add("toxicity");
    } else if (reason.code.includes("term") || reason.code.includes("domain") || reason.code.includes("policy")) {
      classes.add("policy");
    } else {
      classes.add("spam");
    }
  }

  return [...classes];
}

function explanationFor(
  suggestedAction: ModerationSuggestionResult["suggestedAction"],
  reasons: ModerationSuggestionReason[],
  threshold: number
): string {
  if (suggestedAction === "allow") {
    return reasons.length === 0
      ? "No strong moderation signal was found. Keep the message unless the creator sees extra context."
      : `Signals stayed below the ${Math.round(threshold * 100)} percent confidence threshold, so no action is suggested.`;
  }

  const leadReason = reasons[0];
  return `${suggestionVerb(suggestedAction)} is suggested for manual review because ${leadReason.label.toLowerCase()} was detected.`;
}

function suggestionVerb(action: ModerationSuggestionResult["suggestedAction"]): string {
  switch (action) {
    case "deleteMessage":
      return "Delete message";
    case "timeoutUser":
      return "Timeout";
    case "hideUser":
      return "Hide user";
    case "flagForReview":
      return "Review";
    default:
      return "No action";
  }
}

function labelForReason(reason: string): string {
  if (reason.startsWith("blocked_term:")) {
    return "Blocked phrase";
  }
  if (reason.startsWith("blocked_domain:")) {
    return "Blocked domain";
  }
  if (reason.startsWith("domain_not_allowed:")) {
    return "Unapproved domain";
  }
  const labels: Record<string, string> = {
    regex_pattern: "Pattern match",
    link_policy: "Link policy",
    excessive_caps: "Excessive caps",
    repeated_characters: "Repeated characters",
    emoji_spam: "Emoji spam",
    mention_spam: "Mention spam",
    symbol_spam: "Symbol spam",
    severe_match_hide_user: "Severe match"
  };
  const key = reason.split(":")[0] ?? reason;
  return labels[key] ?? reason.replace(/_/g, " ");
}

function detailForReason(reason: string, action: ModerationAction["type"]): string {
  if (reason.startsWith("blocked_term:")) {
    return `Matched blocked phrase "${reason.slice("blocked_term:".length)}".`;
  }
  if (reason.startsWith("blocked_domain:")) {
    return `Matched blocked domain ${reason.slice("blocked_domain:".length)}.`;
  }
  if (reason.startsWith("domain_not_allowed:")) {
    return `Linked to ${reason.slice("domain_not_allowed:".length)}, which is outside the allowed domain list.`;
  }
  return `${labelForReason(reason)} produced a ${action} recommendation.`;
}

function normalizeQuestion(value: string): string | null {
  const trimmed = value
    .trim()
    .toLowerCase()
    .replace(/[^\p{L}\p{N}\s?]/gu, "")
    .replace(/\s+/g, " ");
  if (!trimmed.endsWith("?") || trimmed.length < 12) {
    return null;
  }
  return trimmed;
}

function roundConfidence(value: number): number {
  return Math.round(value * 100) / 100;
}
