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
    private val legacyPrefs = context.getSharedPreferences("shared_pack_imports", Context.MODE_PRIVATE)

    @Synchronized
    fun importBundled(password: String, commitOnErrors: Boolean = true): SharedPackMergeResult {
        val assets = readBundledAssets()
        val currentState = questLogRepository.load()
        val importedMarkers = questLogRepository.importedSharedPackMarkers() + legacyImportedMarkers()
        val result = importer.mergeEncryptedPacks(
            state = currentState,
            assets = assets,
            password = password,
            importedMarkers = importedMarkers,
        )

        val shouldCommit = result.errors.isEmpty() || commitOnErrors
        if (shouldCommit && result.state != currentState) {
            questLogRepository.save(result.state)
        }
        if (shouldCommit && result.newMarkers.isNotEmpty()) {
            questLogRepository.addImportedSharedPackMarkers(result.newMarkers)
        }
        migrateLegacyMarkersIfNeeded(importedMarkers)
        return result
    }

    private fun legacyImportedMarkers(): Set<String> =
        legacyPrefs.getStringSet(KEY_IMPORTED_MARKERS, emptySet()).orEmpty()

    private fun migrateLegacyMarkersIfNeeded(markers: Set<String>) {
        val legacyMarkers = legacyImportedMarkers()
        if (legacyMarkers.isEmpty()) return
        questLogRepository.addImportedSharedPackMarkers(markers)
        legacyPrefs.edit().remove(KEY_IMPORTED_MARKERS).apply()
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
