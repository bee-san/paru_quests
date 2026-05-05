package com.paruchan.questlog.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.paruchan.questlog.BuildConfig
import com.paruchan.questlog.core.LevelProgress
import com.paruchan.questlog.core.Quest
import com.paruchan.questlog.core.QuestCadence
import com.paruchan.questlog.core.QuestGoalType
import com.paruchan.questlog.core.QuestLogEngine
import com.paruchan.questlog.core.QuestLogState
import com.paruchan.questlog.core.QuestProgress
import com.paruchan.questlog.core.QuestPackExporter
import com.paruchan.questlog.data.BundledSharedPackRepository
import com.paruchan.questlog.data.QuestLogRepository
import com.paruchan.questlog.data.SharedPackSecretStore
import com.paruchan.questlog.notification.QuestNotificationPreferences
import com.paruchan.questlog.notification.QuestNotificationScheduler
import com.paruchan.questlog.notification.QuestNotificationSettings
import com.paruchan.questlog.update.GitHubReleaseUpdater
import com.paruchan.questlog.update.InstallResult
import com.paruchan.questlog.update.UpdateCheckResult
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class QuestLogViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = QuestLogRepository(File(application.filesDir, "questlog.json"))
    private val sharedPackRepository = BundledSharedPackRepository(application, repository)
    private val sharedPackSecretStore = SharedPackSecretStore(application)
    private val questNotificationPreferences = QuestNotificationPreferences(application)
    private val engine = QuestLogEngine()
    private val questPackExporter = QuestPackExporter()
    private val updater = GitHubReleaseUpdater(
        repository = BuildConfig.UPDATE_REPOSITORY,
        currentVersion = BuildConfig.VERSION_NAME,
    )
    private var pendingQuestPackExportJson: String? = null

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

    var pendingRestoreUri: Uri? by mutableStateOf(null)
        private set

    var questNotificationSettings: QuestNotificationSettings by mutableStateOf(questNotificationPreferences.load())
        private set

    var questPackDraft: QuestPackDraft by mutableStateOf(QuestPackDraft())
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
            }.onFailure { error ->
                message = error.message ?: "Quest pack import failed"
            }
        }
    }

    fun addGeneratedQuestPack(json: String) {
        viewModelScope.launch {
            loadJob.join()
            runCatching {
                withContext(Dispatchers.IO) { repository.importQuestPack(json) }
            }.onSuccess { result ->
                state = result.state
                message = "Added to quest log: ${result.summary}"
            }.onFailure { error ->
                message = error.message ?: "Quest pack add failed"
            }
        }
    }

    fun importBundledThankYouPack(context: Context) {
        viewModelScope.launch {
            loadJob.join()
            runCatching {
                val json = readAssetText(context, THANK_YOU_PACK_ASSET)
                withContext(Dispatchers.IO) { repository.importQuestPack(json) }
            }.onSuccess { result ->
                state = result.state
                message = "Thank-you pack: ${result.summary}"
            }.onFailure { error ->
                message = error.message ?: "Bundled quest pack import failed"
            }
        }
    }

    val draftQuest: Quest?
        get() = questPackDraft.toQuest()

    fun updateQuestPackDraft(draft: QuestPackDraft) {
        questPackDraft = draft
    }

    fun addDraftQuest() {
        val quest = draftQuest ?: return
        questPackDraft = questPackDraft.copy(
            quests = questPackDraft.quests + quest,
            title = "",
            flavourText = "",
            goalTargetText = "1",
            goalUnit = "completion",
            timerMinutesText = "",
        )
    }

    fun removeDraftQuest(index: Int) {
        questPackDraft = questPackDraft.copy(
            quests = questPackDraft.quests.filterIndexed { itemIndex, _ -> itemIndex != index },
        )
    }

    fun questPackDraftJson(): String =
        questPackExporter.encodePack(questPackDraft.packName, questPackDraft.quests)

    fun prepareQuestPackExport(json: String) {
        pendingQuestPackExportJson = json
    }

    fun consumePendingQuestPackExport(): String? {
        val json = pendingQuestPackExportJson
        pendingQuestPackExportJson = null
        return json
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

    fun exportQuestPack(context: Context, uri: Uri, json: String) {
        viewModelScope.launch {
            runCatching {
                writeText(context, uri, json)
            }.onSuccess {
                message = "Quest pack exported"
            }.onFailure { error ->
                message = error.message ?: "Quest pack export failed"
            }
        }
    }

    fun shareQuestPack(context: Context, json: String) {
        viewModelScope.launch {
            runCatching {
                val file = withContext(Dispatchers.IO) {
                    val dir = File(context.cacheDir, "quest-packs")
                    dir.mkdirs()
                    File(dir, "paruchan-quest-pack.json").also { it.writeText(json) }
                }
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                )
                val send = Intent(Intent.ACTION_SEND)
                    .setType("application/json")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(Intent.createChooser(send, "Share quest pack"))
                "Share sheet opened"
            }.onSuccess { shareMessage ->
                message = shareMessage
            }.onFailure { error ->
                message = error.message ?: "Quest pack share failed"
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
            loadJob.join()
            runCatching {
                val json = readText(context, uri)
                withContext(Dispatchers.IO) { repository.restoreBackup(json) }
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

    private suspend fun readAssetText(context: Context, path: String): String = withContext(Dispatchers.IO) {
        context.assets.open(path)
            .bufferedReader()
            .use { it.readText() }
    }

    private suspend fun writeText(context: Context, uri: Uri, text: String) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri, "wt")
            ?.bufferedWriter()
            ?.use { it.write(text) }
            ?: error("Could not write selected file")
    }

    private companion object {
        const val THANK_YOU_PACK_ASSET = "quest-packs/thank-you-paruchan.json"
    }
}

data class CompletionCelebration(
    val id: String,
    val questTitle: String,
    val xpAwarded: Int,
)

data class QuestPackDraft(
    val packName: String = "Paruchan Quest Pack",
    val title: String = "",
    val flavourText: String = "",
    val xpText: String = "50",
    val category: String = "Paruchan",
    val icon: String = "star",
    val cadence: QuestCadence = QuestCadence.Once,
    val goalType: QuestGoalType = QuestGoalType.Completion,
    val goalTargetText: String = "1",
    val goalUnit: String = "completion",
    val timerMinutesText: String = "",
    val quests: List<Quest> = emptyList(),
) {
    fun toQuest(): Quest? {
        val xp = xpText.toIntOrNull()?.coerceAtLeast(0) ?: return null
        val goalTarget = goalTargetText.toIntOrNull()?.coerceAtLeast(1) ?: return null
        val timerMinutes = timerMinutesText.toIntOrNull()?.coerceIn(1, 24 * 60)
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) return null
        return Quest(
            title = cleanTitle,
            flavourText = flavourText.trim(),
            xp = xp,
            category = category.trim().ifBlank { "General" },
            icon = icon.trim().ifBlank { "star" },
            repeatable = cadence == QuestCadence.Repeatable,
            cadence = cadence.wireName,
            goalType = goalType.wireName,
            goalTarget = goalTarget,
            goalUnit = when (goalType) {
                QuestGoalType.Counter -> goalUnit.trim().ifBlank { "unit" }
                QuestGoalType.Timer -> "minute"
                QuestGoalType.Completion -> goalUnit.trim().ifBlank { "completion" }
            },
            timerMinutes = if (goalType == QuestGoalType.Completion) timerMinutes else null,
        )
    }
}
