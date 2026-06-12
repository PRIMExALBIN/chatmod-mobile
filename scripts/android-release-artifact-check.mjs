import { existsSync, readFileSync, statSync } from "node:fs";
import { delimiter, join, resolve } from "node:path";
import { spawnSync } from "node:child_process";

const root = resolve(process.cwd());
const defaultApk = join(root, "android", "app", "build", "outputs", "apk", "closedBeta", "release", "app-closedBeta-release.apk");
const defaultMetadata = join(root, "android", "app", "build", "outputs", "apk", "closedBeta", "release", "output-metadata.json");
const options = parseArgs(process.argv.slice(2));
const failures = [];
const warnings = [];
const passes = [];

if (options.help) {
  printHelp();
  process.exit(0);
}

checkSourceWiring();
checkArtifactIfPresent();
printResults();

function parseArgs(args) {
  const parsed = {
    apk: defaultApk,
    metadata: defaultMetadata,
    requireArtifact: false,
    help: false
  };

  for (const arg of args) {
    if (arg === "--help" || arg === "-h") {
      parsed.help = true;
    } else if (arg === "--require-artifact") {
      parsed.requireArtifact = true;
    } else if (arg.startsWith("--apk=")) {
      parsed.apk = resolve(root, arg.slice("--apk=".length));
    } else if (arg.startsWith("--metadata=")) {
      parsed.metadata = resolve(root, arg.slice("--metadata=".length));
    } else {
      fail(`Unknown option: ${arg}`);
    }
  }

  return parsed;
}

function checkSourceWiring() {
  const appBuild = readText("android/app/build.gradle.kts");
  const gitignore = readText(".gitignore");
  const releaseDocs = readText("docs/ANDROID_RELEASE_SIGNING.md");

  includesEvery(
    appBuild,
    [
      "verifyReleaseSigningConfigured",
      "CHATMOD_RELEASE_STORE_FILE",
      "CHATMOD_RELEASE_STORE_PASSWORD",
      "CHATMOD_RELEASE_KEY_ALIAS",
      "CHATMOD_RELEASE_KEY_PASSWORD",
      "signingConfigs",
      "externalRelease"
    ],
    "Android release signing source"
  );

  includesEvery(
    gitignore,
    [
      "*.jks",
      "*.keystore",
      "*.p12",
      "*.pfx",
      "release-evidence/"
    ],
    "Git ignore release secret guardrails"
  );

  includesEvery(
    releaseDocs,
    [
      "verifyReleaseSigningConfigured",
      "assembleClosedBetaRelease",
      "CHATMOD_RELEASE_STORE_FILE",
      "apksigner",
      "release-evidence/android-signing",
      "not the final Play upload key"
    ],
    "Android release signing docs"
  );
}

function checkArtifactIfPresent() {
  if (!existsSync(options.apk)) {
    const message = `Signed release APK not found at ${relativePath(options.apk)}`;
    if (options.requireArtifact) {
      fail(message);
    } else {
      warn(`${message}. Run with --require-artifact after building a signed release artifact.`);
    }
    return;
  }

  assert(statSync(options.apk).isFile(), "Signed release APK path is a file");
  assert(statSync(options.apk).size > 1_000_000, "Signed release APK is larger than 1 MB");

  if (!existsSync(options.metadata)) {
    fail(`Release output metadata not found at ${relativePath(options.metadata)}`);
    return;
  }

  const metadata = JSON.parse(readFileSync(options.metadata, "utf8"));
  assert(metadata.artifactType?.type === "APK", "Release metadata describes an APK artifact");
  assert(metadata.applicationId === "com.chatmod.mobile.beta", "Release metadata uses the closed beta application ID");
  assert(metadata.variantName === "closedBetaRelease", "Release metadata uses closedBetaRelease");
  assert(metadata.elements?.[0]?.outputFile === "app-closedBeta-release.apk", "Release metadata points at app-closedBeta-release.apk");

  const apksigner = findApksigner();
  if (!apksigner) {
    const message = "apksigner was not found. Install Android SDK build-tools or set ANDROID_HOME/ANDROID_SDK_ROOT.";
    if (options.requireArtifact) {
      fail(message);
    } else {
      warn(message);
    }
    return;
  }

  const result = runTool(apksigner, ["verify", "--verbose", "--print-certs", options.apk], { allowFailure: true });
  if (result.status !== 0) {
    fail(`apksigner verify failed: ${result.stderr || result.stdout}`);
    return;
  }

  includesEvery(
    result.stdout,
    [
      "Verifies",
      "Verified using v2 scheme (APK Signature Scheme v2): true",
      "Number of signers: 1",
      "Signer #1 key algorithm: RSA",
      "Signer #1 key size (bits): 4096"
    ],
    "APK signature verification"
  );
}

function findApksigner() {
  const names = process.platform === "win32" ? ["apksigner.bat", "apksigner"] : ["apksigner"];
  const sdkRoots = [
    process.env.ANDROID_HOME,
    process.env.ANDROID_SDK_ROOT,
    process.env.LOCALAPPDATA ? join(process.env.LOCALAPPDATA, "Android", "Sdk") : null,
    process.env.USERPROFILE ? join(process.env.USERPROFILE, ".cache", "chatmod-android-tools", "android-sdk") : null
  ].filter(Boolean);

  const candidates = [];
  for (const sdkRoot of sdkRoots) {
    for (const version of ["35.0.0", "34.0.0"]) {
      for (const name of names) {
        candidates.push(join(sdkRoot, "build-tools", version, name));
      }
    }
  }

  for (const pathEntry of (process.env.PATH ?? "").split(delimiter)) {
    if (!pathEntry) {
      continue;
    }
    for (const name of names) {
      candidates.push(join(pathEntry, name));
    }
  }

  return dedupe(candidates).find((candidate) => existsSync(candidate)) ?? null;
}

function runTool(command, args, settings = {}) {
  const javaHome = process.env.JAVA_HOME || (process.env.USERPROFILE ? join(process.env.USERPROFILE, ".cache", "chatmod-android-tools", "jdk") : "");
  const env = {
    ...process.env,
    JAVA_HOME: javaHome,
    PATH: javaHome ? `${join(javaHome, "bin")}${delimiter}${process.env.PATH ?? ""}` : process.env.PATH
  };
  const apksignerJar = command.toLowerCase().endsWith("apksigner.bat")
    ? join(command.slice(0, -"apksigner.bat".length), "lib", "apksigner.jar")
    : null;
  const spawnCommand = apksignerJar ? join(javaHome, "bin", process.platform === "win32" ? "java.exe" : "java") : command;
  const spawnArgs = apksignerJar ? ["-jar", apksignerJar, ...args] : args;
  const result = spawnSync(spawnCommand, spawnArgs, {
    cwd: root,
    encoding: "utf8",
    env,
    windowsHide: true
  });
  const output = {
    status: result.status ?? 1,
    stdout: result.stdout ?? "",
    stderr: result.stderr ?? ""
  };

  if (!settings.allowFailure && output.status !== 0) {
    fail(`${command} ${args.join(" ")} failed: ${output.stderr || output.stdout}`);
  }
  return output;
}

function includesEvery(text, phrases, label) {
  for (const phrase of phrases) {
    assert(text.includes(phrase), `${label} includes ${phrase}`);
  }
}

function readText(...parts) {
  return readFileSync(join(root, ...parts), "utf8");
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

function printHelp() {
  console.log(`Android release artifact checker

Usage:
  npm run android:release:check
  npm run android:release:check -- --require-artifact

Options:
  --require-artifact    Require the signed closed-beta release APK and verify it with apksigner.
  --apk=<path>          Override APK path. Defaults to android/app/build/outputs/apk/closedBeta/release/app-closedBeta-release.apk.
  --metadata=<path>     Override output metadata path.
  --help                Show this help.
`);
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

  console.log(`\nAndroid release artifact check: ${passes.length} passed, ${warnings.length} warning(s), ${failures.length} failure(s).`);
  if (failures.length > 0) {
    process.exit(1);
  }
}

function relativePath(path) {
  return path.startsWith(root) ? path.slice(root.length + 1).replaceAll("\\", "/") : path;
}

function dedupe(values) {
  return [...new Set(values)];
}
