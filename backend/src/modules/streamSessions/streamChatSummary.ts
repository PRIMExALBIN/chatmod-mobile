import type { ChatMessageLogRecord, ModerationActionLogRecord, StreamSessionLogBundle } from "./streamSessionStore.js";

export interface StreamChatSummary {
  provider: "local-heuristic";
  sessionId: string;
  generatedAt: string;
  title: string | null;
  summary: string;
  highlights: string[];
  topQuestions: Array<{
    question: string;
    count: number;
  }>;
  topChatters: Array<{
    authorChannelId: string;
    authorName: string;
    messageCount: number;
  }>;
  moderationNotes: string[];
  suggestedFollowUps: string[];
  stats: {
    messageCount: number;
    uniqueChatters: number;
    moderationActionCount: number;
    destructiveActionCount: number;
  };
}

const StopWords = new Set([
  "about",
  "after",
  "again",
  "also",
  "because",
  "before",
  "chat",
  "could",
  "from",
  "have",
  "just",
  "like",
  "live",
  "more",
  "stream",
  "that",
  "their",
  "there",
  "this",
  "what",
  "when",
  "where",
  "with",
  "would",
  "your"
]);

export function summarizeStreamChat(bundle: StreamSessionLogBundle, now = new Date()): StreamChatSummary {
  const topChatters = topChattersFor(bundle.messages);
  const topQuestions = topQuestionsFor(bundle.messages);
  const keywords = topKeywordsFor(bundle.messages);
  const destructiveActionCount = bundle.actions.filter(isDestructiveAction).length;
  const moderationNotes = moderationNotesFor(bundle.actions);
  const highlights = highlightsFor({
    messages: bundle.messages,
    topChatters,
    topQuestions,
    keywords,
    destructiveActionCount
  });

  return {
    provider: "local-heuristic",
    sessionId: bundle.session.id,
    generatedAt: now.toISOString(),
    title: bundle.session.title,
    summary: summaryTextFor({
      title: bundle.session.title,
      messageCount: bundle.messages.length,
      uniqueChatters: topChatters.length,
      keywords,
      topQuestions,
      destructiveActionCount
    }),
    highlights,
    topQuestions,
    topChatters: topChatters.slice(0, 5),
    moderationNotes,
    suggestedFollowUps: followUpsFor({ topQuestions, moderationNotes, destructiveActionCount, messageCount: bundle.messages.length }),
    stats: {
      messageCount: bundle.messages.length,
      uniqueChatters: topChatters.length,
      moderationActionCount: bundle.actions.length,
      destructiveActionCount
    }
  };
}

function summaryTextFor(input: {
  title: string | null;
  messageCount: number;
  uniqueChatters: number;
  keywords: string[];
  topQuestions: Array<{ question: string; count: number }>;
  destructiveActionCount: number;
}): string {
  if (input.messageCount === 0) {
    return "No synced chat messages were available for this stream yet.";
  }

  const topicText = input.keywords.length > 0
    ? ` Main topics were ${input.keywords.slice(0, 3).join(", ")}.`
    : "";
  const questionText = input.topQuestions.length > 0
    ? ` The most repeated question was "${input.topQuestions[0].question}".`
    : "";
  const moderationText = input.destructiveActionCount > 0
    ? ` Moderation took ${input.destructiveActionCount} destructive action(s).`
    : " Moderation stayed light.";

  return `Local chat summary for ${input.title ?? "this stream"}: ${input.messageCount} messages from ${input.uniqueChatters} chatter(s).${topicText}${questionText}${moderationText}`;
}

function highlightsFor(input: {
  messages: ChatMessageLogRecord[];
  topChatters: Array<{ authorChannelId: string; authorName: string; messageCount: number }>;
  topQuestions: Array<{ question: string; count: number }>;
  keywords: string[];
  destructiveActionCount: number;
}): string[] {
  const highlights: string[] = [];
  if (input.keywords.length > 0) {
    highlights.push(`Most discussed terms: ${input.keywords.slice(0, 5).join(", ")}.`);
  }
  if (input.topChatters.length > 0) {
    highlights.push(`Most active chatter: ${input.topChatters[0].authorName} with ${input.topChatters[0].messageCount} message(s).`);
  }
  if (input.topQuestions.length > 0) {
    highlights.push(`Repeated question: "${input.topQuestions[0].question}" appeared ${input.topQuestions[0].count} time(s).`);
  }
  if (input.destructiveActionCount > 0) {
    highlights.push(`${input.destructiveActionCount} destructive moderation action(s) were logged.`);
  }
  if (highlights.length === 0 && input.messages.length > 0) {
    highlights.push("Chat activity was low and did not produce strong repeated themes.");
  }
  return highlights;
}

function topQuestionsFor(messages: ChatMessageLogRecord[]): Array<{ question: string; count: number }> {
  const questions = new Map<string, { question: string; count: number }>();
  for (const message of messages) {
    const normalized = normalizedQuestion(message.text);
    if (!normalized) {
      continue;
    }
    const entry = questions.get(normalized) ?? { question: displayQuestion(message.text), count: 0 };
    entry.count += 1;
    questions.set(normalized, entry);
  }
  return Array.from(questions.values())
    .filter((question) => question.count >= 2)
    .sort((left, right) => right.count - left.count || left.question.localeCompare(right.question))
    .slice(0, 5);
}

function topChattersFor(messages: ChatMessageLogRecord[]): Array<{ authorChannelId: string; authorName: string; messageCount: number }> {
  const chatters = new Map<string, { authorChannelId: string; authorName: string; messageCount: number }>();
  for (const message of messages) {
    const entry = chatters.get(message.authorChannelId) ?? {
      authorChannelId: message.authorChannelId,
      authorName: message.authorName,
      messageCount: 0
    };
    entry.authorName = message.authorName || entry.authorName;
    entry.messageCount += 1;
    chatters.set(message.authorChannelId, entry);
  }
  return Array.from(chatters.values())
    .sort((left, right) => right.messageCount - left.messageCount || left.authorName.localeCompare(right.authorName));
}

function topKeywordsFor(messages: ChatMessageLogRecord[]): string[] {
  const counts = new Map<string, number>();
  for (const message of messages) {
    const words = message.text
      .toLowerCase()
      .replace(/https?:\/\/\S+/g, " ")
      .match(/[\p{L}\p{N}]{4,}/gu) ?? [];
    for (const word of words) {
      if (StopWords.has(word)) {
        continue;
      }
      counts.set(word, (counts.get(word) ?? 0) + 1);
    }
  }
  return Array.from(counts.entries())
    .filter(([, count]) => count >= 2)
    .sort((left, right) => right[1] - left[1] || left[0].localeCompare(right[0]))
    .slice(0, 8)
    .map(([word]) => word);
}

function moderationNotesFor(actions: ModerationActionLogRecord[]): string[] {
  if (actions.length === 0) {
    return ["No moderation actions were synced for this stream."];
  }

  const notes: string[] = [];
  const destructiveActionCount = actions.filter(isDestructiveAction).length;
  const falsePositiveCount = actions.filter((action) => action.reviewStatus === "false_positive").length;
  const topReason = topActionReason(actions);

  notes.push(`${actions.length} moderation action(s) were logged; ${destructiveActionCount} were destructive.`);
  if (topReason) {
    notes.push(`Top moderation trigger: ${topReason.reason} (${topReason.count} match(es)).`);
  }
  if (falsePositiveCount > 0) {
    notes.push(`${falsePositiveCount} action(s) were marked false positive and should inform preset tuning.`);
  }
  return notes;
}

function followUpsFor(input: {
  topQuestions: Array<{ question: string; count: number }>;
  moderationNotes: string[];
  destructiveActionCount: number;
  messageCount: number;
}): string[] {
  const followUps: string[] = [];
  if (input.topQuestions.length > 0) {
    followUps.push(`Add or update an FAQ reply for: "${input.topQuestions[0].question}".`);
  }
  if (input.destructiveActionCount > 0) {
    followUps.push("Review destructive moderation actions before reusing this preset.");
  }
  if (input.moderationNotes.some((note) => note.includes("false positive"))) {
    followUps.push("Tune rules that produced false positives.");
  }
  if (input.messageCount === 0) {
    followUps.push("Sync live chat logs before relying on an after-stream summary.");
  }
  if (followUps.length === 0) {
    followUps.push("Keep the current preset; no urgent follow-up was detected.");
  }
  return followUps;
}

function topActionReason(actions: ModerationActionLogRecord[]): { reason: string; count: number } | null {
  const counts = new Map<string, number>();
  for (const action of actions) {
    const reason = action.reason.split(":")[0] ?? action.reason;
    counts.set(reason, (counts.get(reason) ?? 0) + 1);
  }
  return Array.from(counts.entries())
    .sort((left, right) => right[1] - left[1] || left[0].localeCompare(right[0]))
    .map(([reason, count]) => ({ reason, count }))[0] ?? null;
}

function isDestructiveAction(action: Pick<ModerationActionLogRecord, "actionType">): boolean {
  return ["deleteMessage", "hideUser", "timeoutUser"].includes(action.actionType);
}

function normalizedQuestion(text: string): string | null {
  const normalized = text
    .trim()
    .toLowerCase()
    .replace(/[^\p{L}\p{N}\s?]/gu, "")
    .replace(/\s+/g, " ");
  if (!normalized.endsWith("?") || normalized.length < 10) {
    return null;
  }
  return normalized;
}

function displayQuestion(text: string): string {
  return text.trim().replace(/\s+/g, " ").slice(0, 160);
}
