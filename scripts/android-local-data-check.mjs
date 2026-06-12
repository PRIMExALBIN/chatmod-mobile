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
    "android/app/src/main/java/com/chatmod/mobile/data/local/ChatModDatabase.kt",
    "android/app/src/main/java/com/chatmod/mobile/data/local/SettingsStore.kt",
    "android/app/src/main/java/com/chatmod/mobile/data/local/LocalPrivacyStore.kt",
    "android/app/src/main/java/com/chatmod/mobile/data/local/PendingCloudSyncQueue.kt",
    "android/app/src/main/java/com/chatmod/mobile/data/local/PendingSyncDrainWorker.kt",
    "android/app/src/main/java/com/chatmod/mobile/data/local/dao/CommandDao.kt",
    "android/app/src/main/java/com/chatmod/mobile/data/local/dao/TimerDao.kt",
    "android/app/src/main/java/com/chatmod/mobile/data/local/dao/ModerationLogDao.kt",
    "android/app/src/main/java/com/chatmod/mobile/data/local/dao/PendingSyncJobDao.kt",
    "android/app/src/main/java/com/chatmod/mobile/data/local/entity/CommandEntity.kt",
    "android/app/src/main/java/com/chatmod/mobile/data/local/entity/TimerEntity.kt",
    "android/app/src/main/java/com/chatmod/mobile/data/local/entity/ChatMessageLogEntity.kt",
    "android/app/src/main/java/com/chatmod/mobile/data/local/entity/ModerationLogEntity.kt",
    "android/app/src/main/java/com/chatmod/mobile/data/local/entity/BotRuntimeEventEntity.kt",
    "android/app/src/main/java/com/chatmod/mobile/data/local/entity/PendingSyncJobEntity.kt",
    "android/app/src/main/java/com/chatmod/mobile/ui/dashboard/DashboardLogStore.kt",
    "android/app/src/main/java/com/chatmod/mobile/ui/dashboard/DashboardUiState.kt",
    "android/app/src/main/java/com/chatmod/mobile/ui/dashboard/DashboardViewModel.kt",
    "android/app/src/main/java/com/chatmod/mobile/data/remote/DemoChatModApiClient.kt",
    "docs/ANDROID_LOCAL_DATA.md",
    "BUILD_CHECKLIST.md",
    ".github/workflows/ci.yml"
  ];

  for (const file of requiredFiles) {
    assert(fileExists(file), `${file} exists`);
  }
}

function checkGradleLocalDataDependencies() {
  const gradle = readText("android/app/build.gradle.kts");

  includesEvery(
    gradle,
    [
      'id("com.google.devtools.ksp")',
      'implementation("androidx.datastore:datastore-preferences:1.1.1")',
      'implementation("androidx.room:room-ktx:2.6.1")',
      'implementation("androidx.room:room-runtime:2.6.1")',
      'implementation("androidx.work:work-runtime-ktx:2.11.2")',
      'ksp("androidx.room:room-compiler:2.6.1")',
      'arg("room.schemaLocation", "$projectDir/schemas")',
      'arg("room.incremental", "true")'
    ],
    "Android local data Gradle setup"
  );
}

function checkRoomDatabaseSource() {
  const database = readText("android/app/src/main/java/com/chatmod/mobile/data/local/ChatModDatabase.kt");

  includesEvery(
    database,
    [
      "@Database(",
      "CommandEntity::class",
      "TimerEntity::class",
      "ChatMessageLogEntity::class",
      "ModerationLogEntity::class",
      "BotRuntimeEventEntity::class",
      "PendingSyncJobEntity::class",
      "version = 6",
      "exportSchema = true",
      "abstract fun commandDao(): CommandDao",
      "abstract fun timerDao(): TimerDao",
      "abstract fun moderationLogDao(): ModerationLogDao",
      "abstract fun pendingSyncJobDao(): PendingSyncJobDao",
      "private val Migration1To2 = object : Migration(1, 2)",
      "private val Migration2To3 = object : Migration(2, 3)",
      "private val Migration3To4 = object : Migration(3, 4)",
      "private val Migration4To5 = object : Migration(4, 5)",
      "private val Migration5To6 = object : Migration(5, 6)",
      ".addMigrations(Migration1To2, Migration2To3, Migration3To4, Migration4To5, Migration5To6)"
    ],
    "Room database source"
  );

  includesEvery(
    database,
    [
      "CREATE TABLE IF NOT EXISTS pending_sync_jobs",
      "CREATE TABLE IF NOT EXISTS chat_message_logs",
      "ALTER TABLE moderation_logs ADD COLUMN reviewStatus TEXT",
      "ALTER TABLE timers ADD COLUMN quietStartMinutes INTEGER",
      "ALTER TABLE moderation_logs ADD COLUMN metadataJson TEXT"
    ],
    "Room migration source"
  );

  assert(!database.includes("fallbackToDestructiveMigration"), "Room database does not use destructive migration fallback");
}

function checkEntities() {
  const entities = [
    {
      path: "android/app/src/main/java/com/chatmod/mobile/data/local/entity/CommandEntity.kt",
      label: "Command entity",
      phrases: [
        'tableName = "commands"',
        'Index(value = ["profileId", "name"], unique = true)',
        "@PrimaryKey val id: String",
        "val aliasesJson: String",
        "val cooldownSeconds: Int",
        "val accessLevel: String",
        "val enabled: Boolean"
      ]
    },
    {
      path: "android/app/src/main/java/com/chatmod/mobile/data/local/entity/TimerEntity.kt",
      label: "Timer entity",
      phrases: [
        'tableName = "timers"',
        'Index(value = ["profileId", "name"], unique = true)',
        "val intervalMinutes: Int",
        "val minChatMessages: Int",
        "val quietStartMinutes: Int?",
        "val quietEndMinutes: Int?",
        "val lastSentAt: Long?"
      ]
    },
    {
      path: "android/app/src/main/java/com/chatmod/mobile/data/local/entity/ChatMessageLogEntity.kt",
      label: "Chat message log entity",
      phrases: [
        'tableName = "chat_message_logs"',
        'Index(value = ["sessionId", "youtubeMessageId"], unique = true)',
        "val youtubeMessageId: String",
        "val authorChannelId: String",
        "val receivedAtIso: String?"
      ]
    },
    {
      path: "android/app/src/main/java/com/chatmod/mobile/data/local/entity/ModerationLogEntity.kt",
      label: "Moderation action log entity",
      phrases: [
        'tableName = "moderation_logs"',
        'Index(value = ["reviewStatus"])',
        "val actionType: String",
        "val reason: String",
        "val confidence: Double?",
        "val metadataJson: String? = null",
        "val reviewStatus: String? = null"
      ]
    },
    {
      path: "android/app/src/main/java/com/chatmod/mobile/data/local/entity/BotRuntimeEventEntity.kt",
      label: "Bot runtime event entity",
      phrases: [
        'tableName = "bot_runtime_events"',
        'Index(value = ["sessionId", "createdAt"])',
        'Index(value = ["type"])',
        "val metadataJson: String?"
      ]
    },
    {
      path: "android/app/src/main/java/com/chatmod/mobile/data/local/entity/PendingSyncJobEntity.kt",
      label: "Pending sync job entity",
      phrases: [
        'tableName = "pending_sync_jobs"',
        'Index(value = ["nextAttemptAt", "createdAt"])',
        "val payloadJson: String",
        "val attempts: Int",
        "val nextAttemptAt: Long",
        "val lastError: String?"
      ]
    }
  ];

  for (const entity of entities) {
    includesEvery(readText(entity.path), entity.phrases, entity.label);
  }
}

function checkDaos() {
  const commandDao = readText("android/app/src/main/java/com/chatmod/mobile/data/local/dao/CommandDao.kt");
  const timerDao = readText("android/app/src/main/java/com/chatmod/mobile/data/local/dao/TimerDao.kt");
  const logDao = readText("android/app/src/main/java/com/chatmod/mobile/data/local/dao/ModerationLogDao.kt");
  const pendingDao = readText("android/app/src/main/java/com/chatmod/mobile/data/local/dao/PendingSyncJobDao.kt");

  includesEvery(
    commandDao,
    [
      "fun observeForProfile(profileId: String): Flow<List<CommandEntity>>",
      "suspend fun enabledForProfile(profileId: String): List<CommandEntity>",
      "@Upsert",
      "suspend fun deleteForProfile(profileId: String): Int",
      "suspend fun deleteAll(): Int"
    ],
    "Command DAO"
  );

  includesEvery(
    timerDao,
    [
      "fun observeForProfile(profileId: String): Flow<List<TimerEntity>>",
      "suspend fun enabledForProfile(profileId: String): List<TimerEntity>",
      "suspend fun markSent(id: String, sentAt: Long)",
      "suspend fun deleteForProfile(profileId: String): Int",
      "suspend fun deleteAll(): Int"
    ],
    "Timer DAO"
  );

  includesEvery(
    logDao,
    [
      "fun observeRecentChatMessages(limit: Int = 200): Flow<List<ChatMessageLogEntity>>",
      "fun observeRecentModerationLogs(limit: Int = 200): Flow<List<ModerationLogEntity>>",
      "fun observeRecentRuntimeEvents(limit: Int = 200): Flow<List<BotRuntimeEventEntity>>",
      "suspend fun insertChatMessage(message: ChatMessageLogEntity)",
      "suspend fun insertModerationLog(log: ModerationLogEntity)",
      "suspend fun insertRuntimeEvent(event: BotRuntimeEventEntity)",
      "suspend fun updateModerationLogReview(",
      "suspend fun deleteAllChatMessages(): Int",
      "suspend fun deleteAllModerationLogs(): Int",
      "suspend fun deleteAllRuntimeEvents(): Int"
    ],
    "Moderation log DAO"
  );

  includesEvery(
    pendingDao,
    [
      "suspend fun insert(job: PendingSyncJobEntity)",
      "suspend fun dueJobs(nowMillis: Long, limit: Int): List<PendingSyncJobEntity>",
      "suspend fun markFailed(",
      "suspend fun deleteAll(): Int",
      "fun observePendingCount(): Flow<Int>"
    ],
    "Pending sync DAO"
  );
}

function checkDataStoreAndPrivacyWipe() {
  const settings = readText("android/app/src/main/java/com/chatmod/mobile/data/local/SettingsStore.kt");
  const privacy = readText("android/app/src/main/java/com/chatmod/mobile/data/local/LocalPrivacyStore.kt");

  includesEvery(
    settings,
    [
      'preferencesDataStore(name = "chatmod_settings")',
      "val settings: Flow<BotSettings>",
      "val activeRuntime: Flow<ActiveBotRuntimeState?>",
      "val lastSelectedStream: Flow<LastSelectedStreamState?>",
      "val activeRulePreset: Flow<ActiveRulePresetState?>",
      "suspend fun setEmergencyMode(enabled: Boolean)",
      "suspend fun setLinkLockdown(enabled: Boolean)",
      "suspend fun setReducedMotion(enabled: Boolean)",
      "suspend fun setHighContrast(enabled: Boolean)",
      "suspend fun setLowDataMode(enabled: Boolean)",
      "suspend fun setShareUsageAnalytics(enabled: Boolean)",
      "suspend fun clear()",
      'stringPreferencesKey("selected_profile_id")',
      'booleanPreferencesKey("emergency_mode")',
      'booleanPreferencesKey("link_lockdown")',
      'booleanPreferencesKey("reduced_motion")',
      'booleanPreferencesKey("high_contrast")',
      'booleanPreferencesKey("low_data_mode")',
      'booleanPreferencesKey("share_usage_analytics")',
      'stringPreferencesKey("active_session_id")',
      'stringPreferencesKey("last_selected_live_chat_id")',
      'stringPreferencesKey("active_rule_preset_config_json")'
    ],
    "DataStore settings source"
  );

  includesEvery(
    privacy,
    [
      "suspend fun wipeLocalData(): LocalWipeResult",
      "commandDao.deleteAll()",
      "timerDao.deleteAll()",
      "logDao.deleteAllChatMessages()",
      "logDao.deleteAllModerationLogs()",
      "logDao.deleteAllRuntimeEvents()",
      "pendingSyncJobDao.deleteAll()",
      "clearCrashReports()",
      "settingsStore.clear()",
      "val pendingSyncJobsDeleted: Int",
      "val crashReportsDeleted: Int"
    ],
    "Local privacy wipe source"
  );
}

function checkSelectedProfileCommandTimers() {
  const store = readText("android/app/src/main/java/com/chatmod/mobile/ui/dashboard/DashboardCommandTimerStore.kt");
  const moduleSource = readText("android/app/src/main/java/com/chatmod/mobile/di/ChatModModules.kt");
  const service = readText("android/app/src/main/java/com/chatmod/mobile/runtime/BotForegroundService.kt");

  includesEvery(
    store,
    [
      "profileIds: Flow<String?>",
      ".distinctUntilChanged()",
      ".flatMapLatest { profileId -> commandDao.observeForProfile(profileId) }",
      ".flatMapLatest { profileId -> timerDao.observeForProfile(profileId) }",
      "override suspend fun activeProfileId(): String = activeProfileIds.first()",
      "command.toEntity(activeProfileId())",
      "timer.toEntity(activeProfileId())",
      "val profileId = activeProfileId()",
      "command.toSyncRequest(profileId)",
      "timer.toSyncRequest(profileId)"
    ],
    "Selected-profile command/timer store"
  );

  includesEvery(
    moduleSource,
    [
      "val settingsStore = get<SettingsStore>()",
      "profileIds = settingsStore.settings.map { settings -> settings.selectedProfileId }"
    ],
    "Selected-profile command/timer DI"
  );

  includesEvery(
    service,
    [
      "private var activeProfileId: String = ChatModApplication.DefaultProfileId",
      "activeProfileId = settings.selectedProfileId ?: ChatModApplication.DefaultProfileId",
      "val loopProfileId = activeProfileId",
      ".enabledForProfile(loopProfileId)",
      "profileId = loopProfileId"
    ],
    "Selected-profile foreground runtime"
  );
}

function checkPendingSyncRecovery() {
  const queue = readText("android/app/src/main/java/com/chatmod/mobile/data/local/PendingCloudSyncQueue.kt");
  const worker = readText("android/app/src/main/java/com/chatmod/mobile/data/local/PendingSyncDrainWorker.kt");
  const application = readText("android/app/src/main/java/com/chatmod/mobile/ChatModApplication.kt");

  includesEvery(
    queue,
    [
      "class PendingCloudSyncQueue(",
      "fun drain()",
      "suspend fun drainNow(): Boolean",
      "dao.dueJobs(System.currentTimeMillis(), DrainBatchSize)",
      "dao.delete(job.id)",
      "dao.markFailed(",
      "refreshAccessTokenProvider()",
      'const val JobTypeStreamSessionUpsert = "stream_session_upsert"',
      'const val JobTypeStreamSessionEnd = "stream_session_end"',
      'const val JobTypeStreamMessage = "stream_message"',
      'const val JobTypeModerationAction = "moderation_action"',
      'const val JobTypeRuntimeEvent = "runtime_event"',
      "const val MaxRetryDelayMillis = 15L * 60L * 1000L"
    ],
    "Pending cloud sync queue"
  );

  includesEvery(
    worker,
    [
      "class PendingSyncDrainWorker(",
      "app.cloudSyncQueue.drainNow()",
      "PeriodicWorkRequestBuilder<PendingSyncDrainWorker>(15, TimeUnit.MINUTES)",
      "setRequiredNetworkType(NetworkType.CONNECTED)",
      "setRequiresBatteryNotLow(true)",
      "enqueueUniquePeriodicWork(",
      "ExistingPeriodicWorkPolicy.UPDATE"
    ],
    "Pending sync WorkManager source"
  );

  assert(application.includes("PendingSyncDrainWorker.schedule(this)"), "Application schedules pending sync drain worker");
}

function checkPlanAwareLocalHistory() {
  const logStore = readText("android/app/src/main/java/com/chatmod/mobile/ui/dashboard/DashboardLogStore.kt");
  const uiState = readText("android/app/src/main/java/com/chatmod/mobile/ui/dashboard/DashboardUiState.kt");
  const viewModel = readText("android/app/src/main/java/com/chatmod/mobile/ui/dashboard/DashboardViewModel.kt");
  const demoApi = readText("android/app/src/main/java/com/chatmod/mobile/data/remote/DemoChatModApiClient.kt");

  includesEvery(
    logStore,
    [
      "const val StarterLocalHistoryLimit = 120",
      "const val ProLocalHistoryLimit = 1000",
      "const val CreatorLocalHistoryLimit = 2000",
      "limit: Int = CreatorLocalHistoryLimit",
      "limit.coerceIn(StarterLocalHistoryLimit, CreatorLocalHistoryLimit)",
      "dao.observeRecentChatMessages(historyLimit)",
      "dao.observeRecentModerationLogs(historyLimit)",
      "dao.observeRecentRuntimeEvents(historyLimit)"
    ],
    "Dashboard Room local history source"
  );

  includesEvery(
    uiState,
    [
      "val localHistoryLimit: Int = StarterLocalHistoryLimit",
      "val availableLocalHistoryEntries: Int = 0",
      "val availableLocalHistoryUsers: Int = 0"
    ],
    "Dashboard local history state"
  );

  includesEvery(
    viewModel,
    [
      "latestLocalLogEntries",
      "latestLocalUsers",
      "withBillingSummary(",
      "withLocalLogEntries(",
      "withLocalUserHistory(",
      "features.intFeature(\"localHistoryLimit\", fallbackHistoryLimit).normalizedLocalHistoryLimit()"
    ],
    "Dashboard entitlement-aware local history ViewModel"
  );

  includesEvery(
    demoApi,
    [
      "\"localHistoryLimit\" to StarterLocalHistoryLimitValue",
      "\"localHistoryLimit\" to if (creatorPlan) CreatorLocalHistoryLimitValue else ProLocalHistoryLimitValue"
    ],
    "Demo entitlement local history features"
  );
}

function checkDocumentationAndCi() {
  const docs = readText("docs/ANDROID_LOCAL_DATA.md");
  const packageJson = JSON.parse(readText("package.json"));
  const ci = readText(".github/workflows/ci.yml");
  const production = readText("scripts/production-readiness.mjs");
  const checklist = readText("BUILD_CHECKLIST.md");
  const release = readText("docs/PRODUCTION_RELEASE_CHECKLIST.md");

  includesEvery(
    docs,
    [
      "npm run android:data:check",
      "Room",
      "DataStore",
      "WorkManager",
      "Source Gate",
      "Manual QA Still Required"
    ],
    "Android local data docs"
  );

  assert(Boolean(packageJson.scripts?.["android:data:check"]), "package.json exposes npm run android:data:check");
  assert(packageJson.scripts?.["android:data:check"] === "node scripts/android-local-data-check.mjs", "android:data:check runs the Android local data checker");
  assert(ci.includes("npm run android:data:check"), "CI runs the Android local data checker");
  assert(production.includes("scripts/android-local-data-check.mjs"), "Production readiness requires the Android local data checker source");
  assert(production.includes("docs/ANDROID_LOCAL_DATA.md"), "Production readiness requires the Android local data doc");
  assert(checklist.includes("Android Room/DataStore source validation"), "Build checklist tracks Android Room/DataStore source validation");
  assert(release.includes("npm run android:data:check"), "Production release checklist includes Android local data source gate");
  assert(docs.includes("plan-aware local history"), "Android local data docs mention plan-aware local history");
  assert(checklist.includes("Android plan-aware local history"), "Build checklist tracks Android plan-aware local history");
}

function printResults() {
  for (const message of passes) {
    console.log(`PASS ${message}`);
  }
  for (const message of failures) {
    console.error(`FAIL ${message}`);
  }

  console.log(`\nAndroid local data source check: ${passes.length} passed, ${failures.length} failure(s).`);
  if (failures.length > 0) {
    process.exit(1);
  }
}

checkRequiredFiles();
checkGradleLocalDataDependencies();
checkRoomDatabaseSource();
checkEntities();
checkDaos();
checkDataStoreAndPrivacyWipe();
checkSelectedProfileCommandTimers();
checkPendingSyncRecovery();
checkPlanAwareLocalHistory();
checkDocumentationAndCi();
printResults();
