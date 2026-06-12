export interface CommandResponseSafetyResult {
  safe: boolean;
  reason: string | null;
}

const urlCandidatePattern = /\bhttps?:\/\/[^\s<>"']+|\bwww\.[^\s<>"']+|\b(?:javascript|data|file|vbscript|ftp):[^\s<>"']+/gi;
const unsafeProtocolPattern = /^(?:javascript|data|file|vbscript|ftp):/i;

export function validateCommandResponseSafety(response: string): CommandResponseSafetyResult {
  const candidates = response.match(urlCandidatePattern) ?? [];

  for (const candidate of candidates) {
    const normalized = normalizeUrlCandidate(candidate);

    if (unsafeProtocolPattern.test(normalized)) {
      return {
        safe: false,
        reason: "Command responses can only use http or https links."
      };
    }

    const url = parseUrl(normalized);
    if (!url) {
      return {
        safe: false,
        reason: "Command response contains an invalid link."
      };
    }

    if (url.username || url.password) {
      return {
        safe: false,
        reason: "Command response links cannot include embedded credentials."
      };
    }

    if (isPrivateOrLocalHost(url.hostname)) {
      return {
        safe: false,
        reason: "Command response links cannot target private or local network hosts."
      };
    }
  }

  return {
    safe: true,
    reason: null
  };
}

function normalizeUrlCandidate(candidate: string): string {
  const trimmed = candidate.replace(/[),.!?]+$/g, "");
  return trimmed.toLowerCase().startsWith("www.") ? `https://${trimmed}` : trimmed;
}

function parseUrl(value: string): URL | null {
  try {
    const url = new URL(value);
    return url.protocol === "http:" || url.protocol === "https:" ? url : null;
  } catch {
    return null;
  }
}

function isPrivateOrLocalHost(hostname: string): boolean {
  const normalized = hostname.toLowerCase().replace(/^\[|\]$/g, "");

  if (
    normalized === "localhost" ||
    normalized === "::1" ||
    normalized.endsWith(".localhost") ||
    normalized.endsWith(".local")
  ) {
    return true;
  }

  if (/^\d+\.\d+\.\d+\.\d+$/.test(normalized)) {
    const [first, second] = normalized.split(".").map(Number);
    return (
      first === 10 ||
      first === 127 ||
      (first === 172 && second >= 16 && second <= 31) ||
      (first === 192 && second === 168) ||
      (first === 169 && second === 254)
    );
  }

  return normalized.startsWith("fc") || normalized.startsWith("fd") || normalized.startsWith("fe80:");
}
