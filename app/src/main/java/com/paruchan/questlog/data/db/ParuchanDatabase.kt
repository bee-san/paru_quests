package com.paruchan.questlog.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        QuestEntity::class,
        CompletionEntity::class,
        LevelEntity::class,
        JournalEntryEntity::class,
        SharedPackImportMarkerEntity::class,
        AppMetadataEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class ParuchanDatabase : RoomDatabase() {
    abstract fun questLogDao(): QuestLogDao

    companion object {
        private const val DATABASE_NAME = "paruchan-questlog.db"

        @Volatile
        private var instance: ParuchanDatabase? = null

        fun getInstance(context: Context): ParuchanDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ParuchanDatabase::class.java,
                    DATABASE_NAME,
                ).build().also { instance = it }
            }
    }
}
