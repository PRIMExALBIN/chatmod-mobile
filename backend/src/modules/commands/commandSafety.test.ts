import { describe, expect, it } from "vitest";
import { validateCommandResponseSafety } from "./commandSafety.js";

describe("validateCommandResponseSafety", () => {
  it("allows normal text and public https links", () => {
    expect(validateCommandResponseSafety("Join us at https://example.com/community").safe).toBe(true);
    expect(validateCommandResponseSafety("No links here.").safe).toBe(true);
  });

  it("allows www links by normalizing to https", () => {
    expect(validateCommandResponseSafety("Visit www.example.com.").safe).toBe(true);
  });

  it("rejects dangerous URL protocols", () => {
    expect(validateCommandResponseSafety("Click javascript:alert(1)")).toMatchObject({
      safe: false,
      reason: "Command responses can only use http or https links."
    });
    expect(validateCommandResponseSafety("Download file:///etc/passwd").safe).toBe(false);
  });

  it("rejects private and local network URLs", () => {
    expect(validateCommandResponseSafety("Debug http://localhost:4100").safe).toBe(false);
    expect(validateCommandResponseSafety("Router http://192.168.1.1").safe).toBe(false);
    expect(validateCommandResponseSafety("Internal http://10.0.0.5").safe).toBe(false);
  });

  it("rejects embedded URL credentials", () => {
    expect(validateCommandResponseSafety("Bad https://user:pass@example.com")).toMatchObject({
      safe: false,
      reason: "Command response links cannot include embedded credentials."
    });
  });
});
