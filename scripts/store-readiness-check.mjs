import { existsSync, readFileSync } from "node:fs";
import { join, resolve } from "node:path";

const root = resolve(process.cwd());
const passes = [];
const warnings = [];
const failures = [];

function pass(message) {
  passes.push(message);
}

function warn(message) {
  warnings.push(message);
}

function fail(message) {
  failures.push(message);
}

function assert(condition, message) {
  if (condition) {
    pass(message);
  } else {
    fail(message);
  }
}

function filePath(path) {
  return join(root, path);
}

function readText(path) {
  return readFileSync(filePath(path), "utf8");
}

function fileExists(path) {
  return existsSync(filePath(path));
}

function section(markdown, heading) {
  const escaped = heading.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const headingMatch = new RegExp(`^## ${escaped}\\s*$`, "m").exec(markdown);
  if (!headingMatch) {
    return "";
  }

  const afterHeading = markdown.slice(headingMatch.index + headingMatch[0].length).replace(/^\r?\n/, "");
  const nextHeading = afterHeading.search(/^##\s+/m);
  return (nextHeading >= 0 ? afterHeading.slice(0, nextHeading) : afterHeading).trim();
}

function normalizeListingText(text) {
  return text
    .replace(/Character count:.*$/gim, "")
    .replace(/\[(.*?)\]\([^)]+\)/g, "$1")
    .replace(/`([^`]+)`/g, "$1")
    .replace(/^\s*[-*]\s+/gm, "")
    .replace(/\r?\n{3,}/g, "\n\n")
    .trim();
}

function firstContentLine(text) {
  return text
    .split(/\r?\n/)
    .map((line) => line.trim())
    .find((line) => line && !line.toLowerCase().startsWith("character count:")) ?? "";
}

function includesEvery(text, phrases, label) {
  const lower = text.toLowerCase();
  for (const phrase of phrases) {
    assert(lower.includes(phrase.toLowerCase()), `${label} mentions ${phrase}`);
  }
}

function extractAndroidPermissions(manifest) {
  const permissions = new Set();
  for (const match of manifest.matchAll(/<uses-permission\s+[^>]*android:name="([^"]+)"/g)) {
    permissions.add(match[1]);
  }
  for (const match of manifest.matchAll(/android:permission="([^"]+)"/g)) {
    permissions.add(match[1]);
  }
  return [...permissions].sort();
}

function checkRequiredFiles() {
  const requiredFiles = [
    "android/app/src/main/AndroidManifest.xml",
    "android/app/src/main/java/com/chatmod/mobile/billing/PlayBillingManager.kt",
    "docs/PLAY_STORE_LISTING.md",
    "docs/PLAY_DATA_SAFETY.md",
    "docs/PRIVACY_DRAFT.md",
    "launch-site/privacy.html",
    "launch-site/terms.html",
    "package.json",
    ".github/workflows/ci.yml"
  ];

  for (const file of requiredFiles) {
    assert(fileExists(file), `${file} exists`);
  }
}

function checkListing() {
  const listing = readText("docs/PLAY_STORE_LISTING.md");
  const appName = firstContentLine(section(listing, "App Name"));
  const shortDescription = firstContentLine(section(listing, "Short Description"));
  const fullDescription = normalizeListingText(section(listing, "Full Description"));

  assert(appName === "ChatMod Mobile", "Play listing app name is ChatMod Mobile");
  assert(appName.length <= 30, `Play listing app name is within 30 chars (${appName.length})`);
  assert(shortDescription.length > 0, "Play listing short description is present");
  assert(shortDescription.length <= 80, `Play listing short description is within 80 chars (${shortDescription.length})`);
  assert(fullDescription.length > 0, "Play listing full description is present");
  assert(fullDescription.length <= 4000, `Play listing full description is within 4000 chars (${fullDescription.length})`);
  assert(listing.includes("not endorsed by YouTube or Google"), "Play listing avoids platform-affiliation claims");
  assert(!/official\s+(youtube|google)/i.test(listing), "Play listing does not claim official YouTube/Google status");
  assert(listing.includes("docs/PLAY_DATA_SAFETY.md"), "Play listing references the Data Safety worksheet");
  assert(listing.includes("https://support.google.com/googleplay/android-developer/answer/9859152"), "Play listing cites Google Play field limits");
  assert(listing.includes("https://support.google.com/googleplay/android-developer/answer/10787469"), "Play listing cites Google Play Data safety guidance");
}

function checkDataSafety() {
  const dataSafety = readText("docs/PLAY_DATA_SAFETY.md");
  const manifest = readText("android/app/src/main/AndroidManifest.xml");
  const permissions = extractAndroidPermissions(manifest);
  const sensitivePermissionPatterns = [
    /^android\.permission\.CAMERA$/,
    /^android\.permission\.RECORD_AUDIO$/,
    /^android\.permission\.ACCESS_FINE_LOCATION$/,
    /^android\.permission\.ACCESS_COARSE_LOCATION$/,
    /^android\.permission\.READ_CONTACTS$/,
    /^android\.permission\.READ_CALENDAR$/,
    /^android\.permission\.READ_PHONE_STATE$/,
    /^android\.permission\.READ_SMS$/,
    /^android\.permission\.READ_EXTERNAL_STORAGE$/,
    /^android\.permission\.WRITE_EXTERNAL_STORAGE$/,
    /^android\.permission\.MANAGE_EXTERNAL_STORAGE$/,
    /^android\.permission\.READ_MEDIA_/,
    /^com\.google\.android\.gms\.permission\.AD_ID$/
  ];

  assert(dataSafety.includes("https://support.google.com/googleplay/android-developer/answer/10787469"), "Data Safety worksheet cites Data safety form guidance");
  assert(dataSafety.includes("https://support.google.com/googleplay/android-developer/answer/10144311"), "Data Safety worksheet cites the User Data policy");
  assert(dataSafety.includes("https://support.google.com/googleplay/android-developer/answer/9859152"), "Data Safety worksheet cites listing setup limits");

  includesEvery(
    dataSafety,
    [
      "Personal info",
      "App activity",
      "App info and performance",
      "User-generated content",
      "Device or other IDs",
      "Financial info",
      "encrypted in transit",
      "Raw Google passwords are never collected",
      "not sold",
      "not used for model training",
      "account deletion",
      "local data wipe",
      "Data retention pruning"
    ],
    "Data Safety worksheet"
  );

  for (const permission of permissions) {
    assert(dataSafety.includes(permission), `Data Safety worksheet documents ${permission}`);
  }

  for (const permission of permissions) {
    const isSensitive = sensitivePermissionPatterns.some((pattern) => pattern.test(permission));
    assert(!isSensitive, `Android manifest avoids sensitive undeclared store permission ${permission}`);
  }

  assert(
    dataSafety.includes("camera, microphone, contacts, location, calendar, phone, SMS, advertising ID"),
    "Data Safety worksheet records sensitive permissions not requested"
  );
}

function checkPolicyConsistency() {
  const privacyDraft = readText("docs/PRIVACY_DRAFT.md");
  const launchPrivacy = readText("launch-site/privacy.html");
  const dataSafety = readText("docs/PLAY_DATA_SAFETY.md");

  includesEvery(
    privacyDraft,
    [
      "Launch-site beta-interest email",
      "Google passwords",
      "account deletion",
      "Disconnect YouTube",
      "Export settings and logs",
      "retention command"
    ],
    "Privacy draft"
  );

  includesEvery(
    launchPrivacy,
    [
      "Beta-interest email",
      "Google passwords",
      "export account/log data",
      "account deletion"
    ],
    "Launch privacy page"
  );

  assert(dataSafety.includes("docs/PRIVACY_DRAFT.md"), "Data Safety worksheet points back to the privacy draft");
  assert(dataSafety.includes("launch-site/privacy.html"), "Data Safety worksheet points back to launch-site privacy copy");
}

function checkChecklistAndCi() {
  const packageJson = JSON.parse(readText("package.json"));
  const ci = readText(".github/workflows/ci.yml");
  const release = readText("docs/PRODUCTION_RELEASE_CHECKLIST.md");
  const buildChecklist = readText("BUILD_CHECKLIST.md");
  const deployment = readText("docs/DEPLOYMENT.md");
  const readiness = readText("scripts/production-readiness.mjs");

  assert(Boolean(packageJson.scripts?.["store:check"]), "package.json exposes npm run store:check");
  assert(packageJson.scripts?.["store:check"] === "node scripts/store-readiness-check.mjs", "store:check runs the store readiness checker");
  assert(ci.includes("npm run store:check"), "CI runs the store readiness checker");
  assert(release.includes("npm run store:check"), "Production release checklist includes store readiness gate");
  assert(buildChecklist.includes("Play Data Safety source worksheet"), "Build checklist tracks Play Data Safety source worksheet");
  assert(deployment.includes("npm run store:check"), "Deployment docs include store readiness command");
  assert(readiness.includes("scripts/store-readiness-check.mjs"), "Production readiness checker requires store readiness source");
  assert(readiness.includes("docs/PLAY_DATA_SAFETY.md"), "Production readiness checker requires Data Safety worksheet");
}

function checkPlayBillingSource() {
  const gradle = readText("android/app/build.gradle.kts");
  const manager = readText("android/app/src/main/java/com/chatmod/mobile/billing/PlayBillingManager.kt");
  const activity = readText("android/app/src/main/java/com/chatmod/mobile/MainActivity.kt");
  const dashboard = readText("android/app/src/main/java/com/chatmod/mobile/ui/dashboard/DashboardScreen.kt");
  const billingDocs = readText("docs/BILLING.md");
  const pricingDocs = readText("docs/PRICING.md");
  const playListing = readText("docs/PLAY_STORE_LISTING.md");

  includesEvery(
    gradle,
    [
      'implementation("com.android.billingclient:billing:8.3.0")'
    ],
    "Android Play Billing dependency"
  );

  includesEvery(
    manager,
    [
      "BillingClient.newBuilder(appContext)",
      ".enablePendingPurchases(",
      "PendingPurchasesParams.newBuilder()",
      ".enableAutoServiceReconnection()",
      "queryProductDetailsAsync(params)",
      "launchBillingFlow(activity, params)",
      "queryPurchasesAsync(params)",
      "acknowledgePurchase(params)",
      "chatmod_pro_monthly",
      "chatmod_creator_monthly"
    ],
    "Android Play Billing manager"
  );
  assert(!manager.includes("chatmod_lifetime"), "Android Play Billing manager does not query a Lifetime SKU");
  assert(!manager.includes("BillingClient.ProductType.INAPP"), "Android Play Billing manager does not query one-time Play products");

  includesEvery(
    activity,
    [
      "private val playBillingManager: PlayBillingManager by inject()",
      "playBillingManager.purchases.collect",
      "viewModel.validatePlayBillingPurchase",
      "playBillingManager.acknowledgePurchase",
      "playBillingManager.launchPurchase",
      "playBillingManager.restorePurchases"
    ],
    "MainActivity Play Billing wiring"
  );

  includesEvery(
    dashboard,
    [
      "\"Play plans\"",
      "Text(\"Restore purchases\")",
      "\"Go Pro\"",
      "\"Go Creator\""
    ],
    "Billing tab purchase UI"
  );

  includesEvery(
    billingDocs,
    [
      "Android Google Play Billing client purchase flow",
      "backend validation succeeds",
      "real Play Console credentials",
      "Lifetime purchase is not offered"
    ],
    "Billing docs"
  );
  assert(pricingDocs.includes("not offered for MVP, closed beta, or public v1"), "Pricing docs close the Lifetime decision for MVP");
  assert(pricingDocs.includes("chatmod_lifetime"), "Pricing docs block accidental Lifetime SKU creation");
  assert(playListing.includes("Lifetime purchase is not offered"), "Play listing draft avoids Lifetime marketing copy");
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

  console.log(`\nStore readiness source check: ${passes.length} passed, ${warnings.length} warning(s), ${failures.length} failure(s).`);
  if (failures.length > 0) {
    process.exit(1);
  }
}

checkRequiredFiles();
checkListing();
checkDataSafety();
checkPolicyConsistency();
checkChecklistAndCi();
checkPlayBillingSource();
printResults();
