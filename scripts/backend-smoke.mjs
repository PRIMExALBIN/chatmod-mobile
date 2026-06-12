const defaultBaseUrl = process.env.CHATMOD_SMOKE_BASE_URL ?? "http://127.0.0.1:4100";

const options = parseOptions(process.argv.slice(2), process.env);
const results = [];

try {
  await smokeHealth();
  await smokeReadiness();
  await smokeCompatibility();
  await smokeProtectedRouteRequestId();
  await smokeValidationRequestId();

  for (const result of results) {
    console.log(`PASS ${result}`);
  }
  console.log(`\nBackend smoke check passed for ${options.baseUrl}`);
} catch (error) {
  for (const result of results) {
    console.log(`PASS ${result}`);
  }
  console.error(`FAIL ${error instanceof Error ? error.message : String(error)}`);
  process.exit(1);
}

async function smokeHealth() {
  const response = await requestJson("/health");
  assert(response.status === 200, `/health returned ${response.status}`);
  assert(response.body?.status === "ok", "/health status is ok");
  assert(response.body?.service === "chatmod-mobile-api", "/health service name matches ChatMod backend");
  results.push("/health returns ChatMod service status");
}

async function smokeReadiness() {
  const response = await requestJson("/health/ready");
  assert(response.status === 200, `/health/ready returned ${response.status}`);
  assert(response.body?.status === "ok", "/health/ready status is ok");

  const databaseStatus = response.body?.dependencies?.database;
  if (options.requireDatabase) {
    assert(databaseStatus === "ok", `/health/ready database status is ${String(databaseStatus)}, expected ok`);
  } else {
    assert(
      databaseStatus === "ok" || databaseStatus === "not_configured",
      `/health/ready database status is unexpected: ${String(databaseStatus)}`
    );
  }

  results.push("/health/ready returns dependency readiness");
}

async function smokeCompatibility() {
  const response = await requestJson("/app/compatibility?platform=android&versionName=0.1.0&versionCode=1");
  assert(response.status === 200, `/app/compatibility returned ${response.status}`);
  assert(response.body?.platform === "android", "/app/compatibility returns android platform");
  assert(typeof response.body?.minimumSupportedVersionCode === "number", "/app/compatibility includes minimum version code");
  assert(typeof response.body?.latestVersionCode === "number", "/app/compatibility includes latest version code");
  assert(typeof response.body?.updateRequired === "boolean", "/app/compatibility includes updateRequired flag");
  results.push("/app/compatibility returns Android version policy");
}

async function smokeProtectedRouteRequestId() {
  const response = await requestJson("/entitlements/current");
  assert(response.status === 401, `/entitlements/current without auth returned ${response.status}`);
  assert(response.body?.error === "UNAUTHORIZED", "/entitlements/current returns unauthorized error");
  assertRequestId(response, "/entitlements/current");
  results.push("protected routes return request IDs on 401");
}

async function smokeValidationRequestId() {
  const response = await requestJson("/feedback/beta-interest", {
    method: "POST",
    headers: {
      "content-type": "application/json"
    },
    body: JSON.stringify({
      email: "not-an-email",
      source: "launch-site"
    })
  });
  assert(response.status === 400, `/feedback/beta-interest invalid payload returned ${response.status}`);
  assert(response.body?.error === "VALIDATION_ERROR", "/feedback/beta-interest returns validation error");
  assertRequestId(response, "/feedback/beta-interest");
  results.push("public beta-interest validation returns request IDs on 400");
}

async function requestJson(path, init = {}) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), options.timeoutMs);
  try {
    const response = await fetch(new URL(path, options.baseUrl), {
      ...init,
      signal: controller.signal,
      headers: {
        accept: "application/json",
        ...(init.headers ?? {})
      }
    });
    const text = await response.text();
    let body = null;
    if (text.length > 0) {
      try {
        body = JSON.parse(text);
      } catch {
        throw new Error(`${path} returned non-JSON response: ${text.slice(0, 120)}`);
      }
    }

    return {
      status: response.status,
      headers: response.headers,
      body
    };
  } catch (error) {
    if (error instanceof Error && error.name === "AbortError") {
      throw new Error(`${path} timed out after ${options.timeoutMs}ms`);
    }
    throw error;
  } finally {
    clearTimeout(timeout);
  }
}

function assertRequestId(response, label) {
  const headerRequestId = response.headers.get("x-request-id");
  assert(Boolean(headerRequestId), `${label} did not include x-request-id`);
  assert(response.body?.requestId === headerRequestId, `${label} requestId body/header mismatch`);
}

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

function parseOptions(args, env) {
  let baseUrl = env.CHATMOD_SMOKE_BASE_URL ?? defaultBaseUrl;
  let timeoutMs = integerOption(env.CHATMOD_SMOKE_TIMEOUT_MS, 8000, "CHATMOD_SMOKE_TIMEOUT_MS");
  let requireDatabase = env.CHATMOD_SMOKE_REQUIRE_DATABASE === "true";

  for (const arg of args) {
    if (arg === "--require-database") {
      requireDatabase = true;
      continue;
    }
    if (arg === "--allow-missing-database") {
      requireDatabase = false;
      continue;
    }

    const match = /^--([a-z0-9-]+)=(.+)$/i.exec(arg);
    if (!match) {
      throw new Error(`Unknown smoke option: ${arg}`);
    }

    if (match[1] === "base-url") {
      baseUrl = match[2];
      continue;
    }
    if (match[1] === "timeout-ms") {
      timeoutMs = integerOption(match[2], 8000, "--timeout-ms");
      continue;
    }

    throw new Error(`Unknown smoke option: ${arg}`);
  }

  return {
    baseUrl: normalizeBaseUrl(baseUrl),
    timeoutMs,
    requireDatabase
  };
}

function normalizeBaseUrl(value) {
  try {
    const url = new URL(value);
    if (url.protocol !== "http:" && url.protocol !== "https:") {
      throw new Error("Base URL must use http or https.");
    }
    url.pathname = url.pathname.replace(/\/+$/, "");
    url.search = "";
    url.hash = "";
    return url.toString();
  } catch (error) {
    throw new Error(`Invalid smoke base URL: ${value}`);
  }
}

function integerOption(value, fallback, label) {
  if (value === undefined || value === "") {
    return fallback;
  }

  const parsed = Number(value);
  if (!Number.isInteger(parsed) || parsed < 1000 || parsed > 60000) {
    throw new Error(`${label} must be an integer from 1000 to 60000.`);
  }

  return parsed;
}
