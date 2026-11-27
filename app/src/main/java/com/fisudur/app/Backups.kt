package com.fisudur.app

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/**
 * A daily copy in a folder the person picked once (on the phone, or a synced folder such as
 * Google Drive), through Android's document picker: no account, no server, no extra permission.
 */
object Backups {
    private const val NAME = "fi-sudur-backup.json"

    /** Writes [json] as [NAME] in the folder, replacing yesterday's copy. False if the folder is gone. */
    fun writeToFolder(context: Context, folder: String, json: String): Boolean = runCatching {
        val tree = Uri.parse(folder)
        val resolver = context.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        var target: Uri? = null
        resolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(1) == NAME) {
                    target = DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0))
                    break
                }
            }
        }
        val file = target ?: DocumentsContract.createDocument(resolver, parent, "application/json", NAME) ?: return false
        resolver.openOutputStream(file, "wt")!!.use { it.write(json.toByteArray()) }
        true
    }.getOrDefault(false)
}
