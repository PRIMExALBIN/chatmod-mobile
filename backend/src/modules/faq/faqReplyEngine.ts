import type { FaqEntryRecord } from "./faqStore.js";

export interface FaqReplySuggestion {
  provider: "local-heuristic";
  matched: boolean;
  manualApprovalRequired: true;
  entryId: string | null;
  question: string | null;
  replyText: string | null;
  confidence: number;
  matchedKeywords: string[];
  explanation: string;
}

export function suggestFaqReply(input: {
  entries: FaqEntryRecord[];
  messageText: string;
  minConfidence?: number;
}): FaqReplySuggestion {
  const enabledEntries = input.entries.filter((entry) => entry.enabled);
  const scored = enabledEntries
    .map((entry) => scoreEntry(entry, input.messageText))
    .sort((left, right) => right.confidence - left.confidence || left.entry.question.localeCompare(right.entry.question))[0];
  const minConfidence = input.minConfidence ?? 0.45;

  if (!scored || scored.confidence < minConfidence) {
    return {
      provider: "local-heuristic",
      matched: false,
      manualApprovalRequired: true,
      entryId: null,
      question: null,
      replyText: null,
      confidence: scored?.confidence ?? 0,
      matchedKeywords: scored?.matchedKeywords ?? [],
      explanation: "No saved FAQ answer matched the viewer message strongly enough."
    };
  }

  return {
    provider: "local-heuristic",
    matched: true,
    manualApprovalRequired: true,
    entryId: scored.entry.id,
    question: scored.entry.question,
    replyText: scored.entry.answer,
    confidence: roundConfidence(scored.confidence),
    matchedKeywords: scored.matchedKeywords,
    explanation: `Suggested from creator FAQ: "${scored.entry.question}".`
  };
}

function scoreEntry(entry: FaqEntryRecord, messageText: string): {
  entry: FaqEntryRecord;
  confidence: number;
  matchedKeywords: string[];
} {
  const messageTokens = tokenize(messageText);
  const questionTokens = tokenize(entry.question);
  const keywordTokens = entry.keywords.flatMap((keyword) => tokenize(keyword));
  const allEntryTokens = new Set([...questionTokens, ...keywordTokens]);
  const matchedKeywords = entry.keywords.filter((keyword) => phraseMatches(messageText, keyword));
  const tokenOverlap = messageTokens.filter((token) => allEntryTokens.has(token)).length;
  const overlapRatio = allEntryTokens.size === 0 ? 0 : tokenOverlap / allEntryTokens.size;
  const questionSimilarity = normalizedText(messageText).includes(normalizedText(entry.question).replace(/\?$/, ""))
    ? 0.9
    : 0;
  const keywordBoost = Math.min(0.35, matchedKeywords.length * 0.12);
  const questionMarkBoost = messageText.includes("?") ? 0.08 : 0;
  const confidence = Math.max(questionSimilarity, Math.min(0.92, overlapRatio + keywordBoost + questionMarkBoost));

  return {
    entry,
    confidence: roundConfidence(confidence),
    matchedKeywords
  };
}

function tokenize(value: string): string[] {
  return normalizedText(value)
    .split(" ")
    .filter((token) => token.length >= 3);
}

function phraseMatches(messageText: string, keyword: string): boolean {
  return normalizedText(messageText).includes(normalizedText(keyword));
}

function normalizedText(value: string): string {
  return value
    .trim()
    .toLowerCase()
    .replace(/[^\p{L}\p{N}\s?]/gu, " ")
    .replace(/\s+/g, " ");
}

function roundConfidence(value: number): number {
  return Math.round(value * 100) / 100;
}
