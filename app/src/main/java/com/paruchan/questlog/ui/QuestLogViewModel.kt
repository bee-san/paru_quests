package com.paruchan.questlog.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.paruchan.questlog.BuildConfig
import com.paruchan.questlog.core.LevelProgress
import com.paruchan.questlog.core.Quest
import com.paruchan.questlog.core.QuestGoalType
import com.paruchan.questlog.core.QuestLogEngine
import com.paruchan.questlog.core.QuestLogState
import com.paruchan.questlog.core.QuestProgress
import com.paruchan.questlog.data.BundledSharedPackRepository
import com.paruchan.questlog.data.QuestLogRepository
import com.paruchan.questlog.data.SharedPackSecretStore
import com.paruchan.questlog.data.UserBackupFolderStore
import com.paruchan.questlog.data.UserBackupWriteResult
import com.paruchan.questlog.notification.QuestNotificationPreferences
import com.paruchan.questlog.notification.QuestNotificationScheduler
import com.paruchan.questlog.notification.QuestNotificationSettings
import com.paruchan.questlog.update.GitHubReleaseUpdater
import com.paruchan.questlog.update.InstallResult
import com.paruchan.questlog.update.UpdateCheckResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class QuestLogViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = QuestLogRepository(File(application.filesDir, "questlog.json"))
    private val sharedPackRepository = BundledSharedPackRepository(application, repository)
    private val sharedPackSecretStore = SharedPackSecretStore(application)
    private val userBackupFolderStore = UserBackupFolderStore(application)
    private val questNotificationPreferences = QuestNotificationPreferences(application)
    private val engine = QuestLogEngine()
    private val updater = GitHubReleaseUpdater(
        repository = BuildConfig.UPDATE_REPOSITORY,
        currentVersion = BuildConfig.VERSION_NAME,
    )

    var state: QuestLogState by mutableStateOf(QuestLogState())
        private set

    var message: String? by mutableStateOf(null)
        private set

    var completionCelebration: CompletionCelebration? by mutableStateOf(null)
        private set

    var updateInProgress: Boolean by mutableStateOf(false)
        private set

    var sharedPackImportInProgress: Boolean by mutableStateOf(false)
        private set

    var sharedPackPasswordSaved: Boolean by mutableStateOf(sharedPackSecretStore.hasPassword())
        private set

    var userBackupFolderEnabled: Boolean by mutableStateOf(userBackupFolderStore.hasFolder())
        private set

    var userBackupInProgress: Boolean by mutableStateOf(false)
        private set

    var pendingRestoreUri: Uri? by mutableStateOf(null)
        private set

    var questNotificationSettings: QuestNotificationSettings by mutableStateOf(questNotificationPreferences.load())
        private set

    private val loadJob = viewModelScope.launch {
        state = withContext(Dispatchers.IO) { repository.load() }
    }

    val progress: LevelProgress
        get() = engine.levelProgress(state)

    val completedQuestIds: Set<String>
        get() = engine.completedQuestIds(state)

    fun canComplete(quest: Quest): Boolean = engine.canComplete(state, quest)

    fun progressFor(quest: Quest): QuestProgress = engine.progressFor(state, quest)

    fun clearMessage() {
        message = null
    }

    fun clearCompletionCelebration() {
        completionCelebration = null
    }

    fun updateQuestNotificationTime(hour: Int, minute: Int) {
        val next = questNotificationSettings.copy(hour = hour, minute = minute).normalized()
        questNotificationSettings = next
        questNotificationPreferences.save(next)
        if (next.enabled) {
            QuestNotificationScheduler.scheduleNext(getApplication(), next)
            message = "Quest reminders set for ${next.timeLabel}"
        }
    }

    fun enableQuestNotifications() {
        val next = questNotificationSettings.copy(enabled = true).normalized()
        questNotificationSettings = next
        questNotificationPreferences.save(next)
        QuestNotificationScheduler.scheduleNext(getApplication(), next)
        message = "Quest reminders set for ${next.timeLabel}"
    }

    fun disableQuestNotifications() {
        val next = questNotificationSettings.copy(enabled = false).normalized()
        questNotificationSettings = next
        questNotificationPreferences.save(next)
        QuestNotificationScheduler.cancel(getApplication())
        message = "Quest reminders off"
    }

    fun notificationPermissionDenied() {
        message = "Notifications permission is required for reminders"
    }

    fun completeQuest(questId: String, progressAmount: Int = 1) {
        viewModelScope.launch {
            loadJob.join()
            val previousState = state
            val quest = previousState.quests.firstOrNull { it.id == questId }
            val result = engine.completeQuest(state, questId, progressAmount = progressAmount)
            state = result.state
            message = result.message
            if (result.completion != null) {
                runCatching {
                    withContext(Dispatchers.IO) { repository.save(result.state) }
                }.onFailure { error ->
                    state = previousState
                    message = error.message ?: "Quest completion failed"
                    return@launch
                }
                writeUserBackup(result.state, announceSuccess = false)
                if (
                    quest != null &&
                    result.completion.xpAwarded > 0 &&
                    QuestGoalType.from(quest) != QuestGoalType.Counter
                ) {
                    completionCelebration = CompletionCelebration(
                        id = result.completion.id,
                        questTitle = quest.title,
                        xpAwarded = result.completion.xpAwarded,
                    )
                }
            }
        }
    }

    fun importQuestPack(context: Context, uri: Uri) {
        viewModelScope.launch {
            loadJob.join()
            runCatching {
                val json = readText(context, uri)
                withContext(Dispatchers.IO) { repository.importQuestPack(json) }
            }.onSuccess { result ->
                state = result.state
                message = result.summary
                writeUserBackup(result.state, announceSuccess = false)
            }.onFailure { error ->
                message = error.message ?: "Quest pack import failed"
            }
        }
    }

    fun saveSharedPackPassword(password: String) {
        if (password.isBlank()) {
            message = "Shared pack password is required"
            return
        }
        importSharedPacks(
            automatic = false,
            passwordOverride = password,
            savePasswordOnSuccess = true,
        )
    }

    fun clearSharedPackPassword() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { sharedPackSecretStore.clearPassword() }
            sharedPackPasswordSaved = false
            message = "Shared pack password cleared"
        }
    }

    fun autoImportSharedPacks() {
        if (sharedPackPasswordSaved) {
            importSharedPacks(automatic = true)
        }
    }

    fun importSharedPacks() {
        importSharedPacks(automatic = false)
    }

    fun exportBackup(context: Context, uri: Uri) {
        viewModelScope.launch {
            loadJob.join()
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

    fun selectBackupFolder(uri: Uri) {
        viewModelScope.launch {
            loadJob.join()
            runCatching {
                withContext(Dispatchers.IO) { userBackupFolderStore.saveFolder(uri) }
                userBackupFolderEnabled = true
                writeUserBackup(state, announceSuccess = true)
            }.onFailure { error ->
                withContext(Dispatchers.IO) { userBackupFolderStore.clearFolder() }
                userBackupFolderEnabled = false
                message = error.message ?: "Could not use that backup folder"
            }
        }
    }

    fun backupNow() {
        viewModelScope.launch {
            loadJob.join()
            if (!userBackupFolderEnabled) {
                message = "Choose a backup folder first"
                return@launch
            }
            writeUserBackup(state, announceSuccess = true)
        }
    }

    fun clearBackupFolder() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { userBackupFolderStore.clearFolder() }
            userBackupFolderEnabled = false
            message = "Folder backups off"
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
            loadJob.join()
            runCatching {
                val json = readText(context, uri)
                withContext(Dispatchers.IO) { repository.restoreBackup(json) }
            }.onSuccess { restored ->
                state = restored
                message = "Backup restored"
                writeUserBackup(restored, announceSuccess = false)
            }.onFailure { error ->
                message = error.message ?: "Backup restore failed"
            }
        }
    }

    fun checkForUpdate(context: Context) {
        if (updateInProgress) return
        updateInProgress = true
        viewModelScope.launch {
            try {
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
            } finally {
                updateInProgress = false
            }
        }
    }

    private fun importSharedPacks(
        automatic: Boolean,
        passwordOverride: String? = null,
        savePasswordOnSuccess: Boolean = false,
    ) {
        if (sharedPackImportInProgress) return
        sharedPackImportInProgress = true

        viewModelScope.launch {
            try {
                loadJob.join()
                val password = withContext(Dispatchers.IO) {
                    passwordOverride ?: sharedPackSecretStore.loadPassword()
                }
                if (password.isNullOrEmpty()) {
                    sharedPackPasswordSaved = false
                    if (!automatic) {
                        message = "Save shared pack password first"
                    }
                    return@launch
                }

                val result = try {
                    withContext(Dispatchers.IO) {
                        sharedPackRepository.importBundled(
                            password = password,
                            commitOnErrors = !savePasswordOnSuccess,
                        )
                    }
                } catch (error: Exception) {
                    message = error.message ?: "Shared pack import failed"
                    return@launch
                }

                if (savePasswordOnSuccess && result.errors.isNotEmpty()) {
                    message = result.errors.first()
                    return@launch
                }

                if (savePasswordOnSuccess) {
                    withContext(Dispatchers.IO) {
                        sharedPackSecretStore.savePassword(password)
                    }
                    sharedPackPasswordSaved = true
                }

                state = result.state
                if (result.changed) {
                    writeUserBackup(result.state, announceSuccess = false)
                }
                if (!automatic || result.changed) {
                    message = if (savePasswordOnSuccess) {
                        "Shared pack password saved. ${result.summary}"
                    } else {
                        result.summary
                    }
                }
            } finally {
                sharedPackImportInProgress = false
            }
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

    private suspend fun writeUserBackup(currentState: QuestLogState, announceSuccess: Boolean) {
        if (!userBackupFolderStore.hasFolder()) {
            userBackupFolderEnabled = false
            if (announceSuccess) {
                message = "Choose a backup folder first"
            }
            return
        }

        userBackupInProgress = true
        try {
            runCatching {
                val json = withContext(Dispatchers.IO) { repository.encodeBackup(currentState) }
                withContext(Dispatchers.IO) { userBackupFolderStore.writeBackup(json) }
            }.onSuccess { result ->
                userBackupFolderEnabled = userBackupFolderStore.hasFolder()
                if (announceSuccess) {
                    message = when (result) {
                        UserBackupWriteResult.Disabled -> "Choose a backup folder first"
                        is UserBackupWriteResult.Saved -> "Folder backup saved"
                    }
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                userBackupFolderEnabled = userBackupFolderStore.hasFolder()
                if (announceSuccess || message == null) {
                    message = error.message ?: "Folder backup failed"
                }
            }
        } finally {
            userBackupInProgress = false
        }
    }

}

data class CompletionCelebration(
    val id: String,
    val questTitle: String,
    val xpAwarded: Int,
)
