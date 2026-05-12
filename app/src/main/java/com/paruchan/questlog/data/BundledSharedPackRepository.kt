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
        return listOf(
            EncryptedSharedPackAsset(
                name = CURRENT_SHARED_PACK_ASSET_NAME,
                json = context.assets.open("$SHARED_PACK_DIR/$CURRENT_SHARED_PACK_ASSET_NAME")
                    .bufferedReader()
                    .use { it.readText() },
            )
        )
    }

    private companion object {
        const val SHARED_PACK_DIR = "shared-packs"
        const val CURRENT_SHARED_PACK_ASSET_NAME = "current.encrypted.json"
        const val KEY_IMPORTED_MARKERS = "imported_markers"
    }
}
