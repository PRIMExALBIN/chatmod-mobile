package com.chatmod.mobile.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.chatmod.mobile.data.local.dao.CommandDao
import com.chatmod.mobile.data.local.dao.ModerationLogDao
import com.chatmod.mobile.data.local.dao.PendingSyncJobDao
import com.chatmod.mobile.data.local.dao.TimerDao
import com.chatmod.mobile.data.local.entity.BotRuntimeEventEntity
import com.chatmod.mobile.data.local.entity.ChatMessageLogEntity
import com.chatmod.mobile.data.local.entity.CommandEntity
import com.chatmod.mobile.data.local.entity.ModerationLogEntity
import com.chatmod.mobile.data.local.entity.PendingSyncJobEntity
import com.chatmod.mobile.data.local.entity.TimerEntity

@Database(
    entities = [
        CommandEntity::class,
        TimerEntity::class,
        ChatMessageLogEntity::class,
        ModerationLogEntity::class,
        BotRuntimeEventEntity::class,
        PendingSyncJobEntity::class
    ],
    version = 6,
    exportSchema = true
)
abstract class ChatModDatabase : RoomDatabase() {
    abstract fun commandDao(): CommandDao
    abstract fun timerDao(): TimerDao
    abstract fun moderationLogDao(): ModerationLogDao
    abstract fun pendingSyncJobDao(): PendingSyncJobDao

    companion object {
        @Volatile
        private var instance: ChatModDatabase? = null

        private val Migration1To2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS pending_sync_jobs (
                        id TEXT NOT NULL PRIMARY KEY,
                        type TEXT NOT NULL,
                        payloadJson TEXT NOT NULL,
                        attempts INTEGER NOT NULL,
                        nextAttemptAt INTEGER NOT NULL,
                        lastError TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pending_sync_jobs_type ON pending_sync_jobs(type)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pending_sync_jobs_nextAttemptAt_createdAt ON pending_sync_jobs(nextAttemptAt, createdAt)")
            }
        }

        private val Migration2To3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS chat_message_logs (
                        id TEXT NOT NULL PRIMARY KEY,
                        sessionId TEXT NOT NULL,
                        youtubeMessageId TEXT NOT NULL,
                        authorChannelId TEXT NOT NULL,
                        authorName TEXT NOT NULL,
                        text TEXT NOT NULL,
                        receivedAtIso TEXT,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_message_logs_sessionId_createdAt ON chat_message_logs(sessionId, createdAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_message_logs_authorChannelId ON chat_message_logs(authorChannelId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_chat_message_logs_sessionId_youtubeMessageId ON chat_message_logs(sessionId, youtubeMessageId)")
            }
        }

        private val Migration3To4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE moderation_logs ADD COLUMN reviewStatus TEXT")
                db.execSQL("ALTER TABLE moderation_logs ADD COLUMN reviewedAt INTEGER")
                db.execSQL("ALTER TABLE moderation_logs ADD COLUMN reviewNote TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_moderation_logs_reviewStatus ON moderation_logs(reviewStatus)")
            }
        }

        private val Migration4To5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE timers ADD COLUMN quietStartMinutes INTEGER")
                db.execSQL("ALTER TABLE timers ADD COLUMN quietEndMinutes INTEGER")
            }
        }

        private val Migration5To6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE moderation_logs ADD COLUMN metadataJson TEXT")
            }
        }

        fun getInstance(context: Context): ChatModDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ChatModDatabase::class.java,
                    "chatmod.db"
                )
                    .addMigrations(Migration1To2, Migration2To3, Migration3To4, Migration4To5, Migration5To6)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
