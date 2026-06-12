package com.chatmod.mobile

import android.app.Application
import com.chatmod.mobile.data.ChatModRepository
import com.chatmod.mobile.data.local.LocalPrivacyStore
import com.chatmod.mobile.data.local.PendingCloudSyncQueue
import com.chatmod.mobile.data.local.PendingSyncDrainWorker
import com.chatmod.mobile.data.local.SettingsStore
import com.chatmod.mobile.data.local.dao.CommandDao
import com.chatmod.mobile.data.local.dao.TimerDao
import com.chatmod.mobile.data.remote.ChatModApiClient
import com.chatmod.mobile.data.remote.ChatModSessionManager
import com.chatmod.mobile.di.ApplicationScopeQualifier
import com.chatmod.mobile.di.ChatModCoreModule
import com.chatmod.mobile.runtime.BotLogSink
import com.chatmod.mobile.support.CrashReporter
import com.chatmod.mobile.support.UsageAnalyticsReporter
import com.chatmod.mobile.ui.dashboard.DashboardCommandTimerStore
import com.chatmod.mobile.ui.dashboard.RoomDashboardLogStore
import kotlinx.coroutines.CoroutineScope
import org.koin.android.ext.koin.androidContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.context.startKoin

class ChatModApplication : Application(), KoinComponent {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@ChatModApplication)
            modules(ChatModCoreModule)
        }
        crashReporter.install()
        analyticsReporter.track("app_open")
        PendingSyncDrainWorker.schedule(this)
    }

    val applicationScope: CoroutineScope get() = get<CoroutineScope>(ApplicationScopeQualifier)
    val commandDao: CommandDao get() = get()
    val timerDao: TimerDao get() = get()
    val apiClient: ChatModApiClient get() = get()
    val repository: ChatModRepository get() = get()
    val sessionManager: ChatModSessionManager get() = get()
    val settingsStore: SettingsStore get() = get()
    val localPrivacyStore: LocalPrivacyStore get() = get()
    val dashboardLogStore: RoomDashboardLogStore get() = get()
    val dashboardCommandTimerStore: DashboardCommandTimerStore get() = get()
    val botLogRepository: BotLogSink get() = get()
    val cloudSyncQueue: PendingCloudSyncQueue get() = get()
    private val crashReporter: CrashReporter get() = get()
    val analyticsReporter: UsageAnalyticsReporter get() = get()

    companion object {
        const val DefaultProfileId = "local-default-profile"
    }
}
