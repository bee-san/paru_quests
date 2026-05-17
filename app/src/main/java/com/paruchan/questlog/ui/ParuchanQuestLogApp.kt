package com.paruchan.questlog.ui

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ImportExport
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Shapes
import androidx.compose.material3.Switch
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paruchan.questlog.R
import com.paruchan.questlog.BuildConfig
import com.paruchan.questlog.core.Completion
import com.paruchan.questlog.core.JournalEntry
import com.paruchan.questlog.core.LevelProgress
import com.paruchan.questlog.core.Quest
import com.paruchan.questlog.core.QuestCadence
import com.paruchan.questlog.core.QuestGoalType
import com.paruchan.questlog.core.QuestProgress
import com.paruchan.questlog.notification.QuestNotificationSettings
import kotlinx.coroutines.delay

private val DeepPlum = Color(0xFF23143F)
private val Plum = Color(0xFF5B3B88)
private val Lilac = Color(0xFFE7D7EE)
private val Parchment = Color(0xFFFFF8EC)
private val ParchmentDeep = Color(0xFFF3E5D2)
private val Ink = Color(0xFF251B3E)
private val MutedInk = Color(0xFF6F5D7F)
private val Gold = Color(0xFFE8C46D)
private val GoldDeep = Color(0xFFB88931)
private val Rose = Color(0xFFDCA6A6)

private val ParuchanScheme = lightColorScheme(
    primary = Plum,
    onPrimary = Color.White,
    secondary = GoldDeep,
    tertiary = Rose,
    background = DeepPlum,
    surface = Parchment,
    surfaceVariant = ParchmentDeep,
    onBackground = Parchment,
    onSurface = Ink,
)

@Composable
fun ParuchanQuestLogApp(
    viewModel: QuestLogViewModel,
    onImportQuestPack: () -> Unit,
    onExportBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    onChooseBackupFolder: () -> Unit,
    onEnableQuestNotifications: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var screen by rememberSaveable { mutableStateOf(Screen.Home) }

    LaunchedEffect(viewModel.message) {
        val message = viewModel.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Short)
        viewModel.clearMessage()
    }

    LaunchedEffect(Unit) {
        viewModel.autoImportSharedPacks()
    }

    MaterialTheme(
        colorScheme = ParuchanScheme,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(4.dp),
            small = RoundedCornerShape(6.dp),
            medium = RoundedCornerShape(8.dp),
            large = RoundedCornerShape(8.dp),
            extraLarge = RoundedCornerShape(8.dp),
        ),
    ) {
        viewModel.pendingRestoreUri?.let {
            AlertDialog(
                onDismissRequest = viewModel::cancelRestore,
                title = { DisplayText("Restore backup", MaterialTheme.typography.titleLarge.fontSize) },
                text = { Text("This replaces the current quest log.") },
                confirmButton = {
                    Button(onClick = { viewModel.confirmRestore(context) }) {
                        Text("Restore")
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::cancelRestore) {
                        Text("Cancel")
                    }
                },
            )
        }

        Scaffold(
            containerColor = DeepPlum,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                NavigationBar(containerColor = DeepPlum, tonalElevation = 0.dp) {
                    Screen.entries.filter { it.showInBottomNav }.forEach { item ->
                        NavigationBarItem(
                            selected = screen == item,
                            onClick = { screen = item },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label, maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Gold,
                                selectedTextColor = Gold,
                                indicatorColor = DeepPlum,
                                unselectedIconColor = Lilac.copy(alpha = 0.72f),
                                unselectedTextColor = Lilac.copy(alpha = 0.72f),
                            ),
                        )
                    }
                }
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF342052), DeepPlum, Color(0xFF1A1031)),
                        )
                    )
                    .padding(padding),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                ) {
                    FantasyHeader(screen = screen)
                    ParchmentPanel(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        when (screen) {
                            Screen.Home -> DashboardScreen(
                                state = DashboardState(
                                    progress = viewModel.progress,
                                    quests = viewModel.state.quests,
                                    availableQuests = viewModel.state.quests.filter { viewModel.canComplete(it) },
                                ),
                                actions = DashboardActions(
                                    canComplete = viewModel::canComplete,
                                    progressFor = viewModel::progressFor,
                                    onComplete = { questId, amount -> viewModel.completeQuest(questId, amount) },
                                    onShowHistory = { screen = Screen.History },
                                    onShowFiles = { screen = Screen.Files },
                                    onShowSettings = { screen = Screen.Settings },
                                ),
                            )

                            Screen.History -> HistoryScreen(
                                completions = viewModel.state.completions,
                                quests = viewModel.state.quests,
                            )

                            Screen.Diary -> DiaryScreen(
                                diaryUiState = viewModel.diaryUiState,
                                onSave = viewModel::saveJournalEntry,
                            )

                            Screen.Files -> ImportExportScreen(
                                state = ImportExportState(
                                    userBackupFolderEnabled = viewModel.userBackupFolderEnabled,
                                    userBackupInProgress = viewModel.userBackupInProgress,
                                ),
                                actions = ImportExportActions(
                                    onImportQuestPack = onImportQuestPack,
                                    onExportBackup = onExportBackup,
                                    onRestoreBackup = onRestoreBackup,
                                    onChooseBackupFolder = onChooseBackupFolder,
                                    onBackupNow = viewModel::backupNow,
                                    onClearBackupFolder = viewModel::clearBackupFolder,
                                ),
                            )

                            Screen.Settings -> SettingsScreen(
                                state = SettingsState(
                                    updateInProgress = viewModel.updateInProgress,
                                    sharedPackImportInProgress = viewModel.sharedPackImportInProgress,
                                    sharedPackPasswordSaved = viewModel.sharedPackPasswordSaved,
                                    questNotificationSettings = viewModel.questNotificationSettings,
                                ),
                                actions = SettingsActions(
                                    onCheckForUpdate = { viewModel.checkForUpdate(context) },
                                    onShowFiles = { screen = Screen.Files },
                                    notifications = ReminderActions(
                                        onTimeChange = viewModel::updateQuestNotificationTime,
                                        onEnable = onEnableQuestNotifications,
                                        onDisable = viewModel::disableQuestNotifications,
                                    ),
                                    sharedPacks = SharedPackActions(
                                        onSavePassword = viewModel::saveSharedPackPassword,
                                        onClearPassword = viewModel::clearSharedPackPassword,
                                        onImport = viewModel::importSharedPacks,
                                    ),
                                ),
                            )
                        }
                    }
                }
                viewModel.completionCelebration?.let { celebration ->
                    CompletionCelebrationOverlay(
                        celebration = celebration,
                        onDismiss = viewModel::clearCompletionCelebration,
                    )
                }
            }
        }
    }
}

@Composable
private fun CompletionCelebrationOverlay(
    celebration: CompletionCelebration,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hapticFeedback = LocalHapticFeedback.current
    var entered by remember(celebration.id) { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.92f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "Completion celebration scale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "Completion celebration alpha",
    )
    val sparkleTransition = rememberInfiniteTransition(label = "Completion sparkles")
    val sparklePhase by sparkleTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "Completion sparkle phase",
    )

    LaunchedEffect(celebration.id) {
        entered = true
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        delay(1_600)
        onDismiss()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 360.dp)
                .heightIn(min = 176.dp)
                .graphicsLayer {
                    this.alpha = alpha
                    scaleX = scale
                    scaleY = scale
                }
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFFFFFCF5), Color(0xFFF4E5F4), Color(0xFFFFF5DA)),
                    )
                )
                .border(BorderStroke(2.dp, Gold), RoundedCornerShape(8.dp))
                .padding(18.dp),
            contentAlignment = Alignment.Center,
        ) {
            CompletionConfetti(phase = sparklePhase, modifier = Modifier.matchParentSize())
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Mascot(Modifier.size(58.dp))
                DisplayText("Goal complete", 27.sp, Ink, FontWeight.Bold, maxLines = 1)
                Text(
                    text = celebration.questTitle,
                    color = MutedInk,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                DisplayText("+${celebration.xpAwarded} XP", 30.sp, Plum, FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

@Composable
private fun CompletionConfetti(phase: Float, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        repeat(16) { index ->
            val alignment = when (index % 8) {
                0 -> Alignment.TopStart
                1 -> Alignment.TopCenter
                2 -> Alignment.TopEnd
                3 -> Alignment.CenterEnd
                4 -> Alignment.BottomEnd
                5 -> Alignment.BottomCenter
                6 -> Alignment.BottomStart
                else -> Alignment.CenterStart
            }
            val color = when (index % 3) {
                0 -> Gold
                1 -> Lilac
                else -> Rose
            }
            val size = (5 + (index % 4) * 2).dp
            val xDrift = (((index % 5) - 2) * 5f * (0.4f + phase)).dp
            val yDrift = ((phase * 18f) - 9f + (index % 3) * 2f).dp
            val dotAlpha = (0.42f + (index % 4) * 0.12f + (1f - phase) * 0.12f).coerceIn(0.32f, 0.88f)
            Box(
                modifier = Modifier
                    .align(alignment)
                    .offset(x = xDrift, y = yDrift)
                    .size(size)
                    .clip(CircleShape)
                    .background(color.copy(alpha = dotAlpha)),
            )
        }
    }
}

@Composable
private fun FantasyHeader(screen: Screen) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 132.dp),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 8.dp, top = 18.dp, end = 118.dp),
        ) {
            DisplayText(
                text = screen.title,
                size = if (screen == Screen.Home) 32.sp else 42.sp,
                color = Gold,
                weight = FontWeight.Bold,
                maxLines = 2,
            )
            if (screen == Screen.Home) {
                DisplayText(
                    text = "Quest Log",
                    size = 27.sp,
                    color = Gold,
                    weight = FontWeight.SemiBold,
                    maxLines = 2,
                )
            }
            Text(
                text = "tiny wins, steady progress",
                color = Lilac.copy(alpha = 0.78f),
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 18.sp,
            )
        }
        Mascot(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 22.dp)
                .size(92.dp),
        )
    }
}

@Composable
private fun ParchmentPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 8.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(Parchment, Color(0xFFFFFBF5), ParchmentDeep),
                )
            )
            .border(BorderStroke(1.dp, Gold.copy(alpha = 0.55f)), RoundedCornerShape(8.dp))
            .padding(14.dp),
    ) {
        content()
    }
}

@Composable
private fun DashboardScreen(
    state: DashboardState,
    actions: DashboardActions,
) {
    val dailyCount = state.quests.count { QuestCadence.from(it) == QuestCadence.Daily && actions.canComplete(it) }
    val goalCount = state.quests.count { (QuestGoalType.from(it) != QuestGoalType.Completion || it.goalTarget > 1) && !it.archived }
    val spotlightQuests = state.availableQuests.sortedWith(
        compareByDescending<Quest> { QuestCadence.from(it) == QuestCadence.Daily }
            .thenByDescending { QuestGoalType.from(it) == QuestGoalType.Timer }
            .thenByDescending { it.timerMinutes != null }
            .thenByDescending { it.goalTarget > 1 }
            .thenBy { QuestGoalType.from(it) == QuestGoalType.Counter }
            .thenBy { it.title },
    )

    DashboardContent(
        progress = state.progress,
        questCount = state.quests.size,
        availableQuestCount = state.availableQuests.size,
        dailyCount = dailyCount,
        goalCount = goalCount,
        spotlightQuests = spotlightQuests,
        actions = actions,
    )
}

private data class DashboardState(
    val progress: LevelProgress,
    val quests: List<Quest>,
    val availableQuests: List<Quest>,
)

private data class DashboardActions(
    val canComplete: (Quest) -> Boolean,
    val progressFor: (Quest) -> QuestProgress,
    val onComplete: (String, Int) -> Unit,
    val onShowHistory: () -> Unit,
    val onShowFiles: () -> Unit,
    val onShowSettings: () -> Unit,
)

@Composable
private fun DashboardContent(
    progress: LevelProgress,
    questCount: Int,
    availableQuestCount: Int,
    dailyCount: Int,
    goalCount: Int,
    spotlightQuests: List<Quest>,
    actions: DashboardActions,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 18.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircleQuestIcon(modifier = Modifier.size(82.dp), icon = "cat")
                Spacer(Modifier.width(14.dp))
                Column {
                    DisplayText("Good evening, Star", 28.sp, Ink, FontWeight.Bold)
                    Text(
                        text = "Let's make today magical",
                        color = MutedInk,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }

        item {
            LevelCard(progress = progress)
        }

        item {
            DashboardStats(availableQuestCount, dailyCount, goalCount)
        }

        item {
            SectionHeader("Quests")
        }

        if (spotlightQuests.isEmpty()) {
            item {
                EmptyStateCard(
                    if (questCount == 0) {
                        "Import a quest pack to begin."
                    } else {
                        "No quests are available right now."
                    }
                )
            }
        } else {
            items(spotlightQuests, key = { it.id }) { quest ->
                QuestListCard(
                    quest = quest,
                    progress = actions.progressFor(quest),
                    completed = false,
                    enabled = actions.canComplete(quest),
                    onComplete = { amount -> actions.onComplete(quest.id, amount) },
                )
            }
        }

        item {
            SectionHeader("Quick Session")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                QuickAction("Log", Icons.Outlined.History, actions.onShowHistory, Modifier.weight(1f))
                QuickAction("Files", Icons.Outlined.ImportExport, actions.onShowFiles, Modifier.weight(1f))
                QuickAction("Update", Icons.Outlined.SystemUpdateAlt, actions.onShowSettings, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DashboardStats(availableQuestCount: Int, dailyCount: Int, goalCount: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        StatCard("Active", availableQuestCount.toString(), "Quests", Modifier.weight(1f))
        StatCard("Daily", dailyCount.toString(), "Open today", Modifier.weight(1f))
        StatCard("Goals", goalCount.toString(), "Targets", Modifier.weight(1f))
    }
}

@Composable
private fun LevelCard(progress: LevelProgress) {
    val animatedFraction by animateFloatAsState(
        targetValue = progress.fraction,
        label = "Level progress",
    )
    val nextLevel = progress.next
    val xpSpan = nextLevel?.let { (it.xpRequired - progress.current.xpRequired).coerceAtLeast(1) }
    val progressLabel = when {
        nextLevel == null -> "All current levels complete"
        progress.xpIntoLevel == 0 && progress.totalXp > 0 ->
            "Level ${progress.current.level} reached. ${nextLevel.xpRequired - progress.totalXp} XP to ${nextLevel.title}"
        xpSpan != null ->
            "${progress.xpIntoLevel.coerceAtLeast(0)} / $xpSpan XP toward ${nextLevel.title}"
        else -> "All current levels complete"
    }

    Card(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Gold.copy(alpha = 0.65f)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0E3EF)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LevelBadge(progress.current.level)
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    DisplayText(progress.current.title, 28.sp, Ink, FontWeight.Bold, maxLines = 2)
                    Text("Total XP", color = MutedInk, style = MaterialTheme.typography.bodyMedium)
                    DisplayText("${progress.totalXp} XP", 33.sp, Plum, FontWeight.Bold)
                }
                Mascot(Modifier.size(64.dp))
            }
            LinearProgressIndicator(
                progress = { animatedFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(8.dp)),
                color = Plum,
                trackColor = Plum.copy(alpha = 0.18f),
                strokeCap = StrokeCap.Round,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Level ${progress.current.level}", color = Ink, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = progress.next?.let { "Level ${it.level}" } ?: "Max level",
                    color = Ink,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                text = progressLabel,
                color = Plum,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, subtitle: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.heightIn(min = 104.dp),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Gold.copy(alpha = 0.35f)),
        colors = CardDefaults.cardColors(containerColor = Parchment.copy(alpha = 0.96f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            DisplayText(value, 24.sp, Ink, FontWeight.Bold, maxLines = 1)
            Text(title, color = Ink, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
            Text(subtitle, color = MutedInk, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun QuestListCard(
    quest: Quest,
    progress: QuestProgress,
    completed: Boolean,
    enabled: Boolean,
    onComplete: (Int) -> Unit,
) {
    val goalType = progress.goalType
    Card(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Gold.copy(alpha = 0.28f)),
        colors = CardDefaults.cardColors(containerColor = Parchment.copy(alpha = 0.98f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            QuestListCardHeader(quest = quest, progress = progress, goalType = goalType)
            QuestActionRow(
                quest = quest,
                progress = progress,
                completed = completed,
                enabled = enabled,
                onComplete = onComplete,
            )
        }
    }
}

@Composable
private fun QuestListCardHeader(
    quest: Quest,
    progress: QuestProgress,
    goalType: QuestGoalType,
) {
    Row(verticalAlignment = Alignment.Top) {
        CircleQuestIcon(modifier = Modifier.size(70.dp), icon = quest.icon)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            DisplayText(
                quest.title,
                24.sp,
                Ink,
                FontWeight.Bold,
                maxLines = 3,
            )
            QuestFlavorText(quest.flavourText)
            QuestProgressDetails(quest = quest, progress = progress, goalType = goalType)
        }
    }
}

@Composable
private fun QuestFlavorText(flavourText: String) {
    if (flavourText.isBlank()) return
    Text(
        flavourText,
        color = MutedInk,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 4,
    )
}

@Composable
private fun QuestProgressDetails(
    quest: Quest,
    progress: QuestProgress,
    goalType: QuestGoalType,
) {
    if (progress.hasGoal) {
        Spacer(Modifier.height(8.dp))
        GoalProgressBar(progress = progress)
    }
    quest.timerMinutes?.takeIf { goalType != QuestGoalType.Timer }?.let { minutes ->
        Spacer(Modifier.height(8.dp))
        QuestTimer(questId = quest.id, minutes = minutes)
    }
}

@Composable
private fun QuestActionRow(
    quest: Quest,
    progress: QuestProgress,
    completed: Boolean,
    enabled: Boolean,
    onComplete: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QuestXpSummary(quest.xp, progress.hasGoal, Modifier.weight(1f))
        if (progress.hasGoal) {
            Text("on goal", color = MutedInk, style = MaterialTheme.typography.bodySmall)
        }
        QuestCompletionControls(
            quest = quest,
            progress = progress,
            completed = completed,
            enabled = enabled,
            onComplete = onComplete,
        )
    }
}

@Composable
private fun QuestXpSummary(xp: Int, hasGoal: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        DisplayText(
            "$xp XP",
            22.sp,
            Plum,
            FontWeight.SemiBold,
            maxLines = 2,
        )
        if (hasGoal) {
            Text("XP awarded when the goal completes", color = MutedInk, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun QuestCompletionControls(
    quest: Quest,
    progress: QuestProgress,
    completed: Boolean,
    enabled: Boolean,
    onComplete: (Int) -> Unit,
) {
    when (progress.goalType) {
        QuestGoalType.Counter -> CounterIncrementControls(
            unit = progress.unit,
            enabled = enabled,
            onLog = onComplete,
        )

        QuestGoalType.Timer -> TimerLogControls(
            questId = quest.id,
            enabled = enabled,
            onLog = onComplete,
        )

        QuestGoalType.Completion -> CompleteButton(
            enabled = enabled,
            completed = completed && !quest.repeatable,
            onClick = { onComplete(1) },
        )
    }
}

@Composable
private fun GoalProgressBar(progress: QuestProgress) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LinearProgressIndicator(
            progress = { progress.fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(8.dp)),
            color = Plum,
            trackColor = Plum.copy(alpha = 0.16f),
            strokeCap = StrokeCap.Round,
        )
        Text(
            text = if (progress.isComplete) {
                "Goal complete"
            } else {
                "${progress.remaining} ${unitLabel(progress.unit, progress.remaining)} to go"
            },
            color = MutedInk,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun QuestTimer(questId: String, minutes: Int) {
    val totalSeconds = minutes.coerceAtLeast(1) * 60
    var remainingSeconds by rememberSaveable(questId, "timerRemaining") { mutableStateOf(totalSeconds) }
    var running by rememberSaveable(questId, "timerRunning") { mutableStateOf(false) }

    LaunchedEffect(running, remainingSeconds) {
        if (running && remainingSeconds > 0) {
            delay(1_000)
            remainingSeconds -= 1
        }
        if (remainingSeconds == 0) running = false
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.42f))
            .border(BorderStroke(1.dp, Gold.copy(alpha = 0.35f)), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Outlined.Timer, contentDescription = null, tint = Plum, modifier = Modifier.size(20.dp))
        Text(formatTimer(remainingSeconds), color = Ink, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        TimerButton(
            icon = if (running) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
            label = if (running) "Pause timer" else "Start timer",
            onClick = {
                if (remainingSeconds == 0) remainingSeconds = totalSeconds
                running = !running
            },
        )
        TimerButton(
            icon = Icons.Outlined.Replay,
            label = "Reset timer",
            onClick = {
                running = false
                remainingSeconds = totalSeconds
            },
        )
    }
}

@Composable
private fun TimerButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Plum)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = Gold, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun CounterIncrementControls(
    unit: String,
    enabled: Boolean,
    onLog: (Int) -> Unit,
) {
    Button(
        onClick = { onLog(1) },
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Plum, contentColor = Gold),
        border = BorderStroke(1.dp, Gold),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Icon(Icons.Outlined.AddCircle, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("+1 ${unitLabel(unit, 1)}", maxLines = 1)
    }
}

@Composable
private fun TimerLogControls(
    questId: String,
    enabled: Boolean,
    onLog: (Int) -> Unit,
) {
    var minutesText by rememberSaveable(questId, "timerMinutesLogged") { mutableStateOf("10") }
    val minutes = minutesText.toIntOrNull()?.takeIf { it > 0 }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = minutesText,
            onValueChange = { minutesText = it.filter(Char::isDigit).take(4) },
            modifier = Modifier.widthIn(min = 82.dp, max = 106.dp),
            label = { Text("min") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(8.dp),
        )
        Button(
            onClick = { minutes?.let(onLog) },
            enabled = enabled && minutes != null && minutes > 0,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Plum, contentColor = Gold),
            border = BorderStroke(1.dp, Gold),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Icon(Icons.Outlined.Timer, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Log", maxLines = 1)
        }
    }
}

@Composable
private fun CompleteButton(enabled: Boolean, completed: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (enabled) Plum else Plum.copy(alpha = 0.35f))
            .border(BorderStroke(1.dp, Gold), RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.CheckCircle,
            contentDescription = if (completed) "Completed" else "Complete quest",
            tint = Gold,
            modifier = Modifier.size(30.dp),
        )
    }
}

@Composable
private fun HistoryScreen(
    completions: List<Completion>,
    quests: List<Quest>,
) {
    if (completions.isEmpty()) {
        EmptyStateCard("No completions yet.")
        return
    }

    val questsById = quests.associateBy { it.id }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 18.dp),
    ) {
        item { SectionHeader("Quest History") }
        items(completions.asReversed(), key = { it.id }) { completion ->
            val quest = questsById[completion.questId]
            Card(
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Gold.copy(alpha = 0.25f)),
                colors = CardDefaults.cardColors(containerColor = Parchment),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    CircleQuestIcon(modifier = Modifier.size(54.dp), icon = quest?.icon.orEmpty())
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        DisplayText(quest?.title ?: "Unknown quest", 21.sp, Ink, FontWeight.Bold, maxLines = 3)
                        Text(shortDate(completion.completedAt), color = MutedInk, style = MaterialTheme.typography.bodySmall)
                        DisplayText("+${completion.xpAwarded} XP", 21.sp, Plum, FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun DiaryScreen(
    diaryUiState: DiaryUiState,
    onSave: (String, String, String) -> Unit,
) {
    val todayEntry = diaryUiState.todayEntry
    var happyText by rememberSaveable(todayEntry.localDate, "happyText") { mutableStateOf(todayEntry.happyText) }
    var gratefulText by rememberSaveable(todayEntry.localDate, "gratefulText") { mutableStateOf(todayEntry.gratefulText) }
    var favoriteMemoryText by rememberSaveable(todayEntry.localDate, "favoriteMemoryText") {
        mutableStateOf(todayEntry.favoriteMemoryText)
    }

    LaunchedEffect(todayEntry.localDate, todayEntry.updatedAt) {
        happyText = todayEntry.happyText
        gratefulText = todayEntry.gratefulText
        favoriteMemoryText = todayEntry.favoriteMemoryText
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
        item {
            SectionHeader("Daily Diary")
            ReflectionCard(diaryUiState.dailyReflection)
        }
        item {
            TodayDiaryCard(
                form = DiaryFormState(
                    entry = todayEntry,
                    happyText = happyText,
                    gratefulText = gratefulText,
                    favoriteMemoryText = favoriteMemoryText,
                    saving = diaryUiState.saving,
                ),
                actions = DiaryFormActions(
                    onHappyChange = { happyText = it },
                    onGratefulChange = { gratefulText = it },
                    onFavoriteMemoryChange = { favoriteMemoryText = it },
                    onSave = { onSave(happyText, gratefulText, favoriteMemoryText) },
                ),
            )
        }
        item {
            SectionHeader("Recent Entries")
        }
        if (diaryUiState.recentEntries.isEmpty()) {
            item { EmptyStateCard("No diary entries yet.") }
        } else {
            items(diaryUiState.recentEntries, key = { it.id.ifBlank { it.localDate } }) { entry ->
                JournalEntryCard(entry)
            }
        }
    }
}

@Composable
private fun ReflectionCard(reflection: String?) {
    Card(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Gold.copy(alpha = 0.35f)),
        colors = CardDefaults.cardColors(containerColor = Parchment),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DisplayText("Today remembers", 22.sp, Ink, FontWeight.Bold, maxLines = 2)
            Text(
                reflection ?: "Paruchan will remember something sweet here after a few diary days.",
                color = if (reflection == null) MutedInk else Ink,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun TodayDiaryCard(
    form: DiaryFormState,
    actions: DiaryFormActions,
) {
    val draftState = form.draftState()

    Card(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Gold.copy(alpha = 0.35f)),
        colors = CardDefaults.cardColors(containerColor = Parchment),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DisplayText("Today's entry", 24.sp, Ink, FontWeight.Bold, maxLines = 2)
            DiaryTextField(
                value = form.happyText,
                onValueChange = actions.onHappyChange,
                label = "One thing that made Paruchan happy today",
            )
            DiaryTextField(
                value = form.gratefulText,
                onValueChange = actions.onGratefulChange,
                label = "One thing Paruchan is grateful for today",
            )
            DiaryTextField(
                value = form.favoriteMemoryText,
                onValueChange = actions.onFavoriteMemoryChange,
                label = "Tell me one of your favourite memories",
            )
            Text(draftState.rewardStatus, color = Plum, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            DiarySaveButton(form = form, draftState = draftState, onSave = actions.onSave)
        }
    }
}

@Composable
private fun DiarySaveButton(
    form: DiaryFormState,
    draftState: DiaryDraftState,
    onSave: () -> Unit,
) {
    Button(
        onClick = onSave,
        enabled = !form.saving && (draftState.hasAnyDraft || form.entry.createdAt.isNotBlank()),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Plum, contentColor = Gold),
        border = BorderStroke(1.dp, Gold),
    ) {
        if (form.saving) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Gold)
        } else {
            Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(if (form.entry.createdAt.isBlank()) "Save diary" else "Update diary", maxLines = 1)
    }
}

private fun DiaryFormState.draftState(): DiaryDraftState {
    val hasAnyDraft = happyText.isNotBlank() ||
        gratefulText.isNotBlank() ||
        favoriteMemoryText.isNotBlank()
    val completeDraft = happyText.trim().isNotBlank() &&
        gratefulText.trim().isNotBlank() &&
        favoriteMemoryText.trim().isNotBlank()
    return DiaryDraftState(
        hasAnyDraft = hasAnyDraft,
        rewardStatus = when {
            entry.xpAwarded == 10 -> "Diary reward claimed: +10 XP"
            completeDraft -> "Diary reward waiting"
            hasAnyDraft -> "Saved as a tiny draft"
            else -> "Diary reward waiting"
        },
    )
}

private data class DiaryFormState(
    val entry: JournalEntry,
    val happyText: String,
    val gratefulText: String,
    val favoriteMemoryText: String,
    val saving: Boolean,
)

private data class DiaryFormActions(
    val onHappyChange: (String) -> Unit,
    val onGratefulChange: (String) -> Unit,
    val onFavoriteMemoryChange: (String) -> Unit,
    val onSave: () -> Unit,
)

private data class DiaryDraftState(
    val hasAnyDraft: Boolean,
    val rewardStatus: String,
)

@Composable
private fun DiaryTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp),
        label = { Text(label) },
        minLines = 2,
        maxLines = 4,
        shape = RoundedCornerShape(8.dp),
    )
}

@Composable
private fun JournalEntryCard(entry: JournalEntry) {
    val preview = entry.favoriteMemoryText.ifBlank { entry.happyText }.ifBlank { entry.gratefulText }
    Card(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Gold.copy(alpha = 0.25f)),
        colors = CardDefaults.cardColors(containerColor = Parchment),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            CircleQuestIcon(modifier = Modifier.size(48.dp), icon = "star")
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DisplayText(entry.localDate, 20.sp, Ink, FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
                    if (entry.xpAwarded > 0) {
                        Text("+${entry.xpAwarded} XP", color = Plum, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    preview.ifBlank { "Tiny draft" },
                    color = MutedInk,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ImportExportScreen(
    state: ImportExportState,
    actions: ImportExportActions,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
        item {
            SectionHeader("Quest Library")
            Text(
                text = "Move quest packs and backups through Android's file picker.",
                color = MutedInk,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        item {
            OrnateActionButton(
                icon = Icons.Outlined.FileUpload,
                title = "Import quest pack",
                detail = "Make imported quests current; close older quests",
                onClick = actions.onImportQuestPack,
            )
        }
        item {
            OrnateActionButton(
                icon = Icons.Outlined.FileDownload,
                title = "Export backup",
                detail = "Save quests, completions, levels, and diary",
                onClick = actions.onExportBackup,
            )
        }
        item {
            OrnateActionButton(
                icon = Icons.Outlined.Restore,
                title = "Restore backup",
                detail = "Replace this device's quest log",
                onClick = actions.onRestoreBackup,
            )
        }
        item {
            UserBackupFolderCard(
                enabled = state.userBackupFolderEnabled,
                inProgress = state.userBackupInProgress,
                onChooseBackupFolder = actions.onChooseBackupFolder,
                onBackupNow = actions.onBackupNow,
                onClearBackupFolder = actions.onClearBackupFolder,
            )
        }
    }
}

private data class ImportExportState(
    val userBackupFolderEnabled: Boolean,
    val userBackupInProgress: Boolean,
)

private data class ImportExportActions(
    val onImportQuestPack: () -> Unit,
    val onExportBackup: () -> Unit,
    val onRestoreBackup: () -> Unit,
    val onChooseBackupFolder: () -> Unit,
    val onBackupNow: () -> Unit,
    val onClearBackupFolder: () -> Unit,
)

@Composable
private fun UserBackupFolderCard(
    enabled: Boolean,
    inProgress: Boolean,
    onChooseBackupFolder: () -> Unit,
    onBackupNow: () -> Unit,
    onClearBackupFolder: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Gold.copy(alpha = 0.35f)),
        colors = CardDefaults.cardColors(containerColor = Parchment),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.FileDownload, contentDescription = null, tint = Plum, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    DisplayText("Folder backups", 24.sp, Ink, FontWeight.Bold, maxLines = 1)
                    Text(
                        if (enabled) {
                            "Saving latest plus 10 daily files"
                        } else {
                            "Choose a folder that survives uninstall"
                        },
                        color = MutedInk,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Button(
                onClick = onChooseBackupFolder,
                enabled = !inProgress,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Plum, contentColor = Gold),
                border = BorderStroke(1.dp, Gold),
            ) {
                Icon(Icons.Outlined.ImportExport, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (enabled) "Change folder" else "Choose folder", maxLines = 1)
            }

            if (enabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onBackupNow,
                        enabled = !inProgress,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GoldDeep, contentColor = Color.White),
                    ) {
                        if (inProgress) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Icon(Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text("Backup now", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = onClearBackupFolder,
                        enabled = !inProgress,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Plum.copy(alpha = 0.42f)),
                    ) {
                        Text("Turn off", maxLines = 1, color = Plum)
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    numeric: Boolean = false,
    keyboardType: KeyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp),
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation,
        shape = RoundedCornerShape(8.dp),
    )
}

@Composable
private fun SettingsScreen(
    state: SettingsState,
    actions: SettingsActions,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
        item {
            SectionHeader("Settings")
            VersionCard()
        }
        item {
            CheckForUpdateButton(
                inProgress = state.updateInProgress,
                onClick = actions.onCheckForUpdate,
            )
        }
        item {
            OrnateActionButton(
                icon = Icons.Outlined.ImportExport,
                title = "Files",
                detail = "Import quest packs, export backups, and restore",
                onClick = actions.onShowFiles,
            )
        }
        item {
            QuestReminderSettingsCard(
                settings = state.questNotificationSettings,
                onTimeChange = actions.notifications.onTimeChange,
                onEnable = actions.notifications.onEnable,
                onDisable = actions.notifications.onDisable,
            )
        }
        item {
            SharedPackSettingsCard(
                passwordSaved = state.sharedPackPasswordSaved,
                importInProgress = state.sharedPackImportInProgress,
                actions = actions.sharedPacks,
            )
        }
    }
}

private data class SettingsState(
    val updateInProgress: Boolean,
    val sharedPackImportInProgress: Boolean,
    val sharedPackPasswordSaved: Boolean,
    val questNotificationSettings: QuestNotificationSettings,
)

private data class SettingsActions(
    val onCheckForUpdate: () -> Unit,
    val onShowFiles: () -> Unit,
    val notifications: ReminderActions,
    val sharedPacks: SharedPackActions,
)

private data class ReminderActions(
    val onTimeChange: (Int, Int) -> Unit,
    val onEnable: () -> Unit,
    val onDisable: () -> Unit,
)

private data class SharedPackActions(
    val onSavePassword: (String) -> Unit,
    val onClearPassword: () -> Unit,
    val onImport: () -> Unit,
)

@Composable
private fun VersionCard() {
    Card(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Gold.copy(alpha = 0.35f)),
        colors = CardDefaults.cardColors(containerColor = Parchment),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            LevelBadge(7)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                DisplayText("Version ${BuildConfig.VERSION_NAME}", 24.sp, Ink, FontWeight.Bold, maxLines = 2)
                Text(BuildConfig.UPDATE_REPOSITORY, color = MutedInk, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun CheckForUpdateButton(inProgress: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !inProgress,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Plum, contentColor = Gold),
        border = BorderStroke(1.dp, Gold),
    ) {
        if (inProgress) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = Gold)
        } else {
            Icon(Icons.Outlined.SystemUpdateAlt, contentDescription = null, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(10.dp))
        DisplayText("Check for update", 24.sp, Gold, FontWeight.Bold, maxLines = 2, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SharedPackSettingsCard(
    passwordSaved: Boolean,
    importInProgress: Boolean,
    actions: SharedPackActions,
) {
    var sharedPackPassword by remember { mutableStateOf("") }

    Card(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Gold.copy(alpha = 0.35f)),
        colors = CardDefaults.cardColors(containerColor = Parchment),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SharedPackHeader(passwordSaved)
            CompactTextField(
                value = sharedPackPassword,
                onValueChange = { sharedPackPassword = it },
                label = "Shared pack password",
                keyboardType = KeyboardType.Password,
                visualTransformation = PasswordVisualTransformation(),
            )
            SaveSharedPackPasswordButton(
                password = sharedPackPassword,
                importInProgress = importInProgress,
                onSave = {
                    actions.onSavePassword(sharedPackPassword)
                    sharedPackPassword = ""
                },
            )
            SharedPackButtons(
                passwordSaved = passwordSaved,
                importInProgress = importInProgress,
                onImport = actions.onImport,
                onClear = actions.onClearPassword,
            )
        }
    }
}

@Composable
private fun SharedPackHeader(passwordSaved: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (passwordSaved) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
            contentDescription = null,
            tint = Plum,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            DisplayText("Shared packs", 24.sp, Ink, FontWeight.Bold, maxLines = 1)
            Text(
                if (passwordSaved) {
                    "Password saved. New bundled packs import after updates."
                } else {
                    "Save the password to unlock encrypted bundled packs."
                },
                color = MutedInk,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SaveSharedPackPasswordButton(
    password: String,
    importInProgress: Boolean,
    onSave: () -> Unit,
) {
    Button(
        onClick = onSave,
        enabled = password.isNotBlank() && !importInProgress,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Plum, contentColor = Gold),
        border = BorderStroke(1.dp, Gold),
    ) {
        Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text("Save password", maxLines = 1)
    }
}

@Composable
private fun SharedPackButtons(
    passwordSaved: Boolean,
    importInProgress: Boolean,
    onImport: () -> Unit,
    onClear: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = onImport,
            enabled = passwordSaved && !importInProgress,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GoldDeep, contentColor = Color.White),
        ) {
            if (importInProgress) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
            } else {
                Icon(Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(6.dp))
            Text("Import now", maxLines = 1)
        }
        OutlinedButton(
            onClick = onClear,
            enabled = passwordSaved && !importInProgress,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Plum.copy(alpha = 0.42f)),
        ) {
            Text("Clear", maxLines = 1, color = Plum)
        }
    }
}

@Composable
private fun QuestReminderSettingsCard(
    settings: QuestNotificationSettings,
    onTimeChange: (Int, Int) -> Unit,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
) {
    val context = LocalContext.current
    Card(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Gold.copy(alpha = 0.35f)),
        colors = CardDefaults.cardColors(containerColor = Parchment),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Timer, contentDescription = null, tint = Plum, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    DisplayText("Quest reminders", 24.sp, Ink, FontWeight.Bold, maxLines = 1)
                    Text(
                        if (settings.enabled) "Daily at ${settings.timeLabel}" else "Off",
                        color = MutedInk,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Switch(
                    checked = settings.enabled,
                    onCheckedChange = { enabled ->
                        if (enabled) onEnable() else onDisable()
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = {
                        TimePickerDialog(
                            context,
                            { _, hour, minute -> onTimeChange(hour, minute) },
                            settings.hour,
                            settings.minute,
                            DateFormat.is24HourFormat(context),
                        ).show()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Plum.copy(alpha = 0.42f)),
                ) {
                    Icon(Icons.Outlined.Timer, contentDescription = null, tint = Plum, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Time ${settings.timeLabel}", maxLines = 1, color = Plum)
                }
                Text(
                    "Uncompleted only",
                    color = MutedInk,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun OrnateActionButton(
    icon: ImageVector,
    title: String,
    detail: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 86.dp),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Gold.copy(alpha = 0.65f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink),
    ) {
        Icon(icon, contentDescription = null, tint = Plum, modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
            DisplayText(title, 23.sp, Ink, FontWeight.Bold, maxLines = 3)
            Text(detail, color = MutedInk, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun QuickAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 58.dp),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Gold.copy(alpha = 0.55f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink),
    ) {
        Icon(icon, contentDescription = null, tint = Plum, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, maxLines = 1)
    }
}

@Composable
private fun SectionHeader(
    title: String,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth < 340.dp && action != null) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DisplayText("✦ $title", 23.sp, Ink, FontWeight.Bold, maxLines = 3)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OrnamentLine(Modifier.weight(1f))
                    TextButton(onClick = onAction) {
                        Text(action, color = Plum)
                    }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DisplayText("✦ $title", 23.sp, Ink, FontWeight.Bold, maxLines = 2)
                Spacer(Modifier.width(10.dp))
                OrnamentLine(Modifier.weight(1f))
                if (action != null) {
                    TextButton(onClick = onAction) {
                        Text(action, color = Plum)
                    }
                }
            }
        }
    }
}

@Composable
private fun OrnamentDivider(modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OrnamentLine(Modifier.weight(1f))
        Text("  ✿  ", color = GoldDeep)
        OrnamentLine(Modifier.weight(1f))
    }
}

@Composable
private fun OrnamentLine(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(1.dp)
            .background(Gold.copy(alpha = 0.38f)),
    )
}

@Composable
private fun CircleQuestIcon(modifier: Modifier = Modifier, icon: String) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(Plum, DeepPlum),
                )
            )
            .border(BorderStroke(2.dp, Gold), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        when (icon.lowercase()) {
            "cat", "paruchan" -> Mascot(Modifier.fillMaxSize(0.82f))
            else -> Icon(Icons.Outlined.Star, contentDescription = null, tint = Gold, modifier = Modifier.fillMaxSize(0.52f))
        }
    }
}

@Composable
private fun LevelBadge(level: Int) {
    Box(
        modifier = Modifier
            .size(86.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.verticalGradient(listOf(Plum, DeepPlum)))
            .border(BorderStroke(2.dp, Gold), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("LEVEL", color = Gold, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            DisplayText(level.toString(), 42.sp, Gold, FontWeight.Bold)
        }
    }
}

@Composable
private fun EmptyStateCard(text: String) {
    Card(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Gold.copy(alpha = 0.35f)),
        colors = CardDefaults.cardColors(containerColor = Parchment),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Mascot(Modifier.size(76.dp))
            DisplayText(text, 22.sp, Ink, FontWeight.SemiBold)
        }
    }
}

@Composable
private fun Mascot(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.paruchan_mascot),
        contentDescription = null,
        modifier = modifier,
    )
}

@Composable
private fun DisplayText(
    text: String,
    size: androidx.compose.ui.unit.TextUnit,
    color: Color = Ink,
    weight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null,
) {
    Text(
        text = text,
        color = color,
        fontSize = size,
        fontWeight = weight,
        fontFamily = FontFamily.Serif,
        letterSpacing = 0.sp,
        lineHeight = size * 1.18f,
        modifier = modifier,
        maxLines = maxLines,
        overflow = overflow,
        textAlign = textAlign,
    )
}

private fun shortDate(value: String): String =
    value.replace('T', ' ').substringBeforeLast('.').removeSuffix("Z")

private fun formatTimer(seconds: Int): String {
    val safeSeconds = seconds.coerceAtLeast(0)
    val minutes = safeSeconds / 60
    val remainingSeconds = safeSeconds % 60
    return "%02d:%02d".format(minutes, remainingSeconds)
}

private fun progressSummary(progress: QuestProgress): String {
    val unit = unitLabel(progress.unit, progress.target)
    return "${progress.progressInCycle}/${progress.target} $unit"
}

private fun normalizedUnitLabel(unit: String, goalType: QuestGoalType, amount: Int): String {
    val normalized = when (goalType) {
        QuestGoalType.Timer -> "minute"
        else -> unit.ifBlank { "completion" }
    }
    return unitLabel(normalized, amount)
}

private fun unitLabel(unit: String, amount: Int): String =
    if (amount == 1 || unit.endsWith("s", ignoreCase = true)) unit else "${unit}s"

private enum class Screen(
    val label: String,
    val title: String,
    val icon: ImageVector,
    val showInBottomNav: Boolean = false,
) {
    Home("Home", "Paruchan", Icons.Outlined.Home, showInBottomNav = true),
    History("Log", "Quest Log", Icons.Outlined.History, showInBottomNav = true),
    Diary("Diary", "Diary", Icons.Outlined.Star, showInBottomNav = true),
    Files("Files", "Library", Icons.Outlined.ImportExport, showInBottomNav = true),
    Settings("Prefs", "Settings", Icons.Outlined.Settings, showInBottomNav = true),
}
