import { createCipheriv, createDecipheriv, createHash, randomBytes } from "node:crypto";
import { env } from "../config/env.js";

const algorithm = "aes-256-gcm";
const legacyVersion = "v1";
const currentVersion = "v2";

export function encryptSecret(value: string | null): string | null {
  if (!value) {
    return null;
  }

  const iv = randomBytes(12);
  const key = currentEncryptionKey();
  const cipher = createCipheriv(algorithm, key.material, iv);
  const encrypted = Buffer.concat([cipher.update(value, "utf8"), cipher.final()]);
  const tag = cipher.getAuthTag();

  if (key.version === legacyVersion) {
    return [
      legacyVersion,
      iv.toString("base64url"),
      tag.toString("base64url"),
      encrypted.toString("base64url")
    ].join(".");
  }

  return [
    currentVersion,
    key.id,
    iv.toString("base64url"),
    tag.toString("base64url"),
    encrypted.toString("base64url")
  ].join(".");
}

export function decryptSecret(value: string | null): string | null {
  if (!value) {
    return null;
  }

  const parts = value.split(".");
  const [version] = parts;
  if (version === legacyVersion) {
    const [, iv, tag, encrypted] = parts;
    if (!iv || !tag || !encrypted || parts.length !== 4) {
      throw new Error("Unsupported encrypted secret format.");
    }
    return decryptWithKey(legacyEncryptionKey(), iv, tag, encrypted);
  }

  if (version === currentVersion) {
    const [, keyId, iv, tag, encrypted] = parts;
    if (!keyId || !iv || !tag || !encrypted || parts.length !== 5) {
      throw new Error("Unsupported encrypted secret format.");
    }
    const key = configuredEncryptionKeys().find((candidate) => candidate.id === keyId);
    if (!key) {
      throw new Error("Encrypted secret key id is not configured.");
    }
    return decryptWithKey(key.material, iv, tag, encrypted);
  }

  throw new Error("Unsupported encrypted secret format.");
}

function decryptWithKey(key: Buffer, iv: string, tag: string, encrypted: string): string {
  const decipher = createDecipheriv(
    algorithm,
    key,
    Buffer.from(iv, "base64url")
  );
  decipher.setAuthTag(Buffer.from(tag, "base64url"));

  return Buffer.concat([
    decipher.update(Buffer.from(encrypted, "base64url")),
    decipher.final()
  ]).toString("utf8");
}

function currentEncryptionKey(): { version: typeof currentVersion | typeof legacyVersion; id: string; material: Buffer } {
  const [primary] = configuredEncryptionKeys();
  if (primary) {
    return { version: currentVersion, id: primary.id, material: primary.material };
  }

  return { version: legacyVersion, id: "local-jwt", material: legacyEncryptionKey() };
}

function configuredEncryptionKeys(): Array<{ id: string; material: Buffer }> {
  return (env.SECRET_ENCRYPTION_KEYS ?? "")
    .split(",")
    .map((entry) => entry.trim())
    .filter(Boolean)
    .map((entry) => {
      const [id, encodedKey] = entry.split(":");
      return {
        id,
        material: Buffer.from(encodedKey, "base64url")
      };
    });
}

function legacyEncryptionKey(): Buffer {
  return createHash("sha256").update(env.JWT_SECRET).digest();
}
