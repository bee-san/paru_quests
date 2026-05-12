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
    val closed: Int,
    val skipped: Int,
    val newMarkers: Set<String>,
    val errors: List<String> = emptyList(),
) {
    val changed: Boolean
        get() = imported > 0 || updated > 0 || closed > 0 || newMarkers.isNotEmpty()

    val summary: String
        get() = when {
            totalPacks == 0 -> "No shared packs found"
            appliedPacks == 0 && errors.isEmpty() -> "Shared packs already up to date"
            errors.isEmpty() -> "Shared packs: imported $imported, updated $updated${closedSummary()}, skipped $skipped"
            else -> "Shared packs: imported $imported, updated $updated${closedSummary()}, skipped $skipped; ${errors.first()}"
        }

    private fun closedSummary(): String = if (closed > 0) ", closed $closed" else ""
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
        val decryptedAssets = mutableListOf<DecryptedSharedPackAsset>()
        val newMarkers = mutableSetOf<String>()
        val errors = mutableListOf<String>()
        var appliedPacks = 0
        var unchangedPacks = 0
        var imported = 0
        var updated = 0
        var closed = 0
        var skipped = 0
        val incomingQuestIds = mutableSetOf<String>()

        assets.sortedBy { it.name }.forEach { asset ->
            runCatching {
                val decrypted = EncryptedQuestPackCodec.decrypt(asset.json, password)
                decryptedAssets += DecryptedSharedPackAsset(
                    assetName = asset.name,
                    packId = decrypted.packId,
                    packVersion = decrypted.packVersion,
                    questPackJson = decrypted.questPackJson,
                    marker = decrypted.importMarker(),
                )
            }.onFailure { error ->
                skipped++
                errors += "${asset.name}: ${error.message ?: "Shared pack could not be imported"}"
            }
        }

        val highestPackVersion = decryptedAssets.maxWithOrNull(decryptedSharedPackAssetComparator)?.packVersion
        val currentGenerationAssets = decryptedAssets.filter { asset ->
            highestPackVersion != null && comparePackVersions(asset.packVersion, highestPackVersion) == 0
        }

        currentGenerationAssets.sortedBy { it.assetName }.forEach { asset ->
            if (asset.marker in importedMarkers || asset.marker in newMarkers) {
                incomingQuestIds += questPackImporter.questIdsInQuestPack(asset.questPackJson, workingState)
                unchangedPacks++
                return@forEach
            }

            val result = questPackImporter.mergeQuestPack(
                state = workingState,
                json = asset.questPackJson,
                closePrevious = false,
            )
            workingState = result.state
            imported += result.imported
            updated += result.updated
            skipped += result.skipped
            incomingQuestIds += result.incomingQuestIds
            errors += result.errors.map { "${asset.packId}: $it" }

            if (result.errors.isEmpty()) {
                newMarkers += asset.marker
            }
            appliedPacks++
        }

        if (currentGenerationAssets.isNotEmpty() && incomingQuestIds.isNotEmpty() && errors.isEmpty()) {
            val closeResult = questPackImporter.closeQuestsOutside(workingState, incomingQuestIds)
            workingState = closeResult.state
            closed = closeResult.closed
        }

        return SharedPackMergeResult(
            state = workingState,
            totalPacks = assets.size,
            appliedPacks = appliedPacks,
            unchangedPacks = unchangedPacks,
            imported = imported,
            updated = updated,
            closed = closed,
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

private data class DecryptedSharedPackAsset(
    val assetName: String,
    val packId: String,
    val packVersion: String,
    val questPackJson: String,
    val marker: String,
)

private val decryptedSharedPackAssetComparator = Comparator<DecryptedSharedPackAsset> { left, right ->
    comparePackVersions(left.packVersion, right.packVersion)
        .takeIf { it != 0 }
        ?: left.assetName.compareTo(right.assetName)
}

private fun comparePackVersions(left: String, right: String): Int {
    val leftParts = left.trim().split('.')
    val rightParts = right.trim().split('.')
    val partCount = maxOf(leftParts.size, rightParts.size)

    for (index in 0 until partCount) {
        val leftPart = leftParts.getOrNull(index)
        val rightPart = rightParts.getOrNull(index)
        if (leftPart == null) return -1
        if (rightPart == null) return 1

        val leftNumber = leftPart.toIntOrNull()
        val rightNumber = rightPart.toIntOrNull()
        when {
            leftNumber != null && rightNumber != null && leftNumber != rightNumber -> {
                return leftNumber.compareTo(rightNumber)
            }

            leftNumber == null || rightNumber == null -> {
                val lexical = left.trim().compareTo(right.trim(), ignoreCase = true)
                if (lexical != 0) return lexical
            }
        }
    }

    return left.trim().compareTo(right.trim(), ignoreCase = true)
}
