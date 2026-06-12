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

function countMatches(text, pattern) {
  return [...text.matchAll(pattern)].length;
}

function includesEvery(text, phrases, label) {
  for (const phrase of phrases) {
    assert(text.includes(phrase), `${label} includes ${phrase}`);
  }
}

function checkRequiredFiles() {
  const requiredFiles = [
    "android/app/src/main/java/com/chatmod/mobile/ui/dashboard/DashboardScreen.kt",
    "android/app/src/main/java/com/chatmod/mobile/ui/dashboard/DashboardUiState.kt",
    "android/app/src/main/java/com/chatmod/mobile/ui/theme/ChatModTheme.kt",
    "docs/ANDROID_UX_ACCESSIBILITY.md",
    "BUILD_CHECKLIST.md",
    ".github/workflows/ci.yml"
  ];

  for (const file of requiredFiles) {
    assert(fileExists(file), `${file} exists`);
  }
}

function checkDashboardAccessibility() {
  const dashboard = readText("android/app/src/main/java/com/chatmod/mobile/ui/dashboard/DashboardScreen.kt");
  const uiState = readText("android/app/src/main/java/com/chatmod/mobile/ui/dashboard/DashboardUiState.kt");

  includesEvery(
    dashboard,
    [
      "import androidx.compose.ui.semantics.contentDescription",
      "import androidx.compose.ui.semantics.semantics",
      "import androidx.compose.ui.semantics.stateDescription",
      "import androidx.compose.material3.Tab",
      "import androidx.compose.material3.TabRow",
      "Modifier.minimumTouchTarget()",
      "sizeIn(minWidth = 48.dp, minHeight = 48.dp)",
      "stateDescription = liveStatusStateDescription(state)",
      "LiveWorkspacePanel(",
      "LiveWorkspacePage.Controls",
      "TabRow(",
      "selectedPage.label",
      "InlineLiveControlsPanel(",
      "LiveControlContent(",
      "FeatureRow(\"Local history\", \"${billing.localHistoryLimit} rows\")",
      "FeatureRow(\"Preset bundles\", if (billing.presetBundles) \"On\" else \"Off\")",
      "label = \"${logs.localHistoryLimit} cap\"",
      "label = \"${history.localHistoryLimit} cap\"",
      "RulePresetImportDialog(",
      "ChannelProfilePanel(",
      "Text(\"Channel profiles\",",
      "\"Create profile\"",
      "Text(\"Export JSON\")",
      "Text(\"Import JSON\")",
      "ReadOnlyStatusChip(",
      "contentDescription = \"Sync status ${syncStatusLabel(state.syncStatus)}\"",
      "contentDescription = \"Bot health ${botHealthLabel(state)}\"",
      "contentDescription = \"YouTube API status ${apiWarningLabel(state)}\"",
      "SyncStatus.Offline -> \"Network offline\"",
      "SyncStatus.Reconnecting -> \"Reconnecting\"",
      "Icons.Default.Warning",
      "Icons.Default.CheckCircle",
      "HapticFeedbackType.LongPress",
      "ModerationConfirmationDialog(",
      "AccountConfirmationDialog("
    ],
    "Dashboard accessibility source"
  );

  assert(countMatches(dashboard, /contentDescription\s*=/g) >= 35, "Dashboard has broad screen-reader content descriptions");
  assert(countMatches(dashboard, /stateDescription\s*=/g) >= 3, "Dashboard has state descriptions for status surfaces");
  assert(countMatches(dashboard, /\.minimumTouchTarget\(\)/g) >= 12, "Dashboard applies explicit 48dp minimum touch targets to dense controls");
  assert(countMatches(dashboard, /LiveWorkspacePage\./g) >= 8, "Dashboard exposes a dedicated queue/feed/controls live workspace");
  assert(!/fontSize\s*=/.test(dashboard), "Dashboard avoids fixed fontSize overrides so Material typography can scale");
  assert(!/\.clickable\s*\{/.test(dashboard), "Dashboard avoids bare clickable modifiers without semantics");

  includesEvery(
    uiState,
    [
      "val reducedMotion: Boolean = false",
      "val highContrast: Boolean = false"
    ],
    "Dashboard settings state"
  );
}

function checkDashboardStateCoverage() {
  const dashboard = readText("android/app/src/main/java/com/chatmod/mobile/ui/dashboard/DashboardScreen.kt");
  const uiState = readText("android/app/src/main/java/com/chatmod/mobile/ui/dashboard/DashboardUiState.kt");
  const viewModel = readText("android/app/src/main/java/com/chatmod/mobile/ui/dashboard/DashboardViewModel.kt");

  includesEvery(
    uiState,
    [
      "enum class SyncStatus",
      "Ready,",
      "Syncing,",
      "Reconnecting,",
      "Offline,",
      "Failed",
      "val isLoading: Boolean = false",
      "val statusMessage: String? = null",
      "val errorMessage: String? = null"
    ],
    "Dashboard UI state model"
  );

  includesEvery(
    dashboard,
    [
      "Text(if (selector.isLoading) \"Loading\" else \"Refresh\")",
      "Text(if (history.isLoadingWarnings) \"Loading\" else \"Refresh\")",
      "label = \"Loading\"",
      "streamSelectorEmptyMessage(selector)",
      "\"No active or scheduled streams found for this channel.\"",
      "\"No local log entries yet.\"",
      "\"No backend API errors recorded for this device.\"",
      "\"YouTube API failed. Try again shortly.\"",
      "\"Network is offline. Stream detection will work again when connected.\"",
      "\"Backend is reconnecting. Try refreshing in a moment.\"",
      "\"Live chat ready\"",
      "\"Ready for live moderation\"",
      "\"Room log store is connected\"",
      "\"Loading trends...\"",
      "\"Sync stream sessions to unlock account-wide trends.\""
    ],
    "Dashboard rendered state coverage"
  );

  includesEvery(
    viewModel,
    [
      "syncStatus = SyncStatus.Syncing",
      "syncStatus = SyncStatus.Ready",
      "syncStatus = SyncStatus.Offline",
      "_state.update { current -> current.copy(syncStatus = SyncStatus.Reconnecting) }",
      "syncStatus = SyncStatus.Failed",
      "isLoading = true",
      "isLoading = false",
      "statusMessage = \"Created ${createdSummary.name}\"",
      "statusMessage = \"Custom preset saved\"",
      "statusMessage = \"Account export ready\"",
      "statusMessage = \"Local data wiped\""
    ],
    "Dashboard ViewModel state transitions"
  );

  assert(countMatches(dashboard, /if \([^)]+\.isEmpty\(\)\)/g) >= 10, "Dashboard renders broad empty states across panels");
  assert(countMatches(viewModel, /syncStatus = SyncStatus\.(Ready|Syncing|Offline|Reconnecting|Failed)/g) >= 20, "ViewModel drives explicit sync status transitions");
  assert(countMatches(viewModel, /statusMessage = "(Could not|.*failed|.*unavailable|.*needs backend sync)/gi) >= 10, "ViewModel surfaces error and recovery status messages");
  assert(countMatches(viewModel, /statusMessage = ".*(saved|ready|loaded|created|selected|wiped|restored|deleted|enabled|disabled)/gi) >= 10, "ViewModel surfaces success status messages");
}

function checkThemeAccessibility() {
  const theme = readText("android/app/src/main/java/com/chatmod/mobile/ui/theme/ChatModTheme.kt");

  includesEvery(
    theme,
    [
      "HighContrastLightColors",
      "HighContrastDarkColors",
      "dynamicDarkColorScheme(context)",
      "dynamicLightColorScheme(context)",
      "highContrast: Boolean = false",
      "if (highContrast)"
    ],
    "Theme accessibility source"
  );

  assert(!/Color\(0x/i.test(theme), "Theme uses readable RGB channel colors instead of opaque one-off hex literals");
  assert(countMatches(theme, /on[A-Z][A-Za-z]+\s*=/g) >= 12, "Theme declares paired on-colors for contrast");
}

function checkDocumentationAndCi() {
  const docs = readText("docs/ANDROID_UX_ACCESSIBILITY.md");
  const packageJson = JSON.parse(readText("package.json"));
  const ci = readText(".github/workflows/ci.yml");
  const production = readText("scripts/production-readiness.mjs");
  const checklist = readText("BUILD_CHECKLIST.md");

  includesEvery(
    docs,
    [
      "npm run android:ux:check",
      "Screen reader",
      "48dp",
      "High contrast",
      "Text scaling",
      "loading, empty, error, offline, reconnecting, and success",
      "Non-color-only status",
      "Manual QA Still Required"
    ],
    "Android UX accessibility docs"
  );

  assert(Boolean(packageJson.scripts?.["android:ux:check"]), "package.json exposes npm run android:ux:check");
  assert(packageJson.scripts?.["android:ux:check"] === "node scripts/android-ux-check.mjs", "android:ux:check runs the Android UX checker");
  assert(ci.includes("npm run android:ux:check"), "CI runs the Android UX checker");
  assert(production.includes("scripts/android-ux-check.mjs"), "Production readiness requires the Android UX checker source");
  assert(production.includes("docs/ANDROID_UX_ACCESSIBILITY.md"), "Production readiness requires the Android accessibility doc");
  assert(checklist.includes("Android UX/accessibility source validation"), "Build checklist tracks Android UX/accessibility source validation");
}

function printResults() {
  for (const message of passes) {
    console.log(`PASS ${message}`);
  }
  for (const message of failures) {
    console.error(`FAIL ${message}`);
  }

  console.log(`\nAndroid UX/accessibility source check: ${passes.length} passed, ${failures.length} failure(s).`);
  if (failures.length > 0) {
    process.exit(1);
  }
}

checkRequiredFiles();
checkDashboardAccessibility();
checkDashboardStateCoverage();
checkThemeAccessibility();
checkDocumentationAndCi();
printResults();
