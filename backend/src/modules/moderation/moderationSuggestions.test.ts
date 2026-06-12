import { describe, expect, it } from "vitest";
import { moderationSuggestionRequestSchema, evaluateModerationSuggestion } from "./moderationSuggestions.js";

const baseMessage = {
  id: "message-1",
  authorChannelId: "viewer-1",
  authorName: "Viewer",
  text: "BUY CHEAP VIEWS NOW www.spam.example",
  timestamp: "2026-06-11T18:00:00.000Z"
};

describe("moderation suggestions", () => {
  it("suggests manual review actions with explanations and confidence", () => {
    const suggestion = evaluateModerationSuggestion(moderationSuggestionRequestSchema.parse({
      message: baseMessage,
      profile: {
        blockedTerms: ["cheap views"],
        linkPolicy: "delete"
      }
    }));

    expect(suggestion).toMatchObject({
      provider: "local-heuristic",
      manualApprovalRequired: true,
      suggestedAction: "deleteMessage",
      classification: expect.arrayContaining(["policy"]),
      confidence: 0.96
    });
    expect(suggestion.explanation).toContain("manual review");
    expect(suggestion.reasons[0]).toMatchObject({
      code: "blocked_term:cheap views",
      label: "Blocked phrase"
    });
  });

  it("detects repeated questions from recent chat context", () => {
    const message = {
      ...baseMessage,
      text: "When does the giveaway start?"
    };
    const suggestion = evaluateModerationSuggestion(moderationSuggestionRequestSchema.parse({
      message,
      profile: {},
      context: {
        recentMessages: [
          { ...message, id: "recent-1", authorChannelId: "viewer-2" },
          { ...message, id: "recent-2", authorChannelId: "viewer-3" }
        ]
      }
    }));

    expect(suggestion).toMatchObject({
      manualApprovalRequired: true,
      suggestedAction: "flagForReview",
      classification: ["repeated_question"],
      confidence: 0.72
    });
  });

  it("keeps low-confidence suggestions below threshold as allow", () => {
    const suggestion = evaluateModerationSuggestion(moderationSuggestionRequestSchema.parse({
      message: {
        ...baseMessage,
        text: "HELLO CHAT"
      },
      profile: {
        capsThreshold: 0.5
      },
      options: {
        confidenceThreshold: 0.9,
        manualApprovalRequired: true
      }
    }));

    expect(suggestion.suggestedAction).toBe("allow");
    expect(suggestion.explanation).toContain("below the 90 percent confidence threshold");
  });
});
