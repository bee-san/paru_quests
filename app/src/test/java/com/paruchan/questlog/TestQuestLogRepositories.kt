package com.paruchan.questlog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.paruchan.questlog.data.QuestLogRepository
import com.paruchan.questlog.data.db.ParuchanDatabase
import java.io.File
import java.time.Clock

fun testQuestLogRepository(
    root: File,
    legacyStateFile: File = File(root, "questlog.json"),
    clock: Clock = Clock.systemDefaultZone(),
    backupDirectory: File = File(root, "questlog-backups"),
): QuestLogRepository {
    val database = testParuchanDatabase()
    return QuestLogRepository(
        database = database,
        legacyStateFile = legacyStateFile,
        clock = clock,
        backupDirectory = backupDirectory,
    )
}

fun testParuchanDatabase(): ParuchanDatabase {
    val context = ApplicationProvider.getApplicationContext<Context>()
    return Room.inMemoryDatabaseBuilder(context, ParuchanDatabase::class.java)
        .allowMainThreadQueries()
        .build()
}
