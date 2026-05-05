package com.paruchan.questlog.data

import android.content.Context
import com.paruchan.questlog.core.EncryptedSharedPackAsset
import com.paruchan.questlog.core.SharedPackImporter
import com.paruchan.questlog.core.SharedPackMergeResult

class BundledSharedPackRepository(
    private val context: Context,
    private val questLogRepository: QuestLogRepository,
    private val importer: SharedPackImporter = SharedPackImporter(),
) {
    private val prefs = context.getSharedPreferences("shared_pack_imports", Context.MODE_PRIVATE)

    @Synchronized
    fun importBundled(password: String, commitOnErrors: Boolean = true): SharedPackMergeResult {
        val assets = readBundledAssets()
        val currentState = questLogRepository.load()
        val result = importer.mergeEncryptedPacks(
            state = currentState,
            assets = assets,
            password = password,
            importedMarkers = prefs.getStringSet(KEY_IMPORTED_MARKERS, emptySet()).orEmpty(),
        )

        val shouldCommit = result.errors.isEmpty() || commitOnErrors
        if (shouldCommit && result.state != currentState) {
            questLogRepository.save(result.state)
        }
        if (shouldCommit && result.newMarkers.isNotEmpty()) {
            val markers = prefs.getStringSet(KEY_IMPORTED_MARKERS, emptySet()).orEmpty() + result.newMarkers
            prefs.edit().putStringSet(KEY_IMPORTED_MARKERS, markers).apply()
        }
        return result
    }

    private fun readBundledAssets(): List<EncryptedSharedPackAsset> {
        val names = context.assets.list(SHARED_PACK_DIR).orEmpty()
            .filter { it.endsWith(".json", ignoreCase = true) }
        return names.map { name ->
            EncryptedSharedPackAsset(
                name = name,
                json = context.assets.open("$SHARED_PACK_DIR/$name")
                    .bufferedReader()
                    .use { it.readText() },
            )
        }
    }

    private companion object {
        const val SHARED_PACK_DIR = "shared-packs"
        const val KEY_IMPORTED_MARKERS = "imported_markers"
    }
}
