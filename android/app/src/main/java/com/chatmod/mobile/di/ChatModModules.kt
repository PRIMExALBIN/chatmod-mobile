package com.chatmod.mobile.di

import com.chatmod.mobile.BuildConfig
import com.chatmod.mobile.ChatModApplication
import com.chatmod.mobile.billing.PlayBillingManager
import com.chatmod.mobile.data.ChatModRepository
import com.chatmod.mobile.data.local.ChatModDatabase
import com.chatmod.mobile.data.local.LocalLogRepository
import com.chatmod.mobile.data.local.LocalPrivacyStore
import com.chatmod.mobile.data.local.PendingCloudSyncQueue
import com.chatmod.mobile.data.local.SettingsStore
import com.chatmod.mobile.data.local.SyncingLogRepository
import com.chatmod.mobile.data.remote.ChatModApiClient
import com.chatmod.mobile.data.remote.ChatModSessionManager
import com.chatmod.mobile.data.remote.DemoChatModApiClient
import com.chatmod.mobile.data.remote.HttpChatModApiClient
import com.chatmod.mobile.runtime.BotLogSink
import com.chatmod.mobile.support.CrashReporter
import com.chatmod.mobile.support.UsageAnalyticsReporter
import com.chatmod.mobile.ui.dashboard.DashboardCommandTimerStore
import com.chatmod.mobile.ui.dashboard.RoomDashboardCommandTimerStore
import com.chatmod.mobile.ui.dashboard.RoomDashboardLogStore
import com.chatmod.mobile.ui.dashboard.SyncingDashboardCommandTimerStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

val ApplicationScopeQualifier = named("applicationScope")

val ChatModCoreModule = module {
    single<CoroutineScope>(ApplicationScopeQualifier) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    single { ChatModDatabase.getInstance(androidContext()) }
    single { get<ChatModDatabase>().commandDao() }
    single { get<ChatModDatabase>().timerDao() }
    single { get<ChatModDatabase>().moderationLogDao() }
    single { get<ChatModDatabase>().pendingSyncJobDao() }

    single<ChatModApiClient> {
        if (BuildConfig.CHATMOD_USE_DEMO_API) {
            DemoChatModApiClient()
        } else {
            HttpChatModApiClient(BuildConfig.CHATMOD_API_BASE_URL)
        }
    }
    single { ChatModRepository(get()) }
    single { ChatModSessionManager(androidContext(), get()) }
    single { SettingsStore(androidContext()) }
    single { PlayBillingManager(androidContext()) }

    single {
        LocalPrivacyStore(
            commandDao = get(),
            timerDao = get(),
            logDao = get(),
            pendingSyncJobDao = get(),
            settingsStore = get(),
            clearCrashReports = { CrashReporter.clearStoredReports(androidContext()) }
        )
    }

    single { RoomDashboardLogStore(get()) }
    single<DashboardCommandTimerStore> {
        val sessionManager = get<ChatModSessionManager>()
        val settingsStore = get<SettingsStore>()
        val localStore = RoomDashboardCommandTimerStore(
            defaultProfileId = ChatModApplication.DefaultProfileId,
            profileIds = settingsStore.settings.map { settings -> settings.selectedProfileId },
            commandDao = get(),
            timerDao = get()
        )

        SyncingDashboardCommandTimerStore(
            localStore = localStore,
            api = get(),
            accessTokenProvider = { sessionManager.currentAccessToken() },
            refreshAccessTokenProvider = { sessionManager.refreshedAccessToken() }
        )
    }

    single {
        val sessionManager = get<ChatModSessionManager>()
        PendingCloudSyncQueue(
            dao = get(),
            backend = get(),
            accessTokenProvider = { sessionManager.currentAccessToken() },
            refreshAccessTokenProvider = { sessionManager.refreshedAccessToken() },
            syncScope = get<CoroutineScope>(ApplicationScopeQualifier)
        ).also { it.drain() }
    }

    single<BotLogSink> {
        SyncingLogRepository(
            local = LocalLogRepository(get()),
            cloudSyncQueue = get()
        )
    }

    single {
        val sessionManager = get<ChatModSessionManager>()
        CrashReporter(
            context = androidContext(),
            scope = get<CoroutineScope>(ApplicationScopeQualifier),
            api = get(),
            accessTokenProvider = { sessionManager.currentAccessToken() },
            refreshAccessTokenProvider = { sessionManager.refreshedAccessToken() }
        )
    }

    single {
        val sessionManager = get<ChatModSessionManager>()
        UsageAnalyticsReporter(
            settingsStore = get(),
            scope = get<CoroutineScope>(ApplicationScopeQualifier),
            api = get(),
            accessTokenProvider = { sessionManager.currentAccessToken() },
            refreshAccessTokenProvider = { sessionManager.refreshedAccessToken() }
        )
    }
}
