#!/usr/bin/env node
/**
 * ChatMod Mobile - Production Deploy Script
 *
 * Automates free-tier deployment to:
 *   - Neon (PostgreSQL)
 *   - Render (Backend API)
 *   - Cloudflare Pages (Launch site)
 *
 * Usage:
 *   node scripts/deploy.mjs --help
 */

const HELP = `
ChatMod Mobile Deploy Script
=============================
Usage: node scripts/deploy.mjs <command> [options]

Commands:
  check              Run all pre-deployment checks locally
  neon:setup         Create a Neon Free Postgres project via API
  render:setup       Create/update a Render Blueprint from render.yaml
  render:deploy      Trigger a manual deploy on Render
  cf:setup           Configure Cloudflare Pages for launch-site/
  full               Run the full deployment pipeline

Options:
  --help             Show this help
  --verbose          Print detailed output for each step
  --yes              Skip confirmation prompts
  --env-file         Path to production env file (default: .env.production)
  --neon-api-key     Neon API key (or NEON_API_KEY env)
  --render-api-key   Render API key (or RENDER_API_KEY env)
  --cf-api-token     Cloudflare API token (or CLOUDFLARE_API_TOKEN env)
  --cf-account-id    Cloudflare account ID (or CLOUDFLARE_ACCOUNT_ID env)

Requirements:
  - Node.js >= 22
  - npm ci already run
  - Backend TypeScript built (npm run backend:build)
  - API keys for services (set via env or flags)

Each step is idempotent and safe to re-run.
`.trim();

const log = {
  info: (msg) => console.log(`  INFO  ${msg}`),
  ok: (msg) => console.log(`  ${green("OK")}  ${msg}`),
  warn: (msg) => console.warn(` ${yellow("WARN")}  ${msg}`),
  fail: (msg) => { console.error(` ${red("FAIL")}  ${msg}`); process.exitCode = 1; },
  step: (msg) => console.log(`\n${bold(msg)}`)
};

function bold(s) { return `\x1b[1m${s}\x1b[22m`; }
function green(s) { return `\x1b[32m${s}\x1b[39m`; }
function yellow(s) { return `\x1b[33m${s}\x1b[39m`; }
function red(s) { return `\x1b[31m${s}\x1b[39m`; }

async function main() {
  const args = process.argv.slice(2);
  if (args.includes("--help") || args.length === 0) {
    console.log(HELP);
    process.exit(0);
  }

  const command = args[0];
  const opts = parseOptions(args.slice(1));

  switch (command) {
    case "check":
      await runPreDeployChecks();
      break;
    case "neon:setup":
      await setupNeon(opts);
      break;
    case "render:setup":
      await setupRender(opts);
      break;
    case "render:deploy":
      await deployRender(opts);
      break;
    case "cf:setup":
      await setupCloudflare(opts);
      break;
    case "full":
      await runFullDeploy(opts);
      break;
    default:
      console.error(`Unknown command: ${command}`);
      console.log(HELP);
      process.exit(1);
  }
}

async function runPreDeployChecks() {
  log.step("Running pre-deployment checks...");

  const { execSync } = await import("child_process");

  const checks = [
    { name: "Node.js version >= 22", cmd: "node --version", test: (v) => parseFloat(v.replace("v", "")) >= 22 },
    { name: "npm install done", cmd: "npm ls --depth=0 2>&1", test: () => true },
    { name: "Build passes", cmd: "npm run backend:build 2>&1", test: (o) => !o.includes("error") },
    { name: "Tests pass", cmd: "npm run backend:test 2>&1", test: (o) => o.includes("passed") },
    { name: "Production readiness check", cmd: "npm run production:check 2>&1", test: (o) => o.includes("0 failure") },
    { name: "Launch site check", cmd: "npm run launch-site:check 2>&1", test: (o) => o.includes("0 failure") },
    { name: "Prisma validate", cmd: "npm run backend:db:validate 2>&1", test: (o) => !o.includes("error") },
    { name: "NPM audit", cmd: "npm audit --audit-level=moderate 2>&1", test: (o) => !o.includes("HIGH") && !o.includes("CRITICAL") }
  ];

  let passed = 0;
  let failed = 0;
  for (const check of checks) {
    try {
      const output = execSync(check.cmd, { encoding: "utf8", timeout: 60000 });
      if (check.test(output)) {
        log.ok(check.name);
        passed++;
      } else {
        log.fail(check.name);
        failed++;
      }
    } catch (err) {
      log.fail(`${check.name}: ${err.message}`);
      failed++;
    }
  }

  log.step(`Checks: ${passed} passed, ${failed} failed`);
  return { passed, failed };
}

async function setupNeon(opts) {
  log.step("Setting up Neon Free Postgres...");
  const apiKey = opts.neonApiKey || process.env.NEON_API_KEY;
  if (!apiKey) {
    log.fail("NEON_API_KEY is required. Get one at https://neon.tech/docs/manage/api-keys");
    return;
  }

  const projectName = "chatmod-mobile";
  const branchName = "main";

  try {
    const createRes = await fetch("https://console.neon.tech/api/v2/projects", {
      method: "POST",
      headers: { "Authorization": `Bearer ${apiKey}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        project: { name: projectName, region_id: "aws-us-east-1", pg_version: 16 }
      })
    });

    if (!createRes.ok) {
      const body = await createRes.text();
      if (createRes.status === 409 || body.includes("already exists")) {
        log.info("Neon project already exists.");
      } else {
        log.fail(`Failed to create Neon project: ${createRes.status} ${body}`);
        return;
      }
    } else {
      log.ok("Neon Free Postgres project created.");
    }

    const listRes = await fetch("https://console.neon.tech/api/v2/projects", {
      headers: { "Authorization": `Bearer ${apiKey}` }
    });
    const listData = await listRes.json();
    const project = listData.projects?.find(p => p.name === projectName);
    if (!project) { log.fail("Could not find Neon project."); return; }

    const connRes = await fetch(
      `https://console.neon.tech/api/v2/projects/${project.id}/branches`,
      { headers: { "Authorization": `Bearer ${apiKey}` } }
    );
    const connData = await connRes.json();
    const branch = connData.branches?.[0];
    if (!branch) { log.fail("No branch found."); return; }

    const endpointRes = await fetch(
      `https://console.neon.tech/api/v2/projects/${project.id}/endpoints`,
      { headers: { "Authorization": `Bearer ${apiKey}` } }
    );
    const endpointData = await endpointRes.json();
    const endpoint = endpointData.endpoints?.[0];
    if (!endpoint) { log.fail("No endpoint found."); return; }

    const connectionString = `postgresql://${endpoint.user}:${endpoint.password}@${endpoint.host}:5432/${endpoint.database}?sslmode=require`;

    log.ok(`Neon database ready.`);
    log.info(`Connection string: ${connectionString.replace(/:[^:@]+@/, ":***@")}`);

    log.info("\nAdd to your .env.production or Render secrets:");
    console.log(`DATABASE_URL=${connectionString}`);

    return { projectId: project.id, connectionString };
  } catch (err) {
    log.fail(`Neon setup error: ${err.message}`);
  }
}

async function setupRender(opts) {
  log.step("Setting up Render deployment...");
  const apiKey = opts.renderApiKey || process.env.RENDER_API_KEY;
  if (!apiKey) {
    log.fail("RENDER_API_KEY is required. Get one at https://dashboard.render.com/account/api-keys");
    return;
  }

  const serviceName = "chatmod-backend";

  try {
    const listRes = await fetch("https://api.render.com/v1/services", {
      headers: { "Authorization": `Bearer ${apiKey}` }
    });
    const services = await listRes.json();
    const existing = services.find(s => s.service?.name === serviceName);

    if (existing) {
      log.info(`Render service '${serviceName}' already exists (ID: ${existing.service.id}).`);
      log.info("Update env vars in Render dashboard: https://dashboard.render.com");
      return existing;
    }

    log.info("Creating Render service via Blueprint...");
    log.info("Go to https://dashboard.render.com/select-repo and connect your repository.");
    log.info("Render will auto-detect render.yaml and create the service.");
    log.info("");
    log.info("After creation, set these encrypted env vars in Render Dashboard:");
    log.info("  DATABASE_URL (from Neon setup)");
    log.info("  CORS_ORIGIN (e.g., https://chatmod-mobile.pages.dev)");
    log.info("  JWT_SECRET (generate with: node -e \"console.log(require('crypto').randomBytes(32).toString('hex'))\")");
    log.info("  SECRET_ENCRYPTION_KEYS (generate: primary:<32-byte-base64url>)");
    log.info("  GOOGLE_OAUTH_CLIENT_ID (from Google Cloud Console)");
    log.info("  GOOGLE_OAUTH_CLIENT_SECRET");
    log.info("  GOOGLE_OAUTH_REDIRECT_URI (e.g., https://your-app.onrender.com/youtube/oauth/callback)");
    log.info("  GOOGLE_PLAY_PACKAGE_NAME=com.chatmod.mobile");
    log.info("  GOOGLE_PLAY_SERVICE_ACCOUNT_JSON_BASE64 (from Google Play Console)");
    log.info("  ADMIN_API_KEY (optional, 32+ chars)");
  } catch (err) {
    log.fail(`Render setup error: ${err.message}`);
  }
}

async function deployRender(opts) {
  log.step("Triggering Render deploy...");
  const apiKey = opts.renderApiKey || process.env.RENDER_API_KEY;
  if (!apiKey) {
    log.fail("RENDER_API_KEY is required.");
    return;
  }

  try {
    const listRes = await fetch("https://api.render.com/v1/services", {
      headers: { "Authorization": `Bearer ${apiKey}` }
    });
    const services = await listRes.json();
    const service = services.find(s => s.service?.name === "chatmod-backend");

    if (!service) {
      log.fail("Service not found. Run 'render:setup' first.");
      return;
    }

    const deployRes = await fetch(
      `https://api.render.com/v1/services/${service.service.id}/deploys`,
      {
        method: "POST",
        headers: {
          "Authorization": `Bearer ${apiKey}`,
          "Content-Type": "application/json"
        },
        body: JSON.stringify({ clearCache: "do_not_clear" })
      }
    );

    if (!deployRes.ok) {
      const body = await deployRes.text();
      log.fail(`Deploy failed: ${deployRes.status} ${body}`);
      return;
    }

    const deploy = await deployRes.json();
    log.ok(`Deploy triggered: https://dashboard.render.com/web/${service.service.id}/deploys/${deploy.id}`);
  } catch (err) {
    log.fail(`Deploy error: ${err.message}`);
  }
}

async function setupCloudflare(opts) {
  log.step("Setting up Cloudflare Pages for launch-site...");
  const apiToken = opts.cfApiToken || process.env.CLOUDFLARE_API_TOKEN;
  const accountId = opts.cfAccountId || process.env.CLOUDFLARE_ACCOUNT_ID;

  if (!apiToken || !accountId) {
    log.fail("CLOUDFLARE_API_TOKEN and CLOUDFLARE_ACCOUNT_ID are required.");
    log.info("Get them at: https://dash.cloudflare.com/profile/api-tokens");
    return;
  }

  const projectName = "chatmod-mobile";
  const baseUrl = "https://chatmod-mobile.pages.dev";

  try {
    const existingRes = await fetch(
      `https://api.cloudflare.com/client/v4/accounts/${accountId}/pages/projects/${projectName}`,
      { headers: { "Authorization": `Bearer ${apiToken}` } }
    );

    if (existingRes.ok) {
      log.info(`Cloudflare Pages project '${projectName}' already exists.`);
    } else {
      log.info("Create the project manually:");
      log.info(`1. Go to https://dash.cloudflare.com/${accountId}/pages`);
      log.info(`2. Connect your Git repository`);
      log.info(`3. Set build command: (leave empty)`);
      log.info(`4. Set build output directory: /launch-site`);
      log.info(`5. Deploy`);
    }

    log.info("\nAfter deploy, set CORS_ORIGIN in Render env vars to:");
    log.info(`  CORS_ORIGIN=${baseUrl}`);

    log.info("\nAdd Cloudflare _headers file for security:");
    log.info("launch-site/_headers:");
    log.info("  /*");
    log.info("    X-Content-Type-Options: nosniff");
    log.info("    X-Frame-Options: SAMEORIGIN");
    log.info("    Referrer-Policy: no-referrer");
    log.info("    Permissions-Policy: camera=(), microphone=()");

    return { projectName, baseUrl };
  } catch (err) {
    log.fail(`Cloudflare setup error: ${err.message}`);
  }
}

async function runFullDeploy(opts) {
  log.step("=== ChatMod Mobile Full Deployment ===");

  if (!opts.yes) {
    console.log("This will deploy ChatMod Mobile to production (Render + Neon + Cloudflare Pages).");
    console.log("Make sure you have API keys for each service.");
    console.log("");
  }

  const checks = await runPreDeployChecks();
  if (checks.failed > 0 && !opts.yes) {
    log.fail("Pre-deploy checks failed. Fix them or re-run with --yes to force.");
    return;
  }

  const neonResult = await setupNeon(opts);
  await setupRender(opts);

  if (opts.cfApiToken || process.env.CLOUDFLARE_API_TOKEN) {
    await setupCloudflare(opts);
  } else {
    log.warn("Skipping Cloudflare Pages (no API token). Set CLOUDFLARE_API_TOKEN to auto-setup.");
  }

  log.step("=== Deployment Summary ===");
  log.ok("Pre-deploy checks complete.");
  if (neonResult) {
    log.ok("Neon database configured.");
    log.info(`  Project ID: ${neonResult.projectId}`);
  } else {
    log.warn("Neon setup skipped or failed.");
  }
  log.info("Render: Create Blueprint from your Git repo at https://dashboard.render.com");
  log.info("Cloudflare: Deploy launch-site/ from https://dash.cloudflare.com/pages");
  log.info("");
  log.info("Next steps:");
  log.info("  1. Set all Render env secrets (see render.yaml for the full list)");
  log.info("  2. Run: npm run backend:db:deploy (from CI after first deploy)");
  log.info("  3. Run: npm run backend:smoke -- --base-url=https://your-app.onrender.com --require-database");
  log.info("  4. Configure Google OAuth in Google Cloud Console");
  log.info("  5. Submit Play Data Safety in Google Play Console");
}

function parseOptions(args) {
  const opts = {};
  for (let i = 0; i < args.length; i++) {
    switch (args[i]) {
      case "--verbose": opts.verbose = true; break;
      case "--yes": opts.yes = true; break;
      case "--env-file": opts.envFile = args[++i]; break;
      case "--neon-api-key": opts.neonApiKey = args[++i]; break;
      case "--render-api-key": opts.renderApiKey = args[++i]; break;
      case "--cf-api-token": opts.cfApiToken = args[++i]; break;
      case "--cf-account-id": opts.cfAccountId = args[++i]; break;
    }
  }
  return opts;
}

main().catch((err) => {
  console.error(`Fatal error: ${err.message}`);
  process.exit(1);
});
