package com.paruchan.questlog

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.paruchan.questlog.ui.ParuchanQuestLogApp
import com.paruchan.questlog.ui.QuestLogViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: QuestLogViewModel by viewModels()

    private val importQuestPack = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { viewModel.importQuestPack(this, it) }
    }

    private val exportQuestPack = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        val json = viewModel.consumePendingQuestPackExport()
        if (uri != null && json != null) {
            viewModel.exportQuestPack(this, uri, json)
        }
    }

    private val exportBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        uri?.let { viewModel.exportBackup(this, it) }
    }

    private val restoreBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { viewModel.requestRestore(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ParuchanQuestLogApp(
                viewModel = viewModel,
                onImportQuestPack = { importQuestPack.launch(arrayOf("application/json", "text/*")) },
                onAddQuestPack = { json -> viewModel.addGeneratedQuestPack(json) },
                onExportQuestPack = { json ->
                    viewModel.prepareQuestPackExport(json)
                    exportQuestPack.launch("paruchan-quest-pack.json")
                },
                onShareQuestPack = { json -> viewModel.shareQuestPack(this, json) },
                onExportBackup = { exportBackup.launch("paruchan-quest-log-backup.json") },
                onRestoreBackup = { restoreBackup.launch(arrayOf("application/json", "text/*")) },
            )
        }
    }
}
