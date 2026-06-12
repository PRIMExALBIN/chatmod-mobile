import { describe, expect, it } from "vitest";
import type { AuthContext } from "../auth/sessionToken.js";
import {
  createBackupStore,
  decryptBackupConfigFromStorage,
  encryptBackupConfigForStorage
} from "./backupStore.js";

describe("BackupStore serialization", () => {
  it("returns isolated JSON config copies", async () => {
    const store = createBackupStore();
    const auth: AuthContext = {
      subject: "device-serialization-1",
      deviceId: "device-serialization-1",
      installId: "install-serialization-1"
    };

    const created = await store.create(auth, {
      profileId: "profile-serialization-1",
      channelId: "channel-serialization-1",
      profileName: "Serialization profile",
      clientVersion: "0.1.0",
      config: {
        kind: "chatmod-settings-v1",
        commands: [
          {
            id: "cmd-1",
            aliases: ["!rules"]
          }
        ],
        timers: [
          {
            id: "timer-1",
            enabled: true
          }
        ]
      }
    });

    (created.config.commands as Array<Record<string, unknown>>)[0].aliases = ["!mutated"];

    const fetched = await store.get(auth, created.id);
    expect(fetched.config).toMatchObject({
      commands: [
        {
          id: "cmd-1",
          aliases: ["!rules"]
        }
      ]
    });

    const listed = await store.list(auth);
    (listed[0].config.timers as Array<Record<string, unknown>>)[0].enabled = false;

    await expect(store.get(auth, created.id)).resolves.toMatchObject({
      config: {
        timers: [
          {
            id: "timer-1",
            enabled: true
          }
        ]
      }
    });
  });
});

describe("Backup config encryption", () => {
  it("encrypts backup config without retaining plaintext in stored JSON", () => {
    const config = {
      kind: "chatmod-settings-v1",
      commands: [
        {
          name: "!secret",
          response: "Private sponsor code"
        }
      ],
      timers: [
        {
          name: "Members reminder",
          message: "Private stream note"
        }
      ]
    };

    const stored = encryptBackupConfigForStorage(config);

    expect(stored).toMatchObject({
      kind: "chatmod-encrypted-backup-config-v1"
    });
    expect(JSON.stringify(stored)).not.toContain("Private sponsor code");
    expect(JSON.stringify(stored)).not.toContain("Private stream note");
    expect(decryptBackupConfigFromStorage(stored)).toMatchObject(config);
  });

  it("keeps legacy plaintext backup config readable", () => {
    const legacyConfig = {
      kind: "chatmod-settings-v1",
      commands: [{ name: "!rules" }],
      timers: []
    };

    expect(decryptBackupConfigFromStorage(legacyConfig)).toMatchObject(legacyConfig);
  });
});
