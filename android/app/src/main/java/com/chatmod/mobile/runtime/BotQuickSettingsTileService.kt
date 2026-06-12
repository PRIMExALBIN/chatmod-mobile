package com.chatmod.mobile.runtime

import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.chatmod.mobile.ChatModApplication
import com.chatmod.mobile.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BotQuickSettingsTileService : TileService() {
    private val tileScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        tileScope.launch {
            val app = application as ChatModApplication
            val activeRuntime = app.settingsStore.activeRuntime.first()
            if (activeRuntime == null) {
                startBotFromSavedStream(app)
            } else {
                stopBot()
            }
            refreshTile()
        }
    }

    override fun onDestroy() {
        tileScope.cancel()
        super.onDestroy()
    }

    private fun refreshTile() {
        tileScope.launch {
            val app = application as ChatModApplication
            val isRunning = app.settingsStore.activeRuntime.first() != null
            qsTile?.apply {
                state = if (isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                label = getString(if (isRunning) R.string.bot_tile_stop_label else R.string.bot_tile_start_label)
                icon = Icon.createWithResource(this@BotQuickSettingsTileService, R.drawable.ic_chatmod_mark)
                updateTile()
            }
        }
    }

    private suspend fun startBotFromSavedStream(app: ChatModApplication) {
        val savedStream = app.settingsStore.lastSelectedStream.first()
        val intent = Intent(this, BotForegroundService::class.java)
            .setAction(BotForegroundService.ActionStart)

        if (savedStream != null) {
            intent
                .putExtra(
                    BotForegroundService.ExtraSessionId,
                    "${savedStream.liveChatId}-${System.currentTimeMillis()}"
                )
                .putExtra(BotForegroundService.ExtraLiveChatId, savedStream.liveChatId)
                .putExtra(BotForegroundService.ExtraVideoId, savedStream.videoId)
                .putExtra(BotForegroundService.ExtraStreamTitle, savedStream.streamTitle)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopBot() {
        startService(
            Intent(this, BotForegroundService::class.java)
                .setAction(BotForegroundService.ActionStop)
        )
    }
}
