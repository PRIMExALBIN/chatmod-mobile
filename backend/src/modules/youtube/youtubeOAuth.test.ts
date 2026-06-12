import { describe, expect, it } from "vitest";
import { issueYouTubeOAuthState, verifyYouTubeOAuthState } from "./youtubeOAuth.js";

describe("YouTube OAuth state", () => {
  it("round trips authenticated device context", async () => {
    const state = await issueYouTubeOAuthState({
      subject: "device-123",
      deviceId: "device-123",
      installId: "install-123"
    });

    const verified = await verifyYouTubeOAuthState(state);

    expect(verified).toEqual({
      subject: "device-123",
      deviceId: "device-123",
      installId: "install-123"
    });
  });

  it("rejects malformed state", async () => {
    await expect(verifyYouTubeOAuthState("not-a-jwt")).rejects.toThrow();
  });
});
