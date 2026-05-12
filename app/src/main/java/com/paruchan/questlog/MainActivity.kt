package com.paruchan.questlog

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.paruchan.questlog.ui.ParuchanQuestLogApp
import com.paruchan.questlog.ui.QuestLogViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: QuestLogViewModel by viewModels()

    private val importQuestPack = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { viewModel.importQuestPack(this, it) }
    }

    private val exportBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        uri?.let { viewModel.exportBackup(this, it) }
    }

    private val restoreBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { viewModel.requestRestore(it) }
    }

    private val requestNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            viewModel.enableQuestNotifications()
        } else {
            viewModel.notificationPermissionDenied()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ParuchanQuestLogApp(
                viewModel = viewModel,
                onImportQuestPack = { importQuestPack.launch(arrayOf("application/json", "text/*")) },
                onExportBackup = { exportBackup.launch("paruchan-quest-log-backup.json") },
                onRestoreBackup = { restoreBackup.launch(arrayOf("application/json", "text/*")) },
                onEnableQuestNotifications = ::enableQuestNotifications,
            )
        }
    }

    private fun enableQuestNotifications() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        viewModel.enableQuestNotifications()
    }
}
