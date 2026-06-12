import { describe, expect, it } from "vitest";
import { loadEnv, localDevelopmentJwtSecret } from "./env.js";

const encryptionKeys = "primary:AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE";

describe("environment configuration", () => {
  it("allows local development defaults", () => {
    const config = loadEnv({});

    expect(config.NODE_ENV).toBe("development");
    expect(config.JWT_SECRET).toBe(localDevelopmentJwtSecret);
  });

  it("rejects malformed encryption keys in any environment", () => {
    expect(() =>
      loadEnv({
        SECRET_ENCRYPTION_KEYS: "primary:not-a-32-byte-key"
      })
    ).toThrow();
  });

  it("rejects production without required secrets and origins", () => {
    expect(() =>
      loadEnv({
        NODE_ENV: "production",
        JWT_SECRET: localDevelopmentJwtSecret
      })
    ).toThrow();
  });

  it("accepts production with explicit database, cors origin, and strong JWT secret", () => {
    const config = loadEnv({
      NODE_ENV: "production",
      DATABASE_URL: "postgresql://user:password@example.com:5432/chatmod",
      CORS_ORIGIN: "https://chatmod.example.com",
      JWT_SECRET: "production-secret-with-at-least-32-characters",
      SECRET_ENCRYPTION_KEYS: encryptionKeys
    });

    expect(config.NODE_ENV).toBe("production");
    expect(config.CORS_ORIGIN).toBe("https://chatmod.example.com");
    expect(config.SECRET_ENCRYPTION_KEYS).toBe(encryptionKeys);
  });

  it("rejects production encryption keys that are not 32-byte base64url values", () => {
    expect(() =>
      loadEnv({
        NODE_ENV: "production",
        DATABASE_URL: "postgresql://user:password@example.com:5432/chatmod",
        CORS_ORIGIN: "https://chatmod.example.com",
        JWT_SECRET: "production-secret-with-at-least-32-characters",
        SECRET_ENCRYPTION_KEYS: "primary:not-a-32-byte-key"
      })
    ).toThrow();
  });

  it("rejects short production admin API keys when configured", () => {
    expect(() =>
      loadEnv({
        NODE_ENV: "production",
        DATABASE_URL: "postgresql://user:password@example.com:5432/chatmod",
        CORS_ORIGIN: "https://chatmod.example.com",
        JWT_SECRET: "production-secret-with-at-least-32-characters",
        SECRET_ENCRYPTION_KEYS: encryptionKeys,
        ADMIN_API_KEY: "short-key"
      })
    ).toThrow();
  });
});
