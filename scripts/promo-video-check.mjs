import { existsSync, readFileSync, statSync } from "node:fs";
import { join, resolve } from "node:path";
import { spawnSync } from "node:child_process";

const root = resolve(process.cwd());
const promoRoot = join(root, "chatmod-mobile-promo");
const failures = [];
const warnings = [];
const passes = [];

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

function filePath(...parts) {
  return join(root, ...parts);
}

function promoPath(...parts) {
  return join(promoRoot, ...parts);
}

function readText(...parts) {
  return readFileSync(filePath(...parts), "utf8");
}

function readPromoText(...parts) {
  return readFileSync(promoPath(...parts), "utf8");
}

function readJson(...parts) {
  return JSON.parse(readText(...parts));
}

function readPromoJson(...parts) {
  return JSON.parse(readPromoText(...parts));
}

function assertPromoFile(parts, label) {
  assert(existsSync(promoPath(...parts)), `${label} exists`);
}

function includesEvery(text, phrases, label) {
  for (const phrase of phrases) {
    assert(text.includes(phrase), `${label} includes ${phrase}`);
  }
}

function checkRootWiring() {
  const packageJson = readJson("package.json");
  const ci = readText(".github/workflows/ci.yml");

  assert(packageJson.scripts?.["promo-video:check"] === "node scripts/promo-video-check.mjs", "Root package exposes npm run promo-video:check");
  assert(ci.includes("npm run promo-video:check"), "CI runs the promo video source check");
}

function checkPromoProject() {
  const packageJson = readPromoJson("package.json");
  const packageLock = readPromoText("package-lock.json");
  const index = readPromoText("index.html");
  const design = readPromoText("DESIGN.md");
  const script = readPromoText("SCRIPT.md");
  const storyboard = readPromoText("STORYBOARD.md");
  const narration = readPromoText("narration.txt");

  const requiredFiles = [
    [["index.html"], "HyperFrames composition"],
    [["DESIGN.md"], "Promo design direction"],
    [["SCRIPT.md"], "Promo script"],
    [["STORYBOARD.md"], "Promo storyboard"],
    [["narration.txt"], "Promo narration"],
    [["hyperframes.json"], "HyperFrames config"],
    [["meta.json"], "HyperFrames metadata"],
    [["assets"], "Promo assets directory"],
    [["renders"], "Promo render directory"],
    [["renders", "chatmod-mobile-tutorial.mp4"], "Tutorial MP4"],
    [["renders", "chatmod-mobile-tutorial-midpoint.jpg"], "Tutorial thumbnail"]
  ];
  for (const [parts, label] of requiredFiles) {
    assertPromoFile(parts, label);
  }

  assert(packageJson.scripts?.check?.includes("hyperframes@0.6.80"), "Promo package pins HyperFrames check command");
  assert(packageJson.scripts?.render?.includes("hyperframes@0.6.80 render"), "Promo package pins HyperFrames render command");
  assert(packageJson.devDependencies?.["ffmpeg-static"], "Promo package includes ffmpeg-static for local free rendering");
  assert(packageJson.devDependencies?.["ffprobe-static"], "Promo package includes ffprobe-static for local media checks");
  assert(packageLock.includes("ffmpeg-static"), "Promo lockfile pins ffmpeg-static");
  assert(packageLock.includes("ffprobe-static"), "Promo lockfile pins ffprobe-static");

  includesEvery(
    index,
    [
      'data-composition-id="main"',
      'data-duration="20"',
      "ChatMod Mobile",
      "YouTube Live ops from your phone",
      "Run live moderation from the phone in your hand.",
      "Your channel. Your bot. Live chat handled.",
      'window.__timelines["main"] = tl'
    ],
    "Promo composition"
  );

  includesEvery(
    design,
    [
      "a crisp live operations control room in your pocket",
      "phone UI is the product signal",
      "Do not imply production YouTube OAuth is already complete"
    ],
    "Promo design brief"
  );

  includesEvery(
    script,
    [
      "desktop babysitter",
      "runs your YouTube Live moderation bot from the phone in your hand",
      "commands and timers",
      "Your channel. Your bot. Live chat handled."
    ],
    "Promo script"
  );

  includesEvery(
    narration,
    [
      "desktop babysitter",
      "phone in your hand",
      "logs, backups, and support diagnostics",
      "Your channel. Your bot. Live chat handled."
    ],
    "Promo narration"
  );

  for (const beat of ["Beat 1", "Beat 2", "Beat 3", "Beat 4", "Beat 5"]) {
    assert(storyboard.includes(beat), `Storyboard includes ${beat}`);
  }
}

function checkRenderedArtifacts() {
  const mp4Path = promoPath("renders", "chatmod-mobile-tutorial.mp4");
  const thumbnailPath = promoPath("renders", "chatmod-mobile-tutorial-midpoint.jpg");

  const mp4 = statSync(mp4Path);
  const thumbnail = statSync(thumbnailPath);
  assert(mp4.size > 1_000_000, "Tutorial MP4 is larger than 1 MB");
  assert(mp4.size < 100_000_000, "Tutorial MP4 stays lightweight enough for repo handoff");
  assert(thumbnail.size > 10_000, "Tutorial thumbnail is non-empty");

  const ffprobeCandidates = [
    promoPath("node_modules", "ffprobe-static", "bin", "win32", "x64", "ffprobe.exe"),
    promoPath("node_modules", "ffprobe-static", "bin", "linux", "x64", "ffprobe"),
    promoPath("node_modules", "ffprobe-static", "bin", "darwin", "x64", "ffprobe"),
    promoPath("node_modules", "ffprobe-static", "bin", "darwin", "arm64", "ffprobe")
  ];
  const ffprobe = ffprobeCandidates.find((candidate) => existsSync(candidate));
  if (!ffprobe) {
    warn("ffprobe-static is not installed locally; duration check skipped after artifact size checks.");
    return;
  }

  const probe = spawnSync(
    ffprobe,
    ["-v", "error", "-show_entries", "format=duration,size", "-of", "json", mp4Path],
    { encoding: "utf8" }
  );
  if (probe.status !== 0) {
    fail(`ffprobe failed for tutorial MP4: ${probe.stderr.trim() || probe.stdout.trim()}`);
    return;
  }

  const metadata = JSON.parse(probe.stdout);
  const duration = Number(metadata.format?.duration);
  const size = Number(metadata.format?.size);
  assert(duration >= 19.5 && duration <= 20.5, "Tutorial MP4 duration is approximately 20 seconds");
  assert(size === mp4.size, "ffprobe MP4 size matches filesystem size");
}

function checkDocs() {
  const docs = readText("docs/TUTORIAL_VIDEO.md");
  const release = readText("docs/PRODUCTION_RELEASE_CHECKLIST.md");
  const checklist = readText("BUILD_CHECKLIST.md");
  const readme = readText("README.md");

  includesEvery(
    docs,
    [
      "chatmod-mobile-promo/renders/chatmod-mobile-tutorial.mp4",
      "HyperFrames",
      "ffmpeg-static",
      "ffprobe-static",
      "not the OAuth review demo video"
    ],
    "Tutorial video docs"
  );
  assert(release.includes("Tutorial video source/render artifact is prepared"), "Release checklist captures tutorial video source gate");
  assert(checklist.includes("- [x] Tutorial video"), "Build checklist marks Tutorial video complete");
  assert(readme.includes("chatmod-mobile-promo/"), "README lists the promo video project");
  assert(readme.includes("docs/TUTORIAL_VIDEO.md"), "README links the tutorial video docs");
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

  console.log(`\nPromo video source check: ${passes.length} passed, ${warnings.length} warning(s), ${failures.length} failure(s).`);
  if (failures.length > 0) {
    process.exit(1);
  }
}

checkRootWiring();
checkPromoProject();
checkRenderedArtifacts();
checkDocs();
printResults();
