package com.chatmod.mobile.data.local

import com.chatmod.mobile.data.ChatModRepository
import com.chatmod.mobile.data.local.dao.PendingSyncJobDao
import com.chatmod.mobile.data.local.entity.PendingSyncJobEntity
import com.chatmod.mobile.data.remote.ChatModHttpException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class PendingCloudSyncQueue(
    private val dao: PendingSyncJobDao,
    private val backend: ChatModRepository,
    private val accessTokenProvider: suspend () -> String?,
    private val refreshAccessTokenProvider: suspend () -> String? = accessTokenProvider,
    private val syncScope: CoroutineScope
) {
    private val draining = AtomicBoolean(false)

    suspend fun enqueueStreamSessionUpsert(
        sessionId: String,
        profileId: String,
        videoId: String,
        liveChatId: String,
        title: String?,
        startedAt: String? = null
    ) {
        enqueue(
            type = JobTypeStreamSessionUpsert,
            payload = JSONObject()
                .put("sessionId", sessionId)
                .put("profileId", profileId)
                .put("videoId", videoId)
                .put("liveChatId", liveChatId)
                .putOptional("title", title)
                .putOptional("startedAt", startedAt)
        )
    }

    suspend fun enqueueStreamSessionEnd(sessionId: String, endedAtIso: String? = null) {
        enqueue(
            type = JobTypeStreamSessionEnd,
            payload = JSONObject()
                .put("sessionId", sessionId)
                .putOptional("endedAtIso", endedAtIso)
        )
    }

    suspend fun enqueueStreamMessage(
        sessionId: String,
        youtubeMessageId: String,
        authorChannelId: String,
        authorName: String,
        text: String,
        receivedAtIso: String?
    ) {
        enqueue(
            type = JobTypeStreamMessage,
            payload = JSONObject()
                .put("sessionId", sessionId)
                .put("youtubeMessageId", youtubeMessageId)
                .put("authorChannelId", authorChannelId)
                .put("authorName", authorName)
                .put("text", text)
                .putOptional("receivedAtIso", receivedAtIso)
        )
    }

    suspend fun enqueueModerationAction(
        logId: String,
        sessionId: String,
        youtubeMessageId: String?,
        authorChannelId: String?,
        actionType: String,
        reason: String,
        confidence: Double?,
        metadataJson: String?
    ) {
        enqueue(
            type = JobTypeModerationAction,
            payload = JSONObject()
                .put("logId", logId)
                .put("sessionId", sessionId)
                .putOptional("youtubeMessageId", youtubeMessageId)
                .putOptional("authorChannelId", authorChannelId)
                .put("actionType", actionType)
                .put("reason", reason)
                .putOptional("confidence", confidence)
                .putOptional("metadataJson", metadataJson)
        )
    }

    suspend fun enqueueRuntimeEvent(
        sessionId: String,
        type: String,
        message: String,
        metadataJson: String?
    ) {
        enqueue(
            type = JobTypeRuntimeEvent,
            payload = JSONObject()
                .put("sessionId", sessionId)
                .put("type", type)
                .put("message", message)
                .putOptional("metadataJson", metadataJson)
        )
    }

    fun drain() {
        syncScope.launch {
            drainNow()
        }
    }

    suspend fun drainNow(): Boolean {
        if (!draining.compareAndSet(false, true)) {
            return false
        }

        return try {
            drainDueJobs()
            true
        } finally {
            draining.set(false)
        }
    }

    private suspend fun enqueue(type: String, payload: JSONObject) {
        val now = System.currentTimeMillis()
        dao.insert(
            PendingSyncJobEntity(
                id = UUID.randomUUID().toString(),
                type = type,
                payloadJson = payload.toString(),
                attempts = 0,
                nextAttemptAt = now,
                lastError = null,
                createdAt = now,
                updatedAt = now
            )
        )
        drain()
    }

    private suspend fun drainDueJobs() {
        var accessToken = accessTokenProvider() ?: return

        while (true) {
            val jobs = dao.dueJobs(System.currentTimeMillis(), DrainBatchSize)
            if (jobs.isEmpty()) {
                return
            }

            jobs.forEach { job ->
                val result = runCatching { syncJob(accessToken, job) }.recoverCatching { error ->
                    if (error.isUnauthorized()) {
                        accessToken = refreshAccessTokenProvider() ?: throw error
                        syncJob(accessToken, job)
                    } else {
                        throw error
                    }
                }
                if (result.isSuccess) {
                    dao.delete(job.id)
                } else {
                    val attempts = job.attempts + 1
                    val now = System.currentTimeMillis()
                    dao.markFailed(
                        id = job.id,
                        attempts = attempts,
                        nextAttemptAt = now + retryDelayMillis(attempts),
                        lastError = result.exceptionOrNull().toSyncError(),
                        updatedAt = now
                    )
                }
            }
        }
    }

    private suspend fun syncJob(accessToken: String, job: PendingSyncJobEntity) {
        val payload = JSONObject(job.payloadJson)
        when (job.type) {
            JobTypeStreamSessionUpsert -> backend.upsertStreamSession(
                accessToken = accessToken,
                sessionId = payload.getString("sessionId"),
                profileId = payload.getString("profileId"),
                videoId = payload.getString("videoId"),
                liveChatId = payload.getString("liveChatId"),
                title = payload.optNullableString("title"),
                startedAt = payload.optNullableString("startedAt")
            )
            JobTypeStreamSessionEnd -> backend.endStreamSession(
                accessToken = accessToken,
                sessionId = payload.getString("sessionId"),
                endedAtIso = payload.optNullableString("endedAtIso")
            )
            JobTypeStreamMessage -> backend.recordStreamMessage(
                accessToken = accessToken,
                sessionId = payload.getString("sessionId"),
                youtubeMessageId = payload.getString("youtubeMessageId"),
                authorChannelId = payload.getString("authorChannelId"),
                authorName = payload.getString("authorName"),
                text = payload.getString("text"),
                receivedAt = payload.optNullableString("receivedAtIso")
            )
            JobTypeModerationAction -> backend.recordModerationActionLog(
                accessToken = accessToken,
                sessionId = payload.getString("sessionId"),
                clientActionId = payload.optNullableString("logId"),
                youtubeMessageId = payload.optNullableString("youtubeMessageId"),
                authorChannelId = payload.optNullableString("authorChannelId"),
                actionType = payload.getString("actionType"),
                reason = payload.getString("reason"),
                confidence = payload.optNullableDouble("confidence"),
                metadata = mapOf(
                    "localMetadataJson" to payload.optNullableString("metadataJson")
                ).filterValues { value -> value != null }
            )
            JobTypeRuntimeEvent -> backend.recordRuntimeEvent(
                accessToken = accessToken,
                sessionId = payload.getString("sessionId"),
                type = payload.getString("type"),
                message = payload.getString("message"),
                metadata = mapOf(
                    "localMetadataJson" to payload.optNullableString("metadataJson")
                ).filterValues { value -> value != null }
            )
            else -> error("Unsupported pending sync job type: ${job.type}")
        }
    }

    private fun retryDelayMillis(attempts: Int): Long {
        val multiplier = 1L shl (attempts.coerceIn(1, MaxRetrySteps) - 1)
        return (BaseRetryDelayMillis * multiplier).coerceAtMost(MaxRetryDelayMillis)
    }

    private fun Throwable?.toSyncError(): String? {
        val value = this?.message?.takeIf { it.isNotBlank() } ?: this?.javaClass?.simpleName
        return value?.take(MaxStoredErrorLength)
    }

    private fun Throwable.isUnauthorized(): Boolean {
        return this is ChatModHttpException && statusCode == 401
    }

    private companion object {
        const val JobTypeStreamSessionUpsert = "stream_session_upsert"
        const val JobTypeStreamSessionEnd = "stream_session_end"
        const val JobTypeStreamMessage = "stream_message"
        const val JobTypeModerationAction = "moderation_action"
        const val JobTypeRuntimeEvent = "runtime_event"

        const val DrainBatchSize = 20
        const val BaseRetryDelayMillis = 30_000L
        const val MaxRetryDelayMillis = 15L * 60L * 1000L
        const val MaxRetrySteps = 6
        const val MaxStoredErrorLength = 240
    }
}

private fun JSONObject.putOptional(name: String, value: String?): JSONObject {
    if (value != null) {
        put(name, value)
    }
    return this
}

private fun JSONObject.putOptional(name: String, value: Double?): JSONObject {
    if (value != null) {
        put(name, value)
    }
    return this
}

private fun JSONObject.optNullableString(name: String): String? {
    return if (has(name) && !isNull(name)) optString(name) else null
}

private fun JSONObject.optNullableDouble(name: String): Double? {
    return if (has(name) && !isNull(name)) optDouble(name) else null
}
