import { existsSync, readFileSync, statSync } from "node:fs";
import { dirname, join, resolve } from "node:path";

const root = resolve(process.cwd());
const defaultManifest = join(root, "docs", "release-evidence.template.json");
const requiredGateIds = [
  "backend-free-tier-deployment",
  "google-youtube-oauth-live",
  "android-device-qa",
  "play-console-store-policy",
  "creator-beta-acceptance"
];
const forbiddenPatterns = [
  /ya29\.[a-z0-9._-]+/i,
  /refresh_token/i,
  /client_secret/i,
  /private_key/i,
  /BEGIN PRIVATE KEY/i,
  /DATABASE_URL=.*:.*@/i,
  /JWT_SECRET=.+/i,
  /SECRET_ENCRYPTION_KEYS=.+/i
];
const placeholderPatterns = [
  /YYYY-MM-DD/,
  /<[^>]+>/,
  /TODO/i,
  /your-render-service/i,
  /your-static-site/i
];

const options = parseArgs(process.argv.slice(2));
const failures = [];
const warnings = [];
const passes = [];

if (options.help) {
  printHelp();
  process.exit(0);
}

const manifest = readManifest(options.manifest);
if (options.printRequired) {
  printRequired(manifest);
  process.exit(0);
}

checkManifest(manifest);
printResults();

function parseArgs(args) {
  const parsed = {
    manifest: defaultManifest,
    printRequired: false,
    requireComplete: false,
    help: false
  };

  for (const arg of args) {
    if (arg === "--help" || arg === "-h") {
      parsed.help = true;
    } else if (arg === "--print-required") {
      parsed.printRequired = true;
    } else if (arg === "--require-complete") {
      parsed.requireComplete = true;
    } else if (arg.startsWith("--manifest=")) {
      parsed.manifest = resolve(root, arg.slice("--manifest=".length));
    } else {
      fail(`Unknown option: ${arg}`);
    }
  }

  return parsed;
}

function readManifest(path) {
  if (!existsSync(path)) {
    fail(`Evidence manifest not found: ${path}`);
    return { gates: [] };
  }

  try {
    const parsed = JSON.parse(readFileSync(path, "utf8"));
    pass(`Evidence manifest parses: ${relativePath(path)}`);
    return parsed;
  } catch (error) {
    fail(`Evidence manifest is not valid JSON: ${error.message}`);
    return { gates: [] };
  }
}

function checkManifest(manifest) {
  assert(manifest.product === "ChatMod Mobile", "Evidence manifest is for ChatMod Mobile");
  assert(Boolean(manifest.releaseCandidate), "Evidence manifest includes releaseCandidate metadata");
  assert(Array.isArray(manifest.gates), "Evidence manifest includes gates array");

  const serialized = JSON.stringify(manifest, null, 2);
  for (const pattern of forbiddenPatterns) {
    assert(!pattern.test(serialized), `Evidence manifest avoids forbidden secret pattern ${pattern}`);
  }

  const gateIds = new Set((manifest.gates ?? []).map((gate) => gate.id));
  for (const gateId of requiredGateIds) {
    assert(gateIds.has(gateId), `Evidence manifest includes ${gateId}`);
  }

  for (const gate of manifest.gates ?? []) {
    checkGate(gate);
  }

  if (options.requireComplete) {
    for (const pattern of placeholderPatterns) {
      assert(!pattern.test(serialized), `Complete evidence manifest avoids placeholder pattern ${pattern}`);
    }
  } else if (placeholderPatterns.some((pattern) => pattern.test(serialized))) {
    warn("Evidence manifest still contains placeholders; this is expected for the template but not for release evidence.");
  }
}

function checkGate(gate) {
  assert(Boolean(gate.id), "Gate has an id");
  assert(Boolean(gate.title), `${gate.id} has a title`);
  assert(["pending_external", "in_progress", "complete", "blocked"].includes(gate.status), `${gate.id} has a valid status`);
  assert(Array.isArray(gate.requiredEvidence) && gate.requiredEvidence.length >= 4, `${gate.id} lists required evidence`);
  assert(Array.isArray(gate.commands) && gate.commands.length >= 1, `${gate.id} lists verification commands or manual flows`);
  assert(Array.isArray(gate.artifactPaths), `${gate.id} has artifactPaths array`);

  if (gate.status === "complete" || options.requireComplete) {
    assert(gate.status === "complete", `${gate.id} is marked complete for require-complete mode`);
    assert(gate.artifactPaths.length > 0, `${gate.id} has at least one artifact path`);
    for (const artifactPath of gate.artifactPaths) {
      const resolved = resolve(dirname(options.manifest), artifactPath);
      assert(existsSync(resolved) && statSync(resolved).isFile(), `${gate.id} artifact exists: ${artifactPath}`);
    }
  }
}

function printRequired(manifest) {
  console.log("External Release Evidence Requirements");
  console.log("--------------------------------------");
  for (const gate of manifest.gates ?? []) {
    console.log(`\n${gate.id}: ${gate.title}`);
    console.log(`Status: ${gate.status}`);
    for (const item of gate.requiredEvidence ?? []) {
      console.log(`- ${item}`);
    }
  }
}

function printHelp() {
  console.log(`Release evidence checker

Usage:
  npm run release:evidence:check
  npm run release:evidence:check -- --print-required
  npm run release:evidence:check -- --manifest=release-evidence/evidence.json --require-complete

Options:
  --manifest=<path>      Evidence manifest JSON. Defaults to docs/release-evidence.template.json.
  --print-required       Print required external evidence without failing on placeholders.
  --require-complete     Require all gates complete, no placeholders, and referenced artifacts present.
  --help                 Show this help.
`);
}

function assert(condition, message) {
  if (condition) {
    pass(message);
  } else {
    fail(message);
  }
}

function pass(message) {
  passes.push(message);
}

function warn(message) {
  warnings.push(message);
}

function fail(message) {
  failures.push(message);
}

function printResults() {
  for (const message of passes) {
    console.log(`PASS ${message}`);
  }
  for (const message of warnings) {
    console.warn(`WARN ${message}`);
  }
  for (const message of failures) {
    console.error(`FAIL ${message}`);
  }

  console.log(`\nRelease evidence source check: ${passes.length} passed, ${warnings.length} warning(s), ${failures.length} failure(s).`);
  if (failures.length > 0) {
    process.exit(1);
  }
}

function relativePath(path) {
  return path.startsWith(root) ? path.slice(root.length + 1).replaceAll("\\", "/") : path;
}
