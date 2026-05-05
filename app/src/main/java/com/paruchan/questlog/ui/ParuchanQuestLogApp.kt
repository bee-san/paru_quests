package com.paruchan.questlog.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ImportExport
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paruchan.questlog.R
import com.paruchan.questlog.BuildConfig
import com.paruchan.questlog.core.Completion
import com.paruchan.questlog.core.LevelProgress
import com.paruchan.questlog.core.Quest
import com.paruchan.questlog.core.QuestCadence
import com.paruchan.questlog.core.QuestPackExporter
import com.paruchan.questlog.core.QuestProgress
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
    onAddQuestPack: (String) -> Unit,
    onExportQuestPack: (String) -> Unit,
    onShareQuestPack: (String) -> Unit,
    onExportBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var screen by rememberSaveable { mutableStateOf(Screen.Home) }

    LaunchedEffect(viewModel.message) {
        val message = viewModel.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Short)
        viewModel.clearMessage()
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
                    Screen.entries.forEach { item ->
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
                                progress = viewModel.progress,
                                quests = viewModel.state.quests,
                                completionCount = viewModel.state.completions.size,
                                availableQuests = viewModel.state.quests.filter { viewModel.canComplete(it) },
                                canComplete = viewModel::canComplete,
                                progressFor = viewModel::progressFor,
                                onComplete = { questId, amount -> viewModel.completeQuest(questId, amount) },
                                onShowQuests = { screen = Screen.Quests },
                                onShowFiles = { screen = Screen.Files },
                                onShowSettings = { screen = Screen.Settings },
                            )

                            Screen.Quests -> QuestsScreen(
                                quests = viewModel.state.quests,
                                completedQuestIds = viewModel.completedQuestIds,
                                canComplete = viewModel::canComplete,
                                progressFor = viewModel::progressFor,
                                onComplete = { questId, amount -> viewModel.completeQuest(questId, amount) },
                            )

                            Screen.History -> HistoryScreen(
                                completions = viewModel.state.completions,
                                quests = viewModel.state.quests,
                            )

                            Screen.Files -> ImportExportScreen(
                                onImportQuestPack = onImportQuestPack,
                                onImportBundledPack = { viewModel.importBundledThankYouPack(context) },
                                onAddQuestPack = onAddQuestPack,
                                onExportQuestPack = onExportQuestPack,
                                onShareQuestPack = onShareQuestPack,
                                onExportBackup = onExportBackup,
                                onRestoreBackup = onRestoreBackup,
                            )

                            Screen.Settings -> SettingsScreen(
                                updateInProgress = viewModel.updateInProgress,
                                onCheckForUpdate = { viewModel.checkForUpdate(context) },
                            )
                        }
                    }
                }
            }
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
    progress: LevelProgress,
    quests: List<Quest>,
    completionCount: Int,
    availableQuests: List<Quest>,
    canComplete: (Quest) -> Boolean,
    progressFor: (Quest) -> QuestProgress,
    onComplete: (String, Int) -> Unit,
    onShowQuests: () -> Unit,
    onShowFiles: () -> Unit,
    onShowSettings: () -> Unit,
) {
    val dailyCount = quests.count { QuestCadence.from(it) == QuestCadence.Daily && canComplete(it) }
    val goalCount = quests.count { it.goalTarget > 1 && !it.archived }
    val spotlightQuests = availableQuests.sortedWith(
        compareByDescending<Quest> { QuestCadence.from(it) == QuestCadence.Daily }
            .thenByDescending { it.timerMinutes != null }
            .thenByDescending { it.goalTarget > 1 }
            .thenBy { it.title },
    )

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
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatCard("Active", availableQuests.size.toString(), "Quests", Modifier.weight(1f))
                StatCard("Daily", dailyCount.toString(), "Open today", Modifier.weight(1f))
                StatCard("Goals", goalCount.toString(), "Targets", Modifier.weight(1f))
            }
        }

        item {
            SectionHeader("Today's Quests", action = if (quests.isNotEmpty()) "View all" else null, onAction = onShowQuests)
        }

        if (availableQuests.isEmpty()) {
            item { EmptyStateCard("Import a quest pack to begin.") }
        } else {
            items(spotlightQuests.take(3), key = { it.id }) { quest ->
                QuestListCard(
                    quest = quest,
                    progress = progressFor(quest),
                    completed = false,
                    enabled = canComplete(quest),
                    compact = true,
                    onComplete = { amount -> onComplete(quest.id, amount) },
                )
            }
        }

        item {
            SectionHeader("Quick Session")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                QuickAction("Quests", Icons.AutoMirrored.Outlined.Assignment, onShowQuests, Modifier.weight(1f))
                QuickAction("Files", Icons.Outlined.ImportExport, onShowFiles, Modifier.weight(1f))
                QuickAction("Update", Icons.Outlined.SystemUpdateAlt, onShowSettings, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LevelCard(progress: LevelProgress) {
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
                    DisplayText(progress.current.title, 28.sp, Ink, FontWeight.Bold)
                    Text("Total XP", color = MutedInk, style = MaterialTheme.typography.bodyMedium)
                    DisplayText("${progress.totalXp} XP", 33.sp, Plum, FontWeight.Bold)
                }
                Mascot(Modifier.size(64.dp))
            }
            LinearProgressIndicator(
                progress = { progress.fraction },
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
                text = progress.xpToNext?.let { "$it XP to next level" } ?: "All current levels complete",
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
@OptIn(ExperimentalLayoutApi::class)
private fun QuestsScreen(
    quests: List<Quest>,
    completedQuestIds: Set<String>,
    canComplete: (Quest) -> Boolean,
    progressFor: (Quest) -> QuestProgress,
    onComplete: (String, Int) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(QuestTab.Active) }
    var category by rememberSaveable { mutableStateOf("All") }
    val categories = listOf("All") + quests.map { it.category }.filter { it.isNotBlank() }.distinct().sorted()
    val filtered = quests.filter { quest ->
        val tabMatch = when (tab) {
            QuestTab.Active -> canComplete(quest)
            QuestTab.Dailies -> QuestCadence.from(quest) == QuestCadence.Daily
            QuestTab.Goals -> quest.goalTarget > 1
            QuestTab.Counters -> QuestCadence.from(quest) == QuestCadence.Counter
            QuestTab.Timed -> quest.timerMinutes != null
            QuestTab.Completed -> quest.id in completedQuestIds && !quest.repeatable
            QuestTab.Repeatable -> quest.repeatable
        }
        val categoryMatch = category == "All" || quest.category == category
        tabMatch && categoryMatch
    }

    Column(Modifier.fillMaxSize()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            QuestTab.entries.forEach { item ->
                FilterPill(
                    text = item.label,
                    selected = tab == item,
                    onClick = { tab = item },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            categories.forEach { item ->
                FilterPill(text = item, selected = category == item, onClick = { category = item })
            }
        }
        OrnamentDivider(Modifier.padding(vertical = 14.dp))

        if (filtered.isEmpty()) {
            EmptyStateCard("No quests in this view.")
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 18.dp),
            ) {
                items(filtered, key = { it.id }) { quest ->
                    QuestListCard(
                        quest = quest,
                        progress = progressFor(quest),
                        completed = quest.id in completedQuestIds,
                        enabled = canComplete(quest),
                        onComplete = { amount -> onComplete(quest.id, amount) },
                    )
                }
            }
        }
    }
}

@Composable
private fun QuestListCard(
    quest: Quest,
    progress: QuestProgress,
    completed: Boolean,
    enabled: Boolean,
    compact: Boolean = false,
    onComplete: (Int) -> Unit,
) {
    val cadence = progress.cadence
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
            Row(verticalAlignment = Alignment.Top) {
                CircleQuestIcon(modifier = Modifier.size(if (compact) 54.dp else 70.dp), icon = quest.icon)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    DisplayText(
                        quest.title,
                        if (compact) 21.sp else 24.sp,
                        Ink,
                        FontWeight.Bold,
                        maxLines = 3,
                    )
                    if (quest.flavourText.isNotBlank()) {
                        Text(
                            quest.flavourText,
                            color = MutedInk,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = if (compact) 2 else 4,
                        )
                    }
                    if (!compact) {
                        Spacer(Modifier.height(6.dp))
                        QuestMetaRow(quest = quest, progress = progress)
                        if (progress.hasGoal) {
                            Spacer(Modifier.height(8.dp))
                            GoalProgressBar(progress = progress)
                        }
                        quest.timerMinutes?.let { minutes ->
                            Spacer(Modifier.height(8.dp))
                            QuestTimer(questId = quest.id, minutes = minutes)
                        }
                    } else {
                        Spacer(Modifier.height(5.dp))
                        QuestMetaRow(quest = quest, progress = progress, compact = true)
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    DisplayText(
                        if (cadence == QuestCadence.Counter) "${quest.xp} XP / ${progress.unit}" else "${quest.xp} XP",
                        22.sp,
                        Plum,
                        FontWeight.SemiBold,
                        maxLines = 2,
                    )
                    if (progress.hasGoal && !compact) {
                        Text("XP awarded when the goal completes", color = MutedInk, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (progress.hasGoal && !compact) {
                    Text("on goal", color = MutedInk, style = MaterialTheme.typography.bodySmall)
                }
                if (cadence == QuestCadence.Counter) {
                    CounterLogControls(
                        questId = quest.id,
                        unit = progress.unit,
                        enabled = enabled,
                        onLog = onComplete,
                    )
                } else {
                    CompleteButton(enabled = enabled, completed = completed && !quest.repeatable, onClick = { onComplete(1) })
                }
            }
        }
    }
}

@Composable
private fun QuestMetaRow(
    quest: Quest,
    progress: QuestProgress,
    compact: Boolean = false,
) {
    if (compact) {
        Text(
            compactMetaText(quest, progress),
            color = Plum,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 3,
        )
        return
    }

    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        item { MetaChip(progress.cadence.label) }
        item { MetaChip(quest.category.ifBlank { "General" }) }
        if (progress.cadence == QuestCadence.Counter) {
            item { MetaChip("${progress.progressInCycle} ${progress.unit} logged") }
        }
        if (progress.hasGoal) {
            item { MetaChip("${progress.progressInCycle}/${progress.target} ${progress.unit}") }
        }
        quest.timerMinutes?.let { minutes ->
            item { MetaChip("${minutes}m timer") }
        }
        if (progress.completedCycles > 0 && progress.cadence == QuestCadence.Repeatable && !compact) {
            item { MetaChip("${progress.completedCycles} cycles done") }
        }
    }
}

private fun compactMetaText(quest: Quest, progress: QuestProgress): String =
    buildList {
        add(progress.cadence.label)
        if (progress.cadence == QuestCadence.Counter) add("${progress.progressInCycle} ${progress.unit} logged")
        if (progress.hasGoal) add("${progress.progressInCycle}/${progress.target} ${progress.unit}")
        quest.timerMinutes?.let { add("${it}m timer") }
        add(quest.category.ifBlank { "General" })
    }.joinToString(" / ")

private fun compactPackQuestText(quest: Quest): String =
    buildList {
        val cadence = QuestCadence.from(quest)
        add(cadence.label)
        if (quest.goalTarget > 1) add("${quest.goalTarget} ${quest.goalUnit}")
        quest.timerMinutes?.let { add("${it}m timer") }
        add(if (cadence == QuestCadence.Counter) "${quest.xp} XP / ${quest.goalUnit}" else "${quest.xp} XP")
    }.joinToString(" / ")

@Composable
private fun MetaChip(text: String) {
    Box(
        modifier = Modifier
            .heightIn(min = 28.dp)
            .widthIn(min = 32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Lilac.copy(alpha = 0.48f))
            .border(BorderStroke(1.dp, Gold.copy(alpha = 0.25f)), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Plum, style = MaterialTheme.typography.labelMedium)
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
                "${progress.remaining} ${progress.unit} to go"
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
private fun CounterLogControls(
    questId: String,
    unit: String,
    enabled: Boolean,
    onLog: (Int) -> Unit,
) {
    var amountText by rememberSaveable(questId, "counterAmount") { mutableStateOf("1") }
    val amount = amountText.toIntOrNull()?.coerceAtLeast(1)

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it.filter(Char::isDigit).take(4) },
            modifier = Modifier.widthIn(min = 76.dp, max = 96.dp),
            label = { Text(unit) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(8.dp),
        )
        Button(
            onClick = { amount?.let(onLog) },
            enabled = enabled && amount != null,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Plum, contentColor = Gold),
            border = BorderStroke(1.dp, Gold),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        ) {
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
private fun ImportExportScreen(
    onImportQuestPack: () -> Unit,
    onImportBundledPack: () -> Unit,
    onAddQuestPack: (String) -> Unit,
    onExportQuestPack: (String) -> Unit,
    onShareQuestPack: (String) -> Unit,
    onExportBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
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
            QuestPackMaker(
                onAddQuestPack = onAddQuestPack,
                onExportQuestPack = onExportQuestPack,
                onShareQuestPack = onShareQuestPack,
            )
        }
        item {
            OrnateActionButton(
                icon = Icons.Outlined.Star,
                title = "Add thank-you pack",
                detail = "One bundled quest: Thank you paruchan, 5000 XP",
                onClick = onImportBundledPack,
            )
        }
        item {
            OrnateActionButton(
                icon = Icons.Outlined.FileUpload,
                title = "Import quest pack",
                detail = "Merge JSON quests into your log",
                onClick = onImportQuestPack,
            )
        }
        item {
            OrnateActionButton(
                icon = Icons.Outlined.FileDownload,
                title = "Export backup",
                detail = "Save quests, completions, and levels",
                onClick = onExportBackup,
            )
        }
        item {
            OrnateActionButton(
                icon = Icons.Outlined.Restore,
                title = "Restore backup",
                detail = "Replace this device's quest log",
                onClick = onRestoreBackup,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun QuestPackMaker(
    onAddQuestPack: (String) -> Unit,
    onExportQuestPack: (String) -> Unit,
    onShareQuestPack: (String) -> Unit,
) {
    val exporter = remember { QuestPackExporter() }
    val packQuests = remember { mutableStateListOf<Quest>() }
    var packName by rememberSaveable { mutableStateOf("Paruchan Quest Pack") }
    var title by rememberSaveable { mutableStateOf("") }
    var flavourText by rememberSaveable { mutableStateOf("") }
    var xpText by rememberSaveable { mutableStateOf("50") }
    var category by rememberSaveable { mutableStateOf("Paruchan") }
    var icon by rememberSaveable { mutableStateOf("star") }
    var cadence by rememberSaveable { mutableStateOf(QuestCadence.Once) }
    var goalTargetText by rememberSaveable { mutableStateOf("1") }
    var goalUnit by rememberSaveable { mutableStateOf("completion") }
    var timerMinutesText by rememberSaveable { mutableStateOf("") }

    fun draftQuest(): Quest? {
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
            goalTarget = if (cadence == QuestCadence.Counter) 1 else goalTarget,
            goalUnit = goalUnit.trim().ifBlank { if (cadence == QuestCadence.Counter) "unit" else "completion" },
            timerMinutes = if (cadence == QuestCadence.Counter) null else timerMinutes,
        )
    }

    fun packJson(): String = exporter.encodePack(packName, packQuests.toList())

    Card(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Gold.copy(alpha = 0.42f)),
        colors = CardDefaults.cardColors(containerColor = Parchment.copy(alpha = 0.98f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader("Quest Pack Maker")
            CompactTextField(
                value = packName,
                onValueChange = { packName = it },
                label = "Pack name",
            )
            CompactTextField(
                value = title,
                onValueChange = { title = it },
                label = "Quest title",
            )
            CompactTextField(
                value = flavourText,
                onValueChange = { flavourText = it },
                label = "Flavour text",
            )
            CompactTextField(
                value = xpText,
                onValueChange = { xpText = it.filter(Char::isDigit).ifBlank { "0" } },
                label = if (cadence == QuestCadence.Counter) "XP per unit" else "XP",
                numeric = true,
            )
            CompactTextField(
                value = category,
                onValueChange = { category = it },
                label = "Category",
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                QuestCadence.entries.forEach { item ->
                    FilterPill(text = item.label, selected = cadence == item, onClick = { cadence = item })
                }
            }
            if (cadence != QuestCadence.Counter) {
                CompactTextField(
                    value = goalTargetText,
                    onValueChange = { goalTargetText = it.filter(Char::isDigit).ifBlank { "1" } },
                    label = "Goal target",
                    numeric = true,
                )
            }
            CompactTextField(
                value = goalUnit,
                onValueChange = { goalUnit = it },
                label = if (cadence == QuestCadence.Counter) "Counter unit" else "Unit",
            )
            if (cadence != QuestCadence.Counter) {
                CompactTextField(
                    value = timerMinutesText,
                    onValueChange = { timerMinutesText = it.filter(Char::isDigit).take(4) },
                    label = "Timer minutes",
                    numeric = true,
                )
            }
            CompactTextField(
                value = icon,
                onValueChange = { icon = it },
                label = "Icon",
            )

            Button(
                onClick = {
                    draftQuest()?.let { quest ->
                        packQuests += quest
                        title = ""
                        flavourText = ""
                        goalTargetText = "1"
                        goalUnit = "completion"
                        timerMinutesText = ""
                    }
                },
                enabled = draftQuest() != null,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Plum, contentColor = Gold),
                border = BorderStroke(1.dp, Gold),
            ) {
                Icon(Icons.Outlined.AddCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                DisplayText("Add quest to pack", 20.sp, Gold, FontWeight.Bold, maxLines = 2, textAlign = TextAlign.Center)
            }

            if (packQuests.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${packQuests.size} quest${if (packQuests.size == 1) "" else "s"} ready", color = MutedInk)
                    packQuests.forEachIndexed { index, quest ->
                        DraftQuestRow(
                            quest = quest,
                            onRemove = { packQuests.removeAt(index) },
                        )
                    }
                }
            }

            Button(
                onClick = { onAddQuestPack(packJson()) },
                enabled = packQuests.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Plum, contentColor = Gold),
                border = BorderStroke(1.dp, Gold),
            ) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add to Quest Log", maxLines = 1)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { onExportQuestPack(packJson()) },
                    enabled = packQuests.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Plum, contentColor = Gold),
                    border = BorderStroke(1.dp, Gold),
                ) {
                    Icon(Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Export", maxLines = 1)
                }
                Button(
                    onClick = { onShareQuestPack(packJson()) },
                    enabled = packQuests.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldDeep, contentColor = Color.White),
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Share", maxLines = 1)
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
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp),
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        shape = RoundedCornerShape(8.dp),
    )
}

@Composable
private fun DraftQuestRow(
    quest: Quest,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Lilac.copy(alpha = 0.36f))
            .border(BorderStroke(1.dp, Gold.copy(alpha = 0.28f)), RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleQuestIcon(modifier = Modifier.size(44.dp), icon = quest.icon)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            DisplayText(quest.title, 18.sp, Ink, FontWeight.Bold)
            Text(
                compactPackQuestText(quest),
                color = MutedInk,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
            )
        }
        Icon(
            Icons.Outlined.Delete,
            contentDescription = "Remove quest",
            tint = Plum,
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onRemove)
                .padding(6.dp),
        )
    }
}

@Composable
private fun SettingsScreen(
    updateInProgress: Boolean,
    onCheckForUpdate: () -> Unit,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
        item {
            SectionHeader("Settings")
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
        item {
            Button(
                onClick = onCheckForUpdate,
                enabled = !updateInProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Plum, contentColor = Gold),
                border = BorderStroke(1.dp, Gold),
            ) {
                if (updateInProgress) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = Gold)
                } else {
                    Icon(Icons.Outlined.SystemUpdateAlt, contentDescription = null, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(10.dp))
                DisplayText("Check for update", 24.sp, Gold, FontWeight.Bold, maxLines = 2, textAlign = TextAlign.Center)
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
private fun FilterPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .heightIn(min = 46.dp)
            .widthIn(min = 76.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Plum else Parchment.copy(alpha = 0.8f))
            .border(BorderStroke(1.dp, if (selected) Gold else Gold.copy(alpha = 0.35f)), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) Gold else Ink,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
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

private enum class QuestTab(val label: String) {
    Active("Active"),
    Dailies("Dailies"),
    Goals("Goals"),
    Counters("Counters"),
    Timed("Timed"),
    Repeatable("Repeat"),
    Completed("Completed"),
}

private enum class Screen(
    val label: String,
    val title: String,
    val icon: ImageVector,
) {
    Home("Home", "Paruchan", Icons.Outlined.Home),
    Quests("Quests", "Quests", Icons.AutoMirrored.Outlined.Assignment),
    History("Log", "Quest Log", Icons.Outlined.History),
    Files("Files", "Library", Icons.Outlined.ImportExport),
    Settings("Prefs", "Settings", Icons.Outlined.Settings),
}
