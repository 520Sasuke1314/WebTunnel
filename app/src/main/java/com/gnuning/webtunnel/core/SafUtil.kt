package com.gnuning.webtunnel.core

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.InputStream

/**
 * SAF（存储访问框架）工具。
 *
 * 本应用**不需要**申请 READ_EXTERNAL_STORAGE / MANAGE_EXTERNAL_STORAGE 等危险权限：
 * 用户通过系统文件选择器授权后，我们拿到的是可持久化的 Uri（重启后仍可读），
 * 这既符合 Android 10+ 的分区存储要求，也满足「动态授权」的安全规范。
 *
 * 这里直接用平台 API [DocumentsContract] 遍历目录，不依赖 androidx.documentfile。
 */
object SafUtil {

    private const val MIME_DIR = DocumentsContract.Document.MIME_TYPE_DIR

    data class Entry(
        val docId: String,
        val name: String,
        val mime: String,
        val size: Long,
        val lastModified: Long,
    ) {
        val isDir: Boolean get() = mime == MIME_DIR
    }

    /** 取出 tree Uri 的根 documentId。 */
    fun treeRootId(treeUri: Uri): String? =
        runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()

    /** 列出某个目录下的直接子项。 */
    fun children(context: Context, treeUri: Uri, parentDocId: String): List<Entry> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val result = mutableListOf<Entry>()
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        runCatching {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0) ?: continue
                    result += Entry(
                        docId = id,
                        name = cursor.getString(1) ?: "未命名",
                        mime = cursor.getString(2) ?: "",
                        size = if (cursor.isNull(3)) 0L else cursor.getLong(3),
                        lastModified = if (cursor.isNull(4)) 0L else cursor.getLong(4),
                    )
                }
            }
        }
        return result
    }

    /** 在目录里按名字找子项（大小写不敏感）。 */
    fun findChild(context: Context, treeUri: Uri, parentDocId: String, name: String): Entry? =
        children(context, treeUri, parentDocId).firstOrNull { it.name.equals(name, ignoreCase = true) }

    /** 打开目录树里的某个文档。 */
    fun openDocument(context: Context, treeUri: Uri, docId: String): InputStream? =
        runCatching {
            context.contentResolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(treeUri, docId))
        }.getOrNull()

    /** 打开单个文件 Uri。 */
    fun open(context: Context, uri: Uri): InputStream? =
        runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()

    /**
     * 目录（Tree Uri）是否仍可访问。
     * 注意：不能用 openInputStream(treeUri) 判断——Tree Uri 不支持直接打开；
     * 这里改成向子文档 URI 发一次查询，只要游标能建起来（哪怕 0 行）就算可访问。
     */
    fun canAccessTree(context: Context, treeUri: Uri): Boolean {
        val rootId = treeRootId(treeUri) ?: return false
        return runCatching {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootId)
            val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { true } ?: false
        }.getOrDefault(false)
    }

    /** 文件是否还可读（用户可能把文件删了 / 撤销了授权）。 */
    fun canRead(context: Context, uri: Uri): Boolean =
        runCatching { context.contentResolver.openInputStream(uri)?.use { true } ?: false }.getOrDefault(false)

    /** 查 display name / size（OpenableColumns）。 */
    fun displayName(context: Context, uri: Uri): String? = queryColumn(context, uri, OpenableColumns.DISPLAY_NAME) as? String

    fun sizeOf(context: Context, uri: Uri): Long = (queryColumn(context, uri, OpenableColumns.SIZE) as? Long) ?: 0L

    private fun queryColumn(context: Context, uri: Uri, column: String): Any? = runCatching {
        context.contentResolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) {
                when (column) {
                    OpenableColumns.SIZE -> cursor.getLong(0)
                    else -> cursor.getString(0)
                }
            } else null
        }
    }.getOrNull()
}
