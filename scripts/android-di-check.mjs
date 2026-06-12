import { existsSync, readFileSync } from "node:fs";
import { join, resolve } from "node:path";

const root = resolve(process.cwd());
const passes = [];
const failures = [];

function pass(message) {
  passes.push(message);
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

function pathFor(path) {
  return join(root, path);
}

function fileExists(path) {
  return existsSync(pathFor(path));
}

function readText(path) {
  return readFileSync(pathFor(path), "utf8");
}

function includesEvery(text, phrases, label) {
  for (const phrase of phrases) {
    assert(text.includes(phrase), `${label} includes ${phrase}`);
  }
}

function checkRequiredFiles() {
  const requiredFiles = [
    "android/app/build.gradle.kts",
    "android/app/src/main/java/com/chatmod/mobile/ChatModApplication.kt",
    "android/app/src/main/java/com/chatmod/mobile/MainActivity.kt",
    "android/app/src/main/java/com/chatmod/mobile/billing/PlayBillingManager.kt",
    "android/app/src/main/java/com/chatmod/mobile/di/ChatModModules.kt",
    "docs/ANDROID_DEPENDENCY_INJECTION.md",
    "BUILD_CHECKLIST.md",
    ".github/workflows/ci.yml"
  ];

  for (const file of requiredFiles) {
    assert(fileExists(file), `${file} exists`);
  }
}

function checkGradleDependencies() {
  const gradle = readText("android/app/build.gradle.kts");
  includesEvery(
    gradle,
    [
      'implementation(platform("io.insert-koin:koin-bom:4.0.4"))',
      'implementation("io.insert-koin:koin-android")'
    ],
    "Android Gradle dependencies"
  );
}

function checkKoinModule() {
  const moduleSource = readText("android/app/src/main/java/com/chatmod/mobile/di/ChatModModules.kt");
  includesEvery(
    moduleSource,
    [
      "package com.chatmod.mobile.di",
      "val ApplicationScopeQualifier = named(\"applicationScope\")",
      "val ChatModCoreModule = module",
      "single<CoroutineScope>(ApplicationScopeQualifier)",
      "single { ChatModDatabase.getInstance(androidContext()) }",
      "single<ChatModApiClient>",
      "DemoChatModApiClient()",
      "HttpChatModApiClient(BuildConfig.CHATMOD_API_BASE_URL)",
      "single { ChatModRepository(get()) }",
      "single { ChatModSessionManager(androidContext(), get()) }",
      "single { SettingsStore(androidContext()) }",
      "single { PlayBillingManager(androidContext()) }",
      "LocalPrivacyStore(",
      "single<DashboardCommandTimerStore>",
      "SyncingDashboardCommandTimerStore(",
      "PendingCloudSyncQueue(",
      "single<BotLogSink>",
      "SyncingLogRepository(",
      "CrashReporter(",
      "UsageAnalyticsReporter("
    ],
    "Koin Android module"
  );
}

function checkApplicationAndActivity() {
  const application = readText("android/app/src/main/java/com/chatmod/mobile/ChatModApplication.kt");
  const activity = readText("android/app/src/main/java/com/chatmod/mobile/MainActivity.kt");

  includesEvery(
    application,
    [
      "KoinComponent",
      "startKoin",
      "androidContext(this@ChatModApplication)",
      "modules(ChatModCoreModule)",
      "val apiClient: ChatModApiClient get() = get()",
      "val dashboardCommandTimerStore: DashboardCommandTimerStore get() = get()",
      "val botLogRepository: BotLogSink get() = get()"
    ],
    "ChatModApplication DI bridge"
  );

  includesEvery(
    activity,
    [
      "import org.koin.android.ext.android.inject",
      "private val dashboardCommandTimerStore: DashboardCommandTimerStore by inject()",
      "private val apiClient: ChatModApiClient by inject()",
      "private val sessionManager: ChatModSessionManager by inject()",
      "private val analyticsReporter: UsageAnalyticsReporter by inject()",
      "private val playBillingManager: PlayBillingManager by inject()"
    ],
    "MainActivity Koin consumers"
  );

  assert(!activity.includes("application as ChatModApplication"), "MainActivity no longer reaches into ChatModApplication for dependencies");
}

function checkDocumentationAndCi() {
  const docs = readText("docs/ANDROID_DEPENDENCY_INJECTION.md");
  const packageJson = JSON.parse(readText("package.json"));
  const ci = readText(".github/workflows/ci.yml");
  const production = readText("scripts/production-readiness.mjs");
  const checklist = readText("BUILD_CHECKLIST.md");

  includesEvery(
    docs,
    [
      "Koin",
      "Apache 2.0",
      "npm run android:di:check",
      "ChatModCoreModule",
      "MainActivity",
      "Manual QA Still Required"
    ],
    "Android DI docs"
  );

  assert(Boolean(packageJson.scripts?.["android:di:check"]), "package.json exposes npm run android:di:check");
  assert(packageJson.scripts?.["android:di:check"] === "node scripts/android-di-check.mjs", "android:di:check runs the Android DI checker");
  assert(ci.includes("npm run android:di:check"), "CI runs the Android DI checker");
  assert(production.includes("scripts/android-di-check.mjs"), "Production readiness requires the Android DI checker source");
  assert(production.includes("docs/ANDROID_DEPENDENCY_INJECTION.md"), "Production readiness requires the Android DI doc");
  assert(checklist.includes("Koin dependency injection source wiring"), "Build checklist tracks Koin dependency injection source wiring");
}

function printResults() {
  for (const message of passes) {
    console.log(`PASS ${message}`);
  }
  for (const message of failures) {
    console.error(`FAIL ${message}`);
  }

  console.log(`\nAndroid dependency injection source check: ${passes.length} passed, ${failures.length} failure(s).`);
  if (failures.length > 0) {
    process.exit(1);
  }
}

checkRequiredFiles();
checkGradleDependencies();
checkKoinModule();
checkApplicationAndActivity();
checkDocumentationAndCi();
printResults();
