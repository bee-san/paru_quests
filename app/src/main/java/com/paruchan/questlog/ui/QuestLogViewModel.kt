package com.paruchan.questlog.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.paruchan.questlog.BuildConfig
import com.paruchan.questlog.core.LevelProgress
import com.paruchan.questlog.core.Quest
import com.paruchan.questlog.core.QuestLogEngine
import com.paruchan.questlog.core.QuestLogState
import com.paruchan.questlog.core.QuestProgress
import com.paruchan.questlog.data.QuestLogRepository
import com.paruchan.questlog.update.GitHubReleaseUpdater
import com.paruchan.questlog.update.InstallResult
import com.paruchan.questlog.update.UpdateCheckResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class QuestLogViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = QuestLogRepository(File(application.filesDir, "questlog.json"))
    private val engine = QuestLogEngine()
    private val updater = GitHubReleaseUpdater(
        repository = BuildConfig.UPDATE_REPOSITORY,
        currentVersion = BuildConfig.VERSION_NAME,
    )

    var state: QuestLogState by androidx.compose.runtime.mutableStateOf(repository.load())
        private set

    var message: String? by androidx.compose.runtime.mutableStateOf(null)
        private set

    var updateInProgress: Boolean by androidx.compose.runtime.mutableStateOf(false)
        private set

    var pendingRestoreUri: Uri? by androidx.compose.runtime.mutableStateOf(null)
        private set

    val progress: LevelProgress
        get() = engine.levelProgress(state)

    val completedQuestIds: Set<String>
        get() = engine.completedQuestIds(state)

    fun canComplete(quest: Quest): Boolean = engine.canComplete(state, quest)

    fun progressFor(quest: Quest): QuestProgress = engine.progressFor(state, quest)

    fun clearMessage() {
        message = null
    }

    fun completeQuest(questId: String) {
        val result = engine.completeQuest(state, questId)
        state = result.state
        repository.save(state)
        message = result.message
    }

    fun importQuestPack(context: Context, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val json = readText(context, uri)
                repository.importQuestPack(json)
            }.onSuccess { result ->
                state = result.state
                message = result.summary
            }.onFailure { error ->
                message = error.message ?: "Quest pack import failed"
            }
        }
    }

    fun exportBackup(context: Context, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val json = withContext(Dispatchers.IO) { repository.exportBackup() }
                writeText(context, uri, json)
            }.onSuccess {
                message = "Backup exported"
            }.onFailure { error ->
                message = error.message ?: "Backup export failed"
            }
        }
    }

    fun requestRestore(uri: Uri) {
        pendingRestoreUri = uri
    }

    fun cancelRestore() {
        pendingRestoreUri = null
    }

    fun confirmRestore(context: Context) {
        val uri = pendingRestoreUri ?: return
        pendingRestoreUri = null
        viewModelScope.launch {
            runCatching {
                val json = readText(context, uri)
                repository.restoreBackup(json)
            }.onSuccess { restored ->
                state = restored
                message = "Backup restored"
            }.onFailure { error ->
                message = error.message ?: "Backup restore failed"
            }
        }
    }

    fun checkForUpdate(context: Context) {
        if (updateInProgress) return
        viewModelScope.launch {
            updateInProgress = true
            message = "Checking for update"
            runCatching {
                when (val result = updater.checkLatest()) {
                    is UpdateCheckResult.Available -> {
                        message = "Downloading ${result.candidate.release.tagName}"
                        val apk = updater.download(result.candidate, File(context.cacheDir, "updates"))
                        when (updater.install(context, apk)) {
                            is InstallResult.Started -> "Installer opened"
                            InstallResult.NeedsUnknownSourcesPermission -> "Allow installs from this app, then check again"
                        }
                    }

                    is UpdateCheckResult.NoInstallableAsset ->
                        "Release ${result.latestVersion} has no APK asset"

                    is UpdateCheckResult.UpToDate ->
                        "Already on ${result.currentVersion}"
                }
            }.onSuccess { updateMessage ->
                message = updateMessage
            }.onFailure { error ->
                message = error.message ?: "Update check failed"
            }
            updateInProgress = false
        }
    }

    private suspend fun readText(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("Could not read selected file")
    }

    private suspend fun writeText(context: Context, uri: Uri, text: String) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri, "wt")
            ?.bufferedWriter()
            ?.use { it.write(text) }
            ?: error("Could not write selected file")
    }
}
