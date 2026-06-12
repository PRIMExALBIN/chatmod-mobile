package com.chatmod.mobile.support

import android.content.Context
import android.os.Build
import android.os.Process
import com.chatmod.mobile.BuildConfig
import com.chatmod.mobile.data.remote.ChatModApiClient
import com.chatmod.mobile.data.remote.ChatModHttpException
import com.chatmod.mobile.data.remote.SupportEventRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import kotlin.system.exitProcess

class CrashReporter(
    context: Context,
    private val scope: CoroutineScope,
    private val api: ChatModApiClient,
    private val accessTokenProvider: suspend () -> String?,
    private val refreshAccessTokenProvider: suspend () -> String?
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
    private var installed = false
    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    fun install() {
        if (installed) return
        installed = true
        flushPendingCrash()

        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            persistCrash(thread, throwable)
            previousHandler?.uncaughtException(thread, throwable) ?: run {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
    }

    private fun flushPendingCrash() {
        val pending = prefs.getString(PendingCrashKey, null)?.takeIf { it.isNotBlank() } ?: return
        val snapshot = CrashSnapshot.fromJson(pending) ?: return

        scope.launch {
            if (sendCrash(snapshot)) {
                prefs.edit().remove(PendingCrashKey).apply()
            }
        }
    }

    private suspend fun sendCrash(snapshot: CrashSnapshot): Boolean {
        val accessToken = accessTokenProvider() ?: return false
        val first = runCatching {
            api.recordSupportEvent(
                accessToken = accessToken,
                request = SupportEventRequest(
                    severity = "error",
                    message = "Android crash captured",
                    details = snapshot.toDetails()
                )
            )
        }
        if (first.isSuccess) return true
        if (first.exceptionOrNull()?.isUnauthorized() != true) return false

        val refreshedAccessToken = refreshAccessTokenProvider() ?: return false
        return runCatching {
            api.recordSupportEvent(
                accessToken = refreshedAccessToken,
                request = SupportEventRequest(
                    severity = "error",
                    message = "Android crash captured",
                    details = snapshot.toDetails()
                )
            )
        }.isSuccess
    }

    private fun persistCrash(thread: Thread, throwable: Throwable) {
        runCatching {
            val snapshot = CrashSnapshot.fromThrowable(thread, throwable)
            prefs.edit().putString(PendingCrashKey, snapshot.toJson().toString()).commit()
        }
    }

    companion object {
        fun clearStoredReports(context: Context): Int {
            val prefs = context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
            val hadPendingCrash = prefs.contains(PendingCrashKey)
            prefs.edit().remove(PendingCrashKey).commit()
            return if (hadPendingCrash) 1 else 0
        }

        private const val PrefsName = "chatmod_crash_reporter"
        private const val PendingCrashKey = "pending_crash"
    }
}

private data class CrashSnapshot(
    val id: String,
    val crashedAtMillis: Long,
    val threadName: String,
    val exceptionClass: String,
    val messagePreview: String?,
    val topFrame: String,
    val stackHash: String,
    val stackFrames: List<Map<String, Any?>>
) {
    fun toJson(): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("crashedAtMillis", crashedAtMillis)
            .put("threadName", threadName)
            .put("exceptionClass", exceptionClass)
            .put("messagePreview", messagePreview)
            .put("topFrame", topFrame)
            .put("stackHash", stackHash)
            .put("stackFrames", stackFrames.toJsonArray())
    }

    fun toDetails(): Map<String, Any?> {
        return mapOf(
            "eventType" to "android_crash",
            "crashId" to id,
            "crashedAtMillis" to crashedAtMillis,
            "threadName" to threadName,
            "exceptionClass" to exceptionClass,
            "messagePreview" to messagePreview,
            "topFrame" to topFrame,
            "stackHash" to stackHash,
            "stackFrames" to stackFrames,
            "appVersionName" to BuildConfig.VERSION_NAME,
            "appVersionCode" to BuildConfig.VERSION_CODE,
            "androidSdk" to Build.VERSION.SDK_INT,
            "deviceManufacturer" to Build.MANUFACTURER.take(80),
            "deviceModel" to Build.MODEL.take(80),
            "demoApi" to BuildConfig.CHATMOD_USE_DEMO_API
        )
    }

    companion object {
        fun fromThrowable(thread: Thread, throwable: Throwable): CrashSnapshot {
            val stackFrames = throwable.stackTrace.take(8).map { frame ->
                mapOf(
                    "className" to frame.className.take(180),
                    "methodName" to frame.methodName.take(120),
                    "fileName" to frame.fileName?.take(120),
                    "lineNumber" to frame.lineNumber
                )
            }
            val topFrame = stackFrames.firstOrNull()?.let { frame ->
                "${frame["className"]}.${frame["methodName"]}:${frame["lineNumber"]}"
            } ?: "unknown"

            return CrashSnapshot(
                id = "crash-${System.currentTimeMillis()}-${thread.id}",
                crashedAtMillis = System.currentTimeMillis(),
                threadName = thread.name.take(120),
                exceptionClass = throwable.javaClass.name.take(180),
                messagePreview = throwable.message?.redactedPreview(),
                topFrame = topFrame.take(260),
                stackHash = stackHash(throwable),
                stackFrames = stackFrames
            )
        }

        fun fromJson(raw: String): CrashSnapshot? {
            return runCatching {
                val json = JSONObject(raw)
                val frames = json.optJSONArray("stackFrames")
                CrashSnapshot(
                    id = json.optString("id"),
                    crashedAtMillis = json.optLong("crashedAtMillis"),
                    threadName = json.optString("threadName"),
                    exceptionClass = json.optString("exceptionClass"),
                    messagePreview = json.optNullableString("messagePreview"),
                    topFrame = json.optString("topFrame"),
                    stackHash = json.optString("stackHash"),
                    stackFrames = List(frames?.length() ?: 0) { index ->
                        val frame = frames!!.getJSONObject(index)
                        mapOf(
                            "className" to frame.optString("className"),
                            "methodName" to frame.optString("methodName"),
                            "fileName" to frame.optNullableString("fileName"),
                            "lineNumber" to frame.optInt("lineNumber")
                        )
                    }
                )
            }.getOrNull()
        }
    }
}

private fun Throwable.stackTraceText(): String {
    return buildString {
        append(this@stackTraceText.javaClass.name)
        append('\n')
        stackTrace.forEach { frame -> append(frame.toString()).append('\n') }
        cause?.let { cause ->
            append("Caused by: ").append(cause.javaClass.name).append('\n')
            cause.stackTrace.forEach { frame -> append(frame.toString()).append('\n') }
        }
    }
}

private fun stackHash(throwable: Throwable): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(throwable.stackTraceText().toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }.take(32)
}

private fun String.redactedPreview(): String {
    return take(240)
        .replace(Regex("""Bearer\s+[A-Za-z0-9._~+/=-]+"""), "Bearer [redacted]")
        .replace(Regex("""ya29\.[A-Za-z0-9._~+/=-]+"""), "ya29.[redacted]")
        .replace(Regex("""postgres(?:ql)?://\S+"""), "postgres://[redacted]")
}

private fun JSONObject.optNullableString(name: String): String? {
    return if (has(name) && !isNull(name)) optString(name) else null
}

private fun List<Map<String, Any?>>.toJsonArray(): JSONArray {
    return JSONArray().also { array ->
        forEach { frame ->
            array.put(JSONObject().also { json ->
                frame.forEach { (key, value) -> json.put(key, value) }
            })
        }
    }
}

private fun Throwable?.isUnauthorized(): Boolean {
    return this is ChatModHttpException && statusCode == 401
}
