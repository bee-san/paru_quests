package com.paruchan.questlog.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.paruchan.questlog.core.UserBackupFiles
import java.time.Clock
import java.time.LocalDate

class UserBackupFolderStore(
    context: Context,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val retentionCount: Int = UserBackupFiles.DefaultRetentionCount,
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasFolder(): Boolean = folderUri() != null

    fun saveFolder(uri: Uri) {
        appContext.contentResolver.takePersistableUriPermission(uri, PERSISTED_URI_FLAGS)
        prefs.edit()
            .putString(KEY_FOLDER_URI, uri.toString())
            .apply()
    }

    fun clearFolder() {
        folderUri()?.let { uri ->
            runCatching {
                appContext.contentResolver.releasePersistableUriPermission(uri, PERSISTED_URI_FLAGS)
            }
        }
        prefs.edit()
            .remove(KEY_FOLDER_URI)
            .apply()
    }

    fun writeBackup(json: String): UserBackupWriteResult {
        val treeUri = folderUri() ?: return UserBackupWriteResult.Disabled
        val directoryUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        val children = listChildren(treeUri, directoryUri)
        val datedName = UserBackupFiles.datedBackupName(LocalDate.now(clock))

        writeOrCreate(directoryUri, children[UserBackupFiles.LatestBackupName], UserBackupFiles.LatestBackupName, json)
        writeOrCreate(directoryUri, children[datedName], datedName, json)

        val pruneNames = UserBackupFiles.backupNamesToPrune(
            existingNames = children.keys + datedName,
            retentionCount = retentionCount,
        )
        pruneNames.forEach { name ->
            children[name]?.let { DocumentsContract.deleteDocument(appContext.contentResolver, it) }
        }

        return UserBackupWriteResult.Saved(datedName)
    }

    private fun folderUri(): Uri? {
        val raw = prefs.getString(KEY_FOLDER_URI, null) ?: return null
        return runCatching { Uri.parse(raw) }.getOrNull()
    }

    private fun listChildren(treeUri: Uri, directoryUri: Uri): Map<String, Uri> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getDocumentId(directoryUri),
        )
        val children = linkedMapOf<String, Uri>()
        appContext.contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIndex) ?: continue
                val documentId = cursor.getString(idIndex) ?: continue
                children[name] = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
            }
        }
        return children
    }

    private fun writeOrCreate(directoryUri: Uri, existingUri: Uri?, name: String, json: String) {
        val documentUri = existingUri ?: DocumentsContract.createDocument(
            appContext.contentResolver,
            directoryUri,
            BACKUP_MIME_TYPE,
            name,
        ) ?: error("Could not create $name")

        appContext.contentResolver.openOutputStream(documentUri, "wt")
            ?.bufferedWriter()
            ?.use { it.write(json) }
            ?: error("Could not write $name")
    }

    private companion object {
        const val PREFS_NAME = "user_backup_folder"
        const val KEY_FOLDER_URI = "folder_uri"
        const val BACKUP_MIME_TYPE = "application/json"
        const val PERSISTED_URI_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}

sealed interface UserBackupWriteResult {
    data object Disabled : UserBackupWriteResult
    data class Saved(val datedFileName: String) : UserBackupWriteResult
}
