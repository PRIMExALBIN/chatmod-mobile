import { afterEach, describe, expect, it, vi } from "vitest";

const originalEnv = { ...process.env };
const primaryKey = "primary:AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE";
const rotatedKey = "rotated:AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI";

describe("secretCipher", () => {
  afterEach(() => {
    restoreEnv();
    vi.resetModules();
  });

  it("encrypts and decrypts local secrets without retaining plaintext", async () => {
    const { decryptSecret, encryptSecret } = await loadCipher();
    const plaintext = "ya29.demo-token";
    const encrypted = encryptSecret(plaintext);

    expect(encrypted).toBeTruthy();
    expect(encrypted).toMatch(/^v1\./);
    expect(encrypted).not.toContain(plaintext);
    expect(decryptSecret(encrypted)).toBe(plaintext);
  });

  it("uses the primary configured key for production-style encryption", async () => {
    const { decryptSecret, encryptSecret } = await loadCipher({
      SECRET_ENCRYPTION_KEYS: primaryKey
    });
    const encrypted = encryptSecret("refresh-token");

    expect(encrypted).toMatch(/^v2\.primary\./);
    expect(decryptSecret(encrypted)).toBe("refresh-token");
  });

  it("decrypts old key ids when the rotated key ring keeps them configured", async () => {
    const firstCipher = await loadCipher({
      SECRET_ENCRYPTION_KEYS: primaryKey
    });
    const encrypted = firstCipher.encryptSecret("refresh-token");

    const rotatedCipher = await loadCipher({
      SECRET_ENCRYPTION_KEYS: `${rotatedKey},${primaryKey}`
    });

    expect(rotatedCipher.decryptSecret(encrypted)).toBe("refresh-token");
    expect(rotatedCipher.encryptSecret("new-token")).toMatch(/^v2\.rotated\./);
  });

  it("keeps null values as null", async () => {
    const { decryptSecret, encryptSecret } = await loadCipher();

    expect(encryptSecret(null)).toBeNull();
    expect(decryptSecret(null)).toBeNull();
  });
});

async function loadCipher(env: Record<string, string> = {}) {
  restoreEnv();
  Object.assign(process.env, env);
  vi.resetModules();
  return import("./secretCipher.js");
}

function restoreEnv(): void {
  for (const key of Object.keys(process.env)) {
    delete process.env[key];
  }
  Object.assign(process.env, originalEnv);
  delete process.env.SECRET_ENCRYPTION_KEYS;
}
