package com.chatmod.mobile.ui.dashboard

import com.chatmod.mobile.data.local.dao.CommandDao
import com.chatmod.mobile.data.local.dao.TimerDao
import com.chatmod.mobile.data.local.entity.CommandEntity
import com.chatmod.mobile.data.local.entity.TimerEntity
import com.chatmod.mobile.data.remote.ChatModApiClient
import com.chatmod.mobile.data.remote.ChatModHttpException
import com.chatmod.mobile.data.remote.CommandSyncRequest
import com.chatmod.mobile.data.remote.TimerSyncRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

interface DashboardCommandTimerStore {
    val commands: Flow<List<CommandSummary>>
    val timers: Flow<List<TimerSummary>>
    suspend fun activeProfileId(): String
    suspend fun upsertCommand(command: CommandSummary)
    suspend fun deleteCommand(id: String)
    suspend fun upsertTimer(timer: TimerSummary)
    suspend fun deleteTimer(id: String)
}

class InMemoryDashboardCommandTimerStore(
    initialCommands: List<CommandSummary>,
    initialTimers: List<TimerSummary>,
    private val profileId: String = LocalDefaultProfileId
) : DashboardCommandTimerStore {
    private val commandState = MutableStateFlow(initialCommands)
    private val timerState = MutableStateFlow(initialTimers)

    override val commands: Flow<List<CommandSummary>> = commandState
    override val timers: Flow<List<TimerSummary>> = timerState

    override suspend fun activeProfileId(): String = profileId

    override suspend fun upsertCommand(command: CommandSummary) {
        commandState.value = commandState.value.upsertCommand(command)
    }

    override suspend fun deleteCommand(id: String) {
        commandState.value = commandState.value.filterNot { it.id == id }
    }

    override suspend fun upsertTimer(timer: TimerSummary) {
        timerState.value = timerState.value.upsertTimer(timer)
    }

    override suspend fun deleteTimer(id: String) {
        timerState.value = timerState.value.filterNot { it.id == id }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class RoomDashboardCommandTimerStore(
    private val defaultProfileId: String,
    profileIds: Flow<String?>,
    private val commandDao: CommandDao,
    private val timerDao: TimerDao
) : DashboardCommandTimerStore {
    private val activeProfileIds: Flow<String> = profileIds
        .map { profileId -> profileId?.takeIf { it.isNotBlank() } ?: defaultProfileId }
        .distinctUntilChanged()

    override val commands: Flow<List<CommandSummary>> = activeProfileIds
        .flatMapLatest { profileId -> commandDao.observeForProfile(profileId) }
        .map { rows -> rows.map { it.toSummary() } }

    override val timers: Flow<List<TimerSummary>> = activeProfileIds
        .flatMapLatest { profileId -> timerDao.observeForProfile(profileId) }
        .map { rows -> rows.map { it.toSummary() } }

    override suspend fun activeProfileId(): String = activeProfileIds.first()

    override suspend fun upsertCommand(command: CommandSummary) {
        commandDao.upsert(command.toEntity(activeProfileId()))
    }

    override suspend fun deleteCommand(id: String) {
        commandDao.deleteById(id)
    }

    override suspend fun upsertTimer(timer: TimerSummary) {
        timerDao.upsert(timer.toEntity(activeProfileId()))
    }

    override suspend fun deleteTimer(id: String) {
        timerDao.deleteById(id)
    }
}

class SyncingDashboardCommandTimerStore(
    private val localStore: DashboardCommandTimerStore,
    private val api: ChatModApiClient,
    private val accessTokenProvider: suspend () -> String?,
    private val refreshAccessTokenProvider: suspend () -> String? = accessTokenProvider
) : DashboardCommandTimerStore {
    override val commands: Flow<List<CommandSummary>> = localStore.commands
    override val timers: Flow<List<TimerSummary>> = localStore.timers

    override suspend fun activeProfileId(): String = localStore.activeProfileId()

    override suspend fun upsertCommand(command: CommandSummary) {
        val profileId = activeProfileId()
        localStore.upsertCommand(command)
        withBackendSession { accessToken ->
            val result = api.saveCommand(accessToken, command.toSyncRequest(profileId))
            if (result.id != command.id) {
                localStore.deleteCommand(command.id)
                localStore.upsertCommand(command.copy(id = result.id))
            }
        }
    }

    override suspend fun deleteCommand(id: String) {
        localStore.deleteCommand(id)
        withBackendSession { accessToken -> api.deleteCommand(accessToken, id) }
    }

    override suspend fun upsertTimer(timer: TimerSummary) {
        val profileId = activeProfileId()
        localStore.upsertTimer(timer)
        withBackendSession { accessToken ->
            val result = api.saveTimer(accessToken, timer.toSyncRequest(profileId))
            if (result.id != timer.id) {
                localStore.deleteTimer(timer.id)
                localStore.upsertTimer(timer.copy(id = result.id))
            }
        }
    }

    override suspend fun deleteTimer(id: String) {
        localStore.deleteTimer(id)
        withBackendSession { accessToken -> api.deleteTimer(accessToken, id) }
    }

    private suspend fun withBackendSession(block: suspend (String) -> Unit) {
        val accessToken = accessTokenProvider() ?: return
        val first = runCatching { block(accessToken) }
        if (first.isSuccess || !first.exceptionOrNull().isUnauthorized()) {
            return
        }

        val refreshedAccessToken = refreshAccessTokenProvider() ?: return
        runCatching { block(refreshedAccessToken) }
    }
}

private fun List<CommandSummary>.upsertCommand(command: CommandSummary): List<CommandSummary> {
    val existingIndex = indexOfFirst { it.id == command.id }
    return if (existingIndex >= 0) {
        mapIndexed { index, item -> if (index == existingIndex) command else item }
    } else {
        this + command
    }
}

private fun List<TimerSummary>.upsertTimer(timer: TimerSummary): List<TimerSummary> {
    val existingIndex = indexOfFirst { it.id == timer.id }
    return if (existingIndex >= 0) {
        mapIndexed { index, item -> if (index == existingIndex) timer else item }
    } else {
        this + timer
    }
}

private fun CommandSummary.toEntity(profileId: String): CommandEntity {
    val now = System.currentTimeMillis()
    return CommandEntity(
        id = id,
        profileId = profileId,
        name = name,
        response = response,
        aliasesJson = aliases.joinToString("\n"),
        cooldownSeconds = cooldownSeconds,
        accessLevel = accessLevel.name,
        enabled = enabled,
        createdAt = now,
        updatedAt = now
    )
}

private fun CommandEntity.toSummary(): CommandSummary {
    return CommandSummary(
        id = id,
        name = name,
        response = response,
        aliases = aliasesJson.lines().filter { it.isNotBlank() },
        cooldownSeconds = cooldownSeconds,
        accessLevel = runCatching { CommandAccessLevel.valueOf(accessLevel) }.getOrDefault(CommandAccessLevel.Everyone),
        enabled = enabled
    )
}

private fun TimerSummary.toEntity(profileId: String): TimerEntity {
    val now = System.currentTimeMillis()
    return TimerEntity(
        id = id,
        profileId = profileId,
        name = name,
        message = message,
        intervalMinutes = intervalMinutes,
        minChatMessages = minChatMessages,
        quietStartMinutes = quietStartMinutes,
        quietEndMinutes = quietEndMinutes,
        enabled = enabled,
        lastSentAt = null,
        createdAt = now,
        updatedAt = now
    )
}

private fun TimerEntity.toSummary(): TimerSummary {
    return TimerSummary(
        id = id,
        name = name,
        message = message,
        intervalMinutes = intervalMinutes,
        minChatMessages = minChatMessages,
        quietStartMinutes = quietStartMinutes,
        quietEndMinutes = quietEndMinutes,
        enabled = enabled
    )
}

private fun CommandSummary.toSyncRequest(profileId: String): CommandSyncRequest {
    return CommandSyncRequest(
        id = id,
        profileId = profileId,
        name = name,
        response = response,
        aliases = aliases,
        cooldownSeconds = cooldownSeconds,
        accessLevel = accessLevel.name.lowercase(),
        enabled = enabled
    )
}

private fun TimerSummary.toSyncRequest(profileId: String): TimerSyncRequest {
    return TimerSyncRequest(
        id = id,
        profileId = profileId,
        name = name,
        message = message,
        intervalMinutes = intervalMinutes,
        minChatMessages = minChatMessages,
        quietStartMinutes = quietStartMinutes,
        quietEndMinutes = quietEndMinutes,
        enabled = enabled
    )
}

private fun Throwable?.isUnauthorized(): Boolean {
    return this is ChatModHttpException && statusCode == 401
}

private const val LocalDefaultProfileId = "local-default-profile"
