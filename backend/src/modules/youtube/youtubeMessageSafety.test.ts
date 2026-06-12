import { describe, expect, it } from "vitest";
import { liveChatTextSchema } from "./youtubeMessageSafety.js";

describe("liveChatTextSchema", () => {
  it("trims valid message text", () => {
    expect(liveChatTextSchema.parse("  Hello chat  ")).toBe("Hello chat");
  });

  it("rejects blank message text", () => {
    expect(() => liveChatTextSchema.parse("   ")).toThrow();
  });

  it("rejects control characters", () => {
    expect(() => liveChatTextSchema.parse("hello\nchat")).toThrow();
    expect(() => liveChatTextSchema.parse("hello\u0000chat")).toThrow();
  });
});
