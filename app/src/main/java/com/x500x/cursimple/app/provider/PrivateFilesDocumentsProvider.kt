package com.x500x.cursimple.app.provider

import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileNotFoundException

private val Context.privateProviderPreferencesDataStore by preferencesDataStore(name = "user_preferences")

class PrivateFilesDocumentsProvider : DocumentsProvider() {
    private val appContext: Context
        get() = requireNotNull(context).applicationContext

    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val cursor = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)
        if (!isEnabled()) return cursor
        addRoot(cursor, RootInfo(FILES_ROOT_ID, "私有 files", appContext.filesDir))
        addRoot(cursor, RootInfo(CACHE_ROOT_ID, "私有 cache", appContext.cacheDir))
        return cursor
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        ensureEnabled()
        val cursor = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        addDocument(cursor, resolveDocument(documentId))
        return cursor
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        ensureEnabled()
        val parent = resolveDocument(parentDocumentId)
        if (!parent.file.isDirectory) throw FileNotFoundException(parentDocumentId)
        val cursor = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        parent.file.listFiles()
            .orEmpty()
            .sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
            .forEach { child -> addDocument(cursor, parent.root.documentFor(child)) }
        return cursor
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        ensureEnabled()
        val document = resolveDocument(documentId)
        if (document.file.isDirectory) throw FileNotFoundException(documentId)
        val accessMode = ParcelFileDescriptor.parseMode(mode)
        return ParcelFileDescriptor.open(document.file, accessMode)
    }

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        ensureEnabled()
        val parent = resolveDocument(parentDocumentId)
        if (!parent.file.isDirectory) throw FileNotFoundException(parentDocumentId)
        val sanitizedName = displayName.replace('/', '_').replace('\\', '_').ifBlank { "untitled" }
        val child = uniqueFile(parent.file, sanitizedName)
        if (mimeType == Document.MIME_TYPE_DIR) {
            check(child.mkdirs()) { "无法创建目录" }
        } else {
            check(child.createNewFile()) { "无法创建文件" }
        }
        return parent.root.documentFor(child).id
    }

    override fun deleteDocument(documentId: String) {
        ensureEnabled()
        val document = resolveDocument(documentId)
        if (document.isRoot) throw FileNotFoundException("不允许删除根目录")
        val deleted = if (document.file.isDirectory) {
            document.file.deleteRecursively()
        } else {
            document.file.delete()
        }
        check(deleted) { "删除失败" }
    }

    override fun renameDocument(documentId: String, displayName: String): String {
        ensureEnabled()
        val document = resolveDocument(documentId)
        if (document.isRoot) throw FileNotFoundException("不允许重命名根目录")
        val sanitizedName = displayName.replace('/', '_').replace('\\', '_').ifBlank { document.file.name }
        val target = uniqueFile(document.file.parentFile ?: document.root.rootDir, sanitizedName)
        check(document.file.renameTo(target)) { "重命名失败" }
        return document.root.documentFor(target).id
    }

    private fun addRoot(cursor: MatrixCursor, root: RootInfo) {
        root.rootDir.mkdirs()
        cursor.newRow()
            .add(Root.COLUMN_ROOT_ID, root.rootId)
            .add(Root.COLUMN_DOCUMENT_ID, root.rootId)
            .add(Root.COLUMN_TITLE, root.title)
            .add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_CREATE or Root.FLAG_SUPPORTS_RECENTS)
            .add(Root.COLUMN_MIME_TYPES, "*/*")
            .add(Root.COLUMN_AVAILABLE_BYTES, root.rootDir.freeSpace)
    }

    private fun addDocument(cursor: MatrixCursor, document: DocumentInfo) {
        val file = document.file
        val flags = when {
            file.isDirectory -> Document.FLAG_DIR_SUPPORTS_CREATE or
                Document.FLAG_SUPPORTS_DELETE or
                Document.FLAG_SUPPORTS_RENAME

            else -> Document.FLAG_SUPPORTS_WRITE or
                Document.FLAG_SUPPORTS_DELETE or
                Document.FLAG_SUPPORTS_RENAME
        }
        cursor.newRow()
            .add(Document.COLUMN_DOCUMENT_ID, document.id)
            .add(Document.COLUMN_DISPLAY_NAME, if (document.isRoot) document.root.title else file.name)
            .add(Document.COLUMN_MIME_TYPE, file.mimeType())
            .add(Document.COLUMN_FLAGS, flags)
            .add(Document.COLUMN_SIZE, if (file.isFile) file.length() else null)
            .add(Document.COLUMN_LAST_MODIFIED, file.lastModified())
    }

    private fun resolveDocument(documentId: String): DocumentInfo {
        val root = rootFor(documentId.substringBefore(':'))
        val relative = documentId.substringAfter(':', "")
        val target = if (relative.isBlank()) root.rootDir else File(root.rootDir, relative)
        val canonicalRoot = root.rootDir.canonicalFile
        val canonicalTarget = target.canonicalFile
        if (canonicalTarget != canonicalRoot && !canonicalTarget.path.startsWith(canonicalRoot.path + File.separator)) {
            throw FileNotFoundException(documentId)
        }
        if (!canonicalTarget.exists()) throw FileNotFoundException(documentId)
        return root.documentFor(canonicalTarget)
    }

    private fun rootFor(rootId: String): RootInfo = when (rootId) {
        FILES_ROOT_ID -> RootInfo(FILES_ROOT_ID, "私有 files", appContext.filesDir)
        CACHE_ROOT_ID -> RootInfo(CACHE_ROOT_ID, "私有 cache", appContext.cacheDir)
        else -> throw FileNotFoundException(rootId)
    }

    private fun RootInfo.documentFor(file: File): DocumentInfo {
        val canonicalRoot = rootDir.canonicalFile
        val canonicalFile = file.canonicalFile
        val relative = canonicalFile.relativeToOrNull(canonicalRoot)?.path.orEmpty()
        val id = if (relative.isBlank()) rootId else "$rootId:$relative"
        return DocumentInfo(id, this, canonicalFile, relative.isBlank())
    }

    private fun uniqueFile(parent: File, name: String): File {
        var candidate = File(parent, name)
        if (!candidate.exists()) return candidate
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var index = 1
        while (candidate.exists()) {
            val nextName = if (ext.isBlank()) "$base ($index)" else "$base ($index).$ext"
            candidate = File(parent, nextName)
            index += 1
        }
        return candidate
    }

    private fun ensureEnabled() {
        if (!isEnabled()) throw FileNotFoundException("私有目录访问未开启")
    }

    private fun isEnabled(): Boolean = runBlocking {
        appContext.privateProviderPreferencesDataStore.data.first()[KEY_PRIVATE_FILES_PROVIDER_ENABLED] ?: false
    }

    private fun File.mimeType(): String {
        if (isDirectory) return Document.MIME_TYPE_DIR
        val ext = extension.lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    private data class RootInfo(
        val rootId: String,
        val title: String,
        val rootDir: File,
    )

    private data class DocumentInfo(
        val id: String,
        val root: RootInfo,
        val file: File,
        val isRoot: Boolean,
    )

    private companion object {
        const val FILES_ROOT_ID = "files"
        const val CACHE_ROOT_ID = "cache"
        val KEY_PRIVATE_FILES_PROVIDER_ENABLED = booleanPreferencesKey("private_files_provider_enabled")
        val DEFAULT_ROOT_PROJECTION = arrayOf(
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_DOCUMENT_ID,
            Root.COLUMN_TITLE,
            Root.COLUMN_FLAGS,
            Root.COLUMN_MIME_TYPES,
            Root.COLUMN_AVAILABLE_BYTES,
        )
        val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_FLAGS,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
        )
    }
}
