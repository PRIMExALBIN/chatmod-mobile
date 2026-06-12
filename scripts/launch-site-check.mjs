import { existsSync, readFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";

const root = resolve(process.cwd());
const siteDir = join(root, "launch-site");
const failures = [];
const passes = [];

const requiredFiles = [
  "index.html",
  "admin.html",
  "privacy.html",
  "terms.html",
  "styles.css",
  "script.js",
  "admin-dashboard.js",
  "assets/chatmod-mark.svg",
  "assets/chatmod-phone-dashboard.svg"
];

for (const file of requiredFiles) {
  assert(existsSync(join(siteDir, file)), `launch-site/${file} exists`);
}

const htmlFiles = ["index.html", "admin.html", "privacy.html", "terms.html"];
for (const file of htmlFiles) {
  checkHtmlPage(file);
}

checkIndexPage();
checkAdminPage();
checkPolicyPages();
checkScript();
checkAdminScript();
checkStyles();
printResults();

function checkHtmlPage(file) {
  const html = readSite(file);
  assert(/^<!doctype html>/i.test(html), `${file} starts with doctype`);
  assert(html.includes('<html lang="en">'), `${file} declares English language`);
  assert(html.includes('<meta charset="utf-8"'), `${file} declares utf-8 charset`);
  assert(html.includes('name="viewport"'), `${file} includes responsive viewport`);
  assert(html.includes('name="description"'), `${file} includes a meta description`);
  assert(/<title>[^<]*ChatMod Mobile[^<]*<\/title>/.test(html), `${file} title names ChatMod Mobile`);
  assert(html.includes('href="assets/chatmod-mark.svg"'), `${file} links favicon`);
  assert(html.includes('href="styles.css"'), `${file} links stylesheet`);
  assert(html.includes("<main"), `${file} includes main content`);
  assert(html.includes("privacy.html"), `${file} links privacy page`);
  assert(html.includes("terms.html"), `${file} links terms page`);
  assertNoPlaceholderText(file, html);
  checkLocalReferences(file, html);
}

function checkIndexPage() {
  const html = readSite("index.html");
  assert(html.includes('class="skip-link"'), "index.html includes skip link");
  assert(html.includes('id="runtime"'), "index.html includes runtime section");
  assert(html.includes('id="stack"'), "index.html includes stack section");
  assert(html.includes('id="beta"'), "index.html includes beta section");
  assert(html.includes("Full-stack, not fake-stack"), "index.html keeps full-stack positioning");
  assert(html.includes("Render Free"), "index.html names Render Free backend posture");
  assert(html.includes("Neon Free"), "index.html names Neon Free database posture");
  assert(html.includes("Cloudflare Pages"), "index.html names Cloudflare Pages static hosting posture");
  assert(html.includes("admin.html"), "index.html links the static admin dashboard");
  assert(html.includes('data-beta-api="/feedback/beta-interest"'), "index.html beta form targets backend beta-interest endpoint");
  assert(html.includes('type="email"'), "index.html beta form uses email input");
  assert(html.includes('role="status"'), "index.html beta form has accessible status region");
  assert(html.includes('aria-live="polite"'), "index.html beta form status is announced politely");
  assert(html.includes('alt="Preview of the ChatMod Mobile live moderation dashboard"'), "index.html hero visual has descriptive alt text");
}

function checkAdminPage() {
  const html = readSite("admin.html");

  assert(html.includes('data-admin-dashboard'), "admin.html declares the admin dashboard root");
  assert(html.includes('class="skip-link"'), "admin.html includes skip link for keyboard users");
  assert(html.includes('data-admin-origin'), "admin.html has a backend origin control");
  assert(html.includes('type="password"'), "admin.html hides the admin API key input");
  assert(html.includes('data-load-beta'), "admin.html includes a beta-interest shortcut");
  assert(html.includes('data-check-health'), "admin.html includes backend readiness check control");
  assert(html.includes('data-entitlement-form'), "admin.html includes manual entitlement form");
  assert(html.includes('data-ticket-form'), "admin.html includes support ticket metadata form");
  assert(html.includes('<label>'), "admin.html uses visible form labels");
  assert(html.includes('type="submit"'), "admin.html uses native submit buttons");
  assert(html.includes('role="status"'), "admin.html exposes an accessible status region");
  assert(html.includes('aria-live="polite"'), "admin.html status is announced politely");
  assert(html.includes('src="admin-dashboard.js"'), "admin.html loads the admin dashboard script");
  assert(html.includes("Cloudflare Pages Free-ready"), "admin.html preserves free static hosting posture");
  assert(html.includes("No checked-in admin key"), "admin.html documents runtime-only admin secrets");
}

function checkPolicyPages() {
  const privacy = readSite("privacy.html");
  const terms = readSite("terms.html");

  assert(privacy.includes("Beta-interest email"), "privacy.html discloses beta-interest collection");
  assert(privacy.includes("Google passwords"), "privacy.html states Google passwords are not collected");
  assert(privacy.includes("export account/log data"), "privacy.html names export controls");
  assert(terms.includes("Acceptable Use"), "terms.html includes acceptable use section");
  assert(terms.includes("Service Limits"), "terms.html includes service limits section");
  assert(terms.includes("YouTube's current policies"), "terms.html references YouTube policy compliance");
  assert(!privacy.includes("Launch policy draft"), "privacy.html does not present as a draft page");
  assert(!terms.includes("Launch policy draft"), "terms.html does not present as a draft page");
}

function checkScript() {
  const script = readSite("script.js");

  assert(script.includes("fetch(betaInterestEndpoint()"), "script.js submits beta interest with fetch");
  assert(script.includes("window.CHATMOD_BETA_API_URL"), "script.js supports deploy-time API URL override");
  assert(script.includes('credentials: "omit"'), "script.js does not send ambient credentials");
  assert(script.includes("Too many beta requests"), "script.js handles rate-limit copy");
  assert(script.includes("betaButton.disabled = true"), "script.js disables submit while saving");
  assertNoPlaceholderText("script.js", script);
}

function checkAdminScript() {
  const script = readSite("admin-dashboard.js");

  assert(script.includes("/admin/support/users"), "admin-dashboard.js loads support snapshots");
  assert(script.includes("/admin/support/devices/"), "admin-dashboard.js loads device and beta-interest snapshots");
  assert(script.includes("/admin/support/entitlements/manual-adjust"), "admin-dashboard.js saves manual entitlement adjustments");
  assert(script.includes("/admin/support/tickets/metadata"), "admin-dashboard.js saves support ticket metadata");
  assert(script.includes("/health/ready"), "admin-dashboard.js checks backend readiness");
  assert(script.includes('"x-admin-api-key"'), "admin-dashboard.js sends admin API key header");
  assert(script.includes('credentials: "omit"'), "admin-dashboard.js does not send ambient credentials");
  assert(script.includes("sessionStorage.setItem(\"chatmodAdminOrigin\""), "admin-dashboard.js remembers only the backend origin");
  assert(!script.includes("localStorage"), "admin-dashboard.js avoids durable browser storage for admin data");
  assert(!script.includes("sessionStorage.setItem(\"chatmodAdminKey\""), "admin-dashboard.js does not store the admin key");
  assert(script.includes("replaceChildren"), "admin-dashboard.js renders dynamic content without innerHTML");
  assertNoPlaceholderText("admin-dashboard.js", script);
}

function checkStyles() {
  const styles = readSite("styles.css");

  assert(styles.includes(":focus-visible"), "styles.css includes visible focus treatment");
  assert(styles.includes("@media (max-width: 980px)"), "styles.css includes tablet/mobile breakpoint");
  assert(styles.includes("@media (max-width: 560px)"), "styles.css includes small phone breakpoint");
  assert(styles.includes(".form-note[data-state=\"success\"]"), "styles.css styles success form state");
  assert(styles.includes(".form-note[data-state=\"error\"]"), "styles.css styles error form state");
  assert(styles.includes(".admin-shell"), "styles.css includes admin dashboard layout");
  assert(styles.includes(".dashboard-grid"), "styles.css includes admin dashboard grid");
  assert(styles.includes(".event-row"), "styles.css includes support event rows");
  assert(!/border-radius:\s*(1[6-9]|[2-9]\d)px/.test(styles), "styles.css avoids oversized rounded card corners");
}

function checkLocalReferences(file, html) {
  const refPattern = /\b(?:href|src)="([^"#?]+)(?:#[^"]*)?"/g;
  const baseDir = dirname(join(siteDir, file));
  const missing = [];
  let match;
  while ((match = refPattern.exec(html)) !== null) {
    const ref = match[1];
    if (/^(https?:|mailto:|tel:|#)/i.test(ref)) {
      continue;
    }
    const target = resolve(baseDir, ref);
    if (!target.startsWith(siteDir) || !existsSync(target)) {
      missing.push(ref);
    }
  }

  assert(missing.length === 0, `${file} local references exist${missing.length ? `: ${missing.join(", ")}` : ""}`);
}

function assertNoPlaceholderText(file, text) {
  const forbidden = [
    /static (?:beta form )?placeholder/i,
    /saved locally/i,
    /lorem ipsum/i,
    /\bTODO\b/i,
    /Launch policy draft/i
  ];

  for (const pattern of forbidden) {
    assert(!pattern.test(text), `${file} has no placeholder text matching ${pattern}`);
  }
}

function readSite(file) {
  return readFileSync(join(siteDir, file), "utf8");
}

function assert(condition, message) {
  if (condition) {
    passes.push(message);
  } else {
    failures.push(message);
  }
}

function printResults() {
  for (const message of passes) {
    console.log(`PASS ${message}`);
  }
  for (const message of failures) {
    console.error(`FAIL ${message}`);
  }

  console.log(`\nLaunch-site check: ${passes.length} passed, ${failures.length} failure(s).`);
  if (failures.length > 0) {
    process.exit(1);
  }
}
