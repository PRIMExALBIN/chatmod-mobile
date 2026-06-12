import { HttpError } from "../../lib/httpErrors.js";

export type DiscordAlertSeverity = "info" | "warning" | "critical";

export interface DiscordWebhookPayload {
  username: string;
  embeds: Array<{
    title: string;
    description: string;
    color: number;
    timestamp: string;
    footer: {
      text: string;
    };
    fields?: Array<{
      name: string;
      value: string;
      inline?: boolean;
    }>;
  }>;
}

export interface DiscordWebhookSender {
  send(webhookUrl: string, payload: DiscordWebhookPayload): Promise<void>;
}

export class FetchDiscordWebhookSender implements DiscordWebhookSender {
  async send(webhookUrl: string, payload: DiscordWebhookPayload): Promise<void> {
    const response = await fetch(webhookUrl, {
      method: "POST",
      headers: {
        "content-type": "application/json"
      },
      body: JSON.stringify(payload)
    });

    if (response.ok) {
      return;
    }

    if (response.status === 429) {
      throw new HttpError(429, "Discord webhook is rate limited. Try again shortly.");
    }

    throw new HttpError(502, "Discord webhook delivery failed.");
  }
}

export function buildDiscordAlertPayload(input: {
  title: string;
  detail: string;
  severity: DiscordAlertSeverity;
  eventType: string;
  profileId: string;
  metadata?: Record<string, unknown>;
  now?: Date;
}): DiscordWebhookPayload {
  const fields = Object.entries(input.metadata ?? {})
    .filter(([, value]) => value !== null && value !== undefined && typeof value !== "object")
    .slice(0, 5)
    .map(([name, value]) => ({
      name: truncateDiscordText(name, 64),
      value: truncateDiscordText(String(value), 256),
      inline: true
    }));

  return {
    username: "ChatMod Mobile",
    embeds: [
      {
        title: truncateDiscordText(input.title, 120),
        description: truncateDiscordText(input.detail, 1000),
        color: severityColor(input.severity),
        timestamp: (input.now ?? new Date()).toISOString(),
        footer: {
          text: `ChatMod Mobile | ${input.eventType} | ${input.profileId}`
        },
        fields: fields.length > 0 ? fields : undefined
      }
    ]
  };
}

function severityColor(severity: DiscordAlertSeverity): number {
  switch (severity) {
    case "critical":
      return 0xd93025;
    case "warning":
      return 0xf9ab00;
    case "info":
    default:
      return 0x1a73e8;
  }
}

function truncateDiscordText(value: string, maxLength: number): string {
  const trimmed = value.trim();
  if (trimmed.length <= maxLength) {
    return trimmed;
  }

  return `${trimmed.slice(0, Math.max(0, maxLength - 3))}...`;
}
