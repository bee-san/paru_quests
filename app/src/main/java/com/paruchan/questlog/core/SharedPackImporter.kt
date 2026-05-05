package com.paruchan.questlog.core

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

data class EncryptedSharedPackAsset(
    val name: String,
    val json: String,
)

data class SharedPackMergeResult(
    val state: QuestLogState,
    val totalPacks: Int,
    val appliedPacks: Int,
    val unchangedPacks: Int,
    val imported: Int,
    val updated: Int,
    val skipped: Int,
    val newMarkers: Set<String>,
    val errors: List<String> = emptyList(),
) {
    val changed: Boolean
        get() = imported > 0 || updated > 0 || newMarkers.isNotEmpty()

    val summary: String
        get() = when {
            totalPacks == 0 -> "No shared packs found"
            appliedPacks == 0 && errors.isEmpty() -> "Shared packs already up to date"
            errors.isEmpty() -> "Shared packs: imported $imported, updated $updated, skipped $skipped"
            else -> "Shared packs: imported $imported, updated $updated, skipped $skipped; ${errors.first()}"
        }
}

class SharedPackImporter(
    private val questPackImporter: QuestPackImporter = QuestPackImporter(),
) {
    fun mergeEncryptedPacks(
        state: QuestLogState,
        assets: List<EncryptedSharedPackAsset>,
        password: String,
        importedMarkers: Set<String>,
    ): SharedPackMergeResult {
        var workingState = state
        val newMarkers = mutableSetOf<String>()
        val errors = mutableListOf<String>()
        var appliedPacks = 0
        var unchangedPacks = 0
        var imported = 0
        var updated = 0
        var skipped = 0

        assets.sortedBy { it.name }.forEach { asset ->
            runCatching {
                val decrypted = EncryptedQuestPackCodec.decrypt(asset.json, password)
                val marker = decrypted.importMarker()
                if (marker in importedMarkers || marker in newMarkers) {
                    unchangedPacks++
                    return@forEach
                }

                val result = questPackImporter.mergeQuestPack(workingState, decrypted.questPackJson)
                workingState = result.state
                imported += result.imported
                updated += result.updated
                skipped += result.skipped
                errors += result.errors.map { "${decrypted.packId}: $it" }

                if (result.errors.isEmpty()) {
                    newMarkers += marker
                }
                appliedPacks++
            }.onFailure { error ->
                skipped++
                errors += "${asset.name}: ${error.message ?: "Shared pack could not be imported"}"
            }
        }

        return SharedPackMergeResult(
            state = workingState,
            totalPacks = assets.size,
            appliedPacks = appliedPacks,
            unchangedPacks = unchangedPacks,
            imported = imported,
            updated = updated,
            skipped = skipped,
            newMarkers = newMarkers,
            errors = errors,
        )
    }

    private fun DecryptedQuestPack.importMarker(): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(questPackJson.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "$packId:$packVersion:$digest"
    }
}
