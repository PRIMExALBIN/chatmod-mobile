import { existsSync, mkdirSync, statSync, writeFileSync } from "node:fs";
import { basename, delimiter, join, resolve } from "node:path";
import { spawnSync } from "node:child_process";

const root = resolve(process.cwd());
const defaultApk = join(root, "android", "app", "build", "outputs", "apk", "closedBeta", "debug", "app-closedBeta-debug.apk");
const defaultPackageName = "com.chatmod.mobile.beta";
const defaultEvidenceDir = join(root, "android", "app", "build", "qa-evidence");

const options = parseArgs(process.argv.slice(2));
const passes = [];
const warnings = [];
const failures = [];

const manualPlan = [
  "Install and first launch",
  "Capture screenshot and logcat evidence for the run",
  "Room and DataStore migration smoke after upgrade/install",
  "Command and timer editor sync against a real backend session",
  "Account tab privacy controls: export, YouTube disconnect, backup delete, account delete copy",
  "Settings backup export/restore and local data wipe",
  "Support diagnostics, beta feedback, and recent API errors",
  "Settings DataStore toggles: emergency mode, link lockdown, reduced motion, high contrast, analytics",
  "Logs tab Room feed and local analytics cards",
  "HTTP retry/backoff while backend is slow, offline, or recovers",
  "Koin graph paths: Activity, foreground service, Quick Settings tile, WorkManager pending-sync drain",
  "TalkBack through live controls, queue actions, account/privacy, support, settings, and logs",
  "Text scaling at 130 percent, 160 percent, and 200 percent on a small phone",
  "Dark mode, high contrast, and non-color-only status cues",
  "One-handed live-control reach while queue is active",
  "Foreground service while app is backgrounded and screen is locked during a test stream",
  "Battery optimization warning on a non-exempt device",
  "Poor network and airplane-mode recovery",
  "Small phone, large phone, tablet/foldable, and orientation behavior"
];

if (options.help) {
  printHelp();
  process.exit(0);
}

if (options.printPlan) {
  printManualPlan();
  console.log("\nAndroid device QA plan check: print-only mode passed.");
  process.exit(0);
}

runDeviceQa();
printResults();

function parseArgs(args) {
  const parsed = {
    apk: defaultApk,
    packageName: defaultPackageName,
    evidenceDir: defaultEvidenceDir,
    device: null,
    install: false,
    launch: false,
    screenshot: false,
    logcat: false,
    clearLogcat: false,
    printPlan: false,
    help: false
  };

  for (const arg of args) {
    if (arg === "--help" || arg === "-h") {
      parsed.help = true;
    } else if (arg === "--print-plan") {
      parsed.printPlan = true;
    } else if (arg === "--install") {
      parsed.install = true;
    } else if (arg === "--launch") {
      parsed.launch = true;
    } else if (arg === "--screenshot") {
      parsed.screenshot = true;
    } else if (arg === "--logcat") {
      parsed.logcat = true;
    } else if (arg === "--clear-logcat") {
      parsed.clearLogcat = true;
    } else if (arg === "--capture-evidence") {
      parsed.screenshot = true;
      parsed.logcat = true;
    } else if (arg.startsWith("--apk=")) {
      parsed.apk = resolve(root, arg.slice("--apk=".length));
    } else if (arg.startsWith("--package=")) {
      parsed.packageName = arg.slice("--package=".length);
    } else if (arg.startsWith("--evidence-dir=")) {
      parsed.evidenceDir = resolve(root, arg.slice("--evidence-dir=".length));
    } else if (arg.startsWith("--device=")) {
      parsed.device = arg.slice("--device=".length);
    } else {
      fail(`Unknown option: ${arg}`);
    }
  }

  return parsed;
}

function runDeviceQa() {
  const adb = findAdb();
  if (!adb) {
    fail("ADB was not found. Install Android SDK platform-tools or set ANDROID_HOME/ANDROID_SDK_ROOT.");
    return;
  }

  pass(`ADB found at ${adb}`);
  const version = runTool(adb, ["version"], { allowFailure: true });
  if (version.status === 0) {
    const firstLine = version.stdout.split(/\r?\n/).find(Boolean);
    pass(firstLine ? `ADB responds: ${firstLine}` : "ADB responds to version check");
  } else {
    fail(`ADB version check failed: ${version.stderr || version.stdout}`);
    return;
  }

  if (!existsSync(options.apk)) {
    fail(`APK not found at ${options.apk}`);
    warn("Build it first: cd android; .\\gradlew.bat testInternalDebugUnitTest assembleClosedBetaDebug -PchatmodUseDemoApi=true --no-daemon");
    return;
  }

  if (!statSync(options.apk).isFile()) {
    fail(`APK path is not a file: ${options.apk}`);
    return;
  }
  pass(`APK exists: ${options.apk}`);

  const devices = listDevices(adb);
  if (devices.length === 0) {
    fail("No online Android device or emulator found. Connect a phone with USB debugging or start an emulator.");
    return;
  }

  const selected = selectDevice(devices);
  if (!selected) {
    return;
  }
  pass(`Using device ${selected.serial}`);

  const model = adbShell(adb, selected.serial, ["getprop", "ro.product.model"], { allowFailure: true });
  const androidVersion = adbShell(adb, selected.serial, ["getprop", "ro.build.version.release"], { allowFailure: true });
  if (model.status === 0) {
    pass(`Device model: ${model.stdout.trim() || "unknown"}`);
  }
  if (androidVersion.status === 0) {
    pass(`Android version: ${androidVersion.stdout.trim() || "unknown"}`);
  }

  if (options.install) {
    const install = runAdb(adb, selected.serial, ["install", "-r", options.apk], { allowFailure: true });
    if (install.status === 0) {
      pass(`Installed ${basename(options.apk)} onto ${selected.serial}`);
    } else {
      fail(`ADB install failed: ${install.stderr || install.stdout}`);
      return;
    }
  } else {
    warn("Install step skipped. Re-run with --install to install the APK before device QA.");
  }

  const packagePath = adbShell(adb, selected.serial, ["pm", "path", options.packageName], { allowFailure: true });
  if (packagePath.status === 0 && packagePath.stdout.trim().startsWith("package:")) {
    pass(`Package is installed: ${options.packageName}`);
  } else {
    fail(`Package ${options.packageName} is not installed on ${selected.serial}. Re-run with --install or install from Play closed testing.`);
    return;
  }

  if (options.launch) {
    if (options.clearLogcat) {
      const clear = runAdb(adb, selected.serial, ["logcat", "-c"], { allowFailure: true });
      if (clear.status === 0) {
        pass("Cleared logcat before launching the app");
      } else {
        warn(`Could not clear logcat before launch: ${clear.stderr || clear.stdout}`);
      }
    }

    const launch = runAdb(adb, selected.serial, [
      "shell",
      "monkey",
      "-p",
      options.packageName,
      "-c",
      "android.intent.category.LAUNCHER",
      "1"
    ], { allowFailure: true });
    if (launch.status === 0) {
      pass("Launcher activity opened through monkey");
    } else {
      fail(`Launch failed: ${launch.stderr || launch.stdout}`);
    }
  } else {
    warn("Launch step skipped. Re-run with --launch to open the app after install.");
  }

  const notificationPermission = adbShell(adb, selected.serial, ["dumpsys", "package", options.packageName], { allowFailure: true });
  if (notificationPermission.status === 0 && notificationPermission.stdout.includes("android.permission.POST_NOTIFICATIONS")) {
    pass("Manifest/package dump includes POST_NOTIFICATIONS permission state");
  } else {
    warn("Could not confirm notification permission state from dumpsys package output.");
  }

  if (options.screenshot || options.logcat) {
    captureEvidence(adb, selected.serial);
  } else {
    warn("Evidence capture skipped. Re-run with --capture-evidence, or use --screenshot and --logcat.");
  }

  printManualPlan();
  warn("Manual evidence still required. Record device, build, backend URL, stream URL, and result notes in docs/PRODUCTION_RELEASE_CHECKLIST.md.");
}

function captureEvidence(adb, serial) {
  const timestamp = new Date().toISOString().replace(/[:.]/g, "-");
  const deviceDir = join(options.evidenceDir, `${timestamp}-${sanitizeFilePart(serial)}`);
  mkdirSync(deviceDir, { recursive: true });
  pass(`Evidence directory prepared: ${deviceDir}`);

  if (options.screenshot) {
    const screenshot = runBinaryTool(adb, ["-s", serial, "exec-out", "screencap", "-p"]);
    if (screenshot.status === 0 && screenshot.stdout.length > 1_000) {
      const screenshotPath = join(deviceDir, "launch-screen.png");
      writeFileSync(screenshotPath, screenshot.stdout);
      pass(`Screenshot captured: ${screenshotPath}`);
    } else {
      warn(`Screenshot capture failed or returned an empty image: ${screenshot.stderr.toString("utf8")}`);
    }
  }

  if (options.logcat) {
    const logcat = runTool(adb, ["-s", serial, "logcat", "-d", "-t", "800"], { allowFailure: true });
    if (logcat.status === 0 && logcat.stdout.trim().length > 0) {
      const logcatPath = join(deviceDir, "logcat.txt");
      writeFileSync(logcatPath, logcat.stdout, "utf8");
      pass(`Logcat captured: ${logcatPath}`);
    } else {
      warn(`Logcat capture failed or returned no output: ${logcat.stderr || logcat.stdout}`);
    }
  }
}

function findAdb() {
  const candidates = [];
  const names = process.platform === "win32" ? ["adb.exe", "adb"] : ["adb"];
  const sdkRoots = [
    process.env.ANDROID_HOME,
    process.env.ANDROID_SDK_ROOT,
    process.env.LOCALAPPDATA ? join(process.env.LOCALAPPDATA, "Android", "Sdk") : null,
    process.env.USERPROFILE ? join(process.env.USERPROFILE, ".cache", "chatmod-android-tools", "android-sdk") : null
  ].filter(Boolean);

  for (const sdkRoot of sdkRoots) {
    for (const name of names) {
      candidates.push(join(sdkRoot, "platform-tools", name));
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

  for (const candidate of dedupe(candidates)) {
    if (!existsSync(candidate)) {
      continue;
    }
    const result = runTool(candidate, ["version"], { allowFailure: true });
    if (result.status === 0) {
      return candidate;
    }
  }

  return null;
}

function listDevices(adb) {
  const result = runTool(adb, ["devices"], { allowFailure: true });
  if (result.status !== 0) {
    fail(`adb devices failed: ${result.stderr || result.stdout}`);
    return [];
  }

  const devices = [];
  for (const line of result.stdout.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("List of devices")) {
      continue;
    }
    const [serial, state] = trimmed.split(/\s+/);
    if (state === "device") {
      devices.push({ serial, state });
    } else if (serial && state) {
      warn(`Ignoring ${serial} because adb state is ${state}`);
    }
  }
  return devices;
}

function selectDevice(devices) {
  if (options.device) {
    const selected = devices.find((device) => device.serial === options.device);
    if (!selected) {
      fail(`Requested device ${options.device} is not online. Online devices: ${devices.map((device) => device.serial).join(", ")}`);
      return null;
    }
    return selected;
  }

  if (devices.length > 1) {
    fail(`Multiple online devices found: ${devices.map((device) => device.serial).join(", ")}. Re-run with --device=<serial>.`);
    return null;
  }

  return devices[0];
}

function adbShell(adb, serial, args, settings = {}) {
  return runAdb(adb, serial, ["shell", ...args], settings);
}

function runAdb(adb, serial, args, settings = {}) {
  return runTool(adb, ["-s", serial, ...args], settings);
}

function runTool(command, args, settings = {}) {
  const result = spawnSync(command, args, {
    cwd: root,
    encoding: "utf8",
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

function runBinaryTool(command, args) {
  const result = spawnSync(command, args, {
    cwd: root,
    encoding: null,
    windowsHide: true
  });
  return {
    status: result.status ?? 1,
    stdout: result.stdout ?? Buffer.alloc(0),
    stderr: result.stderr ?? Buffer.alloc(0)
  };
}

function printManualPlan() {
  console.log("\nAndroid Device QA Manual Plan");
  console.log("--------------------------------");
  for (const [index, item] of manualPlan.entries()) {
    console.log(`${index + 1}. ${item}`);
  }
  console.log("\nRecommended real-device command:");
  console.log("npm run android:device:qa -- --install --launch --capture-evidence --clear-logcat");
  console.log("\nFor multiple devices:");
  console.log("npm run android:device:qa -- --device=<adb-serial> --install --launch --capture-evidence --clear-logcat");
}

function printHelp() {
  console.log(`Android device QA runner

Usage:
  npm run android:device:qa -- --print-plan
  npm run android:device:qa -- --install --launch

Options:
  --print-plan          Print the manual QA plan without requiring ADB, an APK, or a device.
  --install             Install the APK with adb install -r before checks.
  --launch              Launch the app with adb monkey after install/package verification.
  --capture-evidence    Capture both screenshot and logcat evidence.
  --screenshot          Capture a PNG screenshot with adb exec-out screencap -p.
  --logcat              Capture recent logcat output.
  --clear-logcat        Clear logcat before launching the app.
  --evidence-dir=<path> Write evidence under this directory. Defaults to android/app/build/qa-evidence.
  --device=<serial>     Select an adb serial when multiple devices are online.
  --apk=<path>          Override the APK path. Defaults to the closed-beta debug APK.
  --package=<name>      Override the package name. Defaults to com.chatmod.mobile.beta.
  --help                Show this help.
`);
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

  console.log(`\nAndroid device QA runner: ${passes.length} passed, ${warnings.length} warning(s), ${failures.length} failure(s).`);
  if (failures.length > 0) {
    process.exit(1);
  }
}

function dedupe(values) {
  return [...new Set(values)];
}

function sanitizeFilePart(value) {
  return value.replace(/[^a-z0-9_.-]/gi, "_");
}
