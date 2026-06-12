package com.chatmod.mobile.data.local

import com.chatmod.mobile.data.local.dao.CommandDao
import com.chatmod.mobile.data.local.dao.ModerationLogDao
import com.chatmod.mobile.data.local.dao.PendingSyncJobDao
import com.chatmod.mobile.data.local.dao.TimerDao

class LocalPrivacyStore(
    private val commandDao: CommandDao,
    private val timerDao: TimerDao,
    private val logDao: ModerationLogDao,
    private val pendingSyncJobDao: PendingSyncJobDao,
    private val settingsStore: SettingsStore,
    private val clearCrashReports: suspend () -> Int = { 0 }
) {
    suspend fun wipeLocalData(): LocalWipeResult {
        val commandsDeleted = commandDao.deleteAll()
        val timersDeleted = timerDao.deleteAll()
        val chatMessagesDeleted = logDao.deleteAllChatMessages()
        val moderationLogsDeleted = logDao.deleteAllModerationLogs()
        val runtimeEventsDeleted = logDao.deleteAllRuntimeEvents()
        val pendingSyncJobsDeleted = pendingSyncJobDao.deleteAll()
        val crashReportsDeleted = clearCrashReports()
        settingsStore.clear()

        return LocalWipeResult(
            commandsDeleted = commandsDeleted,
            timersDeleted = timersDeleted,
            chatMessagesDeleted = chatMessagesDeleted,
            moderationLogsDeleted = moderationLogsDeleted,
            runtimeEventsDeleted = runtimeEventsDeleted,
            pendingSyncJobsDeleted = pendingSyncJobsDeleted,
            crashReportsDeleted = crashReportsDeleted
        )
    }
}

data class LocalWipeResult(
    val commandsDeleted: Int,
    val timersDeleted: Int,
    val chatMessagesDeleted: Int,
    val moderationLogsDeleted: Int,
    val runtimeEventsDeleted: Int,
    val pendingSyncJobsDeleted: Int,
    val crashReportsDeleted: Int
)
