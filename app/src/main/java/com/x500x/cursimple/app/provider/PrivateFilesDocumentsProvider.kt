package com.x500x.cursimple.app.provider

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.system.Os
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileNotFoundException

class PrivateFilesDocumentsProvider : DocumentsProvider() {
    private lateinit var packageName: String
    private lateinit var dataDir: File
    private var userDeDataDir: File? = null
    private var androidDataDir: File? = null
    private var androidObbDir: File? = null
    private var appInfo: ApplicationInfo? = null

    private val appContext: Context
        get() = requireNotNull(context).applicationContext

    override fun attachInfo(context: Context, info: ProviderInfo) {
        super.attachInfo(context, info)
        packageName = context.packageName
        appInfo = context.applicationInfo
        dataDir = requireNotNull(context.filesDir.parentFile)
        val dataPath = dataDir.path
        if (dataPath.startsWith("/data/user/")) {
            userDeDataDir = File("/data/user_de/${dataPath.substringAfter("/data/user/")}")
        }
        androidDataDir = context.getExternalFilesDir(null)?.parentFile
        androidObbDir = context.obbDir
    }

    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val cursor = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)
        if (!isEnabled()) return cursor

        val applicationInfo = appInfo ?: appContext.applicationInfo
        cursor.newRow()
            .addColumn(cursor, Root.COLUMN_ROOT_ID, packageName)
            .addColumn(cursor, Root.COLUMN_DOCUMENT_ID, packageName)
            .addColumn(cursor, Root.COLUMN_TITLE, ROOT_TITLE)
            .addColumn(cursor, Root.COLUMN_SUMMARY, packageName)
            .addColumn(cursor, Root.COLUMN_ICON, applicationInfo.icon)
            .addColumn(cursor, Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_CREATE or Root.FLAG_SUPPORTS_IS_CHILD)
            .addColumn(cursor, Root.COLUMN_MIME_TYPES, "*/*")
            .addColumn(cursor, Root.COLUMN_AVAILABLE_BYTES, dataDir.freeSpace)
        return cursor
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        ensureEnabled()
        val cursor = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        addDocument(cursor, documentId)
        return cursor
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        ensureEnabled()
        val cursor = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        val normalizedParentId = parentDocumentId.trimEnd('/')
        val parent = resolveDocument(normalizedParentId)
        if (parent == null) {
            rootDirs.forEach { rootType ->
                rootType.dir()?.takeIf { it.exists() }?.let { dir ->
                    addDocument(cursor, "$packageName/${rootType.id}", dir)
                }
            }
            return cursor
        }
        if (!parent.isDirectory) throw FileNotFoundException(parentDocumentId)
        parent.listFiles()
            .orEmpty()
            .sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
            .forEach { child ->
                val childId = if (normalizedParentId == packageName) {
                    "$packageName/${child.name}"
                } else {
                    "$normalizedParentId/${child.name}"
                }
                addDocument(cursor, childId, child)
            }
        return cursor
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        ensureEnabled()
        val file = resolveDocument(documentId, checkExists = false)
            ?: throw FileNotFoundException(documentId)
        if (file.isDirectory) throw FileNotFoundException(documentId)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))
    }

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        ensureEnabled()
        val parent = resolveDocument(parentDocumentId) ?: throw FileNotFoundException(parentDocumentId)
        if (!parent.isDirectory) throw FileNotFoundException(parentDocumentId)
        val child = uniqueFile(parent, displayName.sanitizeFileName())
        val created = if (mimeType == Document.MIME_TYPE_DIR) child.mkdirs() else child.createNewFile()
        check(created) { "无法创建文件" }
        return "${parentDocumentId.trimEnd('/')}/${child.name}"
    }

    override fun deleteDocument(documentId: String) {
        ensureEnabled()
        val file = resolveDocument(documentId) ?: throw FileNotFoundException("不允许删除根目录")
        val deleted = if (file.isDirectory) file.deleteRecursively() else file.delete()
        check(deleted) { "删除失败" }
    }

    override fun removeDocument(documentId: String, parentDocumentId: String) {
        deleteDocument(documentId)
    }

    override fun renameDocument(documentId: String, displayName: String): String {
        ensureEnabled()
        val file = resolveDocument(documentId) ?: throw FileNotFoundException("不允许重命名根目录")
        val parent = file.parentFile ?: throw FileNotFoundException(documentId)
        val target = uniqueFile(parent, displayName.sanitizeFileName().ifBlank { file.name })
        check(file.renameTo(target)) { "重命名失败" }
        return documentId.substringBeforeLast('/', packageName) + "/" + target.name
    }

    override fun moveDocument(
        sourceDocumentId: String,
        sourceParentDocumentId: String,
        targetParentDocumentId: String,
    ): String {
        ensureEnabled()
        val source = resolveDocument(sourceDocumentId) ?: throw FileNotFoundException(sourceDocumentId)
        val targetDir = resolveDocument(targetParentDocumentId) ?: throw FileNotFoundException(targetParentDocumentId)
        if (!targetDir.isDirectory) throw FileNotFoundException(targetParentDocumentId)
        val target = File(targetDir, source.name)
        check(!target.exists() && source.renameTo(target)) { "移动失败" }
        return "${targetParentDocumentId.trimEnd('/')}/${target.name}"
    }

    override fun getDocumentType(documentId: String): String {
        ensureEnabled()
        return resolveDocument(documentId)?.mimeType() ?: Document.MIME_TYPE_DIR
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        val parent = parentDocumentId.trimEnd('/')
        val child = documentId.trimEnd('/')
        return child == parent || child.startsWith("$parent/")
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (!method.startsWith(MT_METHOD_PREFIX)) return super.call(method, arg, extras)
        ensureEnabled()
        val documentId = extras?.documentIdFromUri() ?: return Bundle().withResult(false, "缺少 uri")
        val file = resolveDocument(documentId, checkExists = false)
        val out = Bundle()
        when (method) {
            METHOD_SET_LAST_MODIFIED -> {
                val time = extras.getLong("time")
                out.putBoolean("result", file?.setLastModified(time) == true)
            }

            METHOD_SET_PERMISSIONS -> {
                val target = file ?: return out.withResult(false, "文件不存在")
                runCatching {
                    Os.chmod(target.path, extras.getInt("permissions"))
                }.onSuccess {
                    out.putBoolean("result", true)
                }.onFailure {
                    out.withResult(false, it.message.orEmpty())
                }
            }

            METHOD_CREATE_SYMLINK -> {
                val target = file ?: return out.withResult(false, "文件不存在")
                runCatching {
                    Os.symlink(extras.getString("path").orEmpty(), target.path)
                }.onSuccess {
                    out.putBoolean("result", true)
                }.onFailure {
                    out.withResult(false, it.message.orEmpty())
                }
            }

            else -> out.withResult(false, "不支持的方法：$method")
        }
        return out
    }

    private fun addDocument(cursor: MatrixCursor, documentId: String, file: File? = null) {
        val resolved = file ?: resolveDocument(documentId)
        if (resolved == null) {
            val applicationInfo = appInfo ?: appContext.applicationInfo
            cursor.newRow()
                .addColumn(cursor, Document.COLUMN_DOCUMENT_ID, packageName)
                .addColumn(cursor, Document.COLUMN_DISPLAY_NAME, ROOT_TITLE)
                .addColumn(cursor, Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR)
                .addColumn(cursor, Document.COLUMN_FLAGS, Document.FLAG_DIR_SUPPORTS_CREATE)
                .addColumn(cursor, Document.COLUMN_SIZE, 0L)
                .addColumn(cursor, Document.COLUMN_LAST_MODIFIED, 0L)
            return
        }

        val rootName = rootDisplayName(resolved)
        val flags = when {
            rootName != null -> {
                if (resolved.isDirectory && resolved.canWrite()) Document.FLAG_DIR_SUPPORTS_CREATE else 0
            }

            resolved.isDirectory -> {
                var dirFlags = 0
                if (resolved.canWrite()) dirFlags = dirFlags or Document.FLAG_DIR_SUPPORTS_CREATE
                if (resolved.parentFile?.canWrite() == true) {
                    dirFlags = dirFlags or Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_RENAME
                }
                dirFlags
            }

            else -> {
                var fileFlags = 0
                if (resolved.canWrite()) fileFlags = fileFlags or Document.FLAG_SUPPORTS_WRITE
                if (resolved.parentFile?.canWrite() == true) {
                    fileFlags = fileFlags or Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_RENAME
                }
                fileFlags
            }
        }

        val path = resolved.path
        cursor.newRow()
            .addColumn(cursor, Document.COLUMN_DOCUMENT_ID, documentId)
            .addColumn(cursor, Document.COLUMN_DISPLAY_NAME, rootName ?: resolved.name)
            .addColumn(cursor, Document.COLUMN_MIME_TYPE, resolved.mimeType())
            .addColumn(cursor, Document.COLUMN_FLAGS, flags)
            .addColumn(cursor, Document.COLUMN_SIZE, if (resolved.isFile) resolved.length() else 0L)
            .addColumn(cursor, Document.COLUMN_LAST_MODIFIED, resolved.lastModified())
            .addColumn(cursor, COLUMN_MT_PATH, resolved.absolutePath)
            .addColumn(cursor, COLUMN_MT_EXTRAS, if (rootName == null) path.mtExtras() else null)
    }

    private fun resolveDocument(documentId: String, checkExists: Boolean = true): File? {
        val normalizedId = documentId.trimEnd('/')
        if (normalizedId == packageName) return null
        if (!normalizedId.startsWith("$packageName/")) throw FileNotFoundException(documentId)
        val relative = normalizedId.removePrefix("$packageName/")
        val rootName = relative.substringBefore('/')
        val subPath = relative.substringAfter('/', "")
        val rootType = rootDirs.firstOrNull { it.id == rootName } ?: throw FileNotFoundException(documentId)
        val rootDir = rootType.dir() ?: throw FileNotFoundException(documentId)
        val target = if (subPath.isBlank()) rootDir else File(rootDir, subPath)
        val canonicalRoot = rootDir.canonicalFile
        val canonicalTarget = if (checkExists) {
            target.canonicalFile
        } else {
            target.parentFile?.canonicalFile?.let { File(it, target.name) } ?: target.absoluteFile
        }
        if (canonicalTarget != canonicalRoot && !canonicalTarget.path.startsWith(canonicalRoot.path + File.separator)) {
            throw FileNotFoundException(documentId)
        }
        if (checkExists) {
            runCatching { Os.lstat(target.path) }
                .getOrElse { throw FileNotFoundException(documentId) }
        }
        return target
    }

    private fun RootType.dir(): File? = when (this) {
        RootType.Data -> dataDir
        RootType.Files -> appContext.filesDir
        RootType.Cache -> appContext.cacheDir
        RootType.SharedPrefs -> File(dataDir, "shared_prefs")
        RootType.DataStore -> File(appContext.filesDir.parentFile, "datastore")
        RootType.Databases -> appContext.getDatabasePath("placeholder").parentFile
        RootType.AndroidData -> androidDataDir
        RootType.AndroidObb -> androidObbDir
        RootType.UserDeData -> userDeDataDir
    }

    private val rootDirs: List<RootType>
        get() = RootType.entries

    private fun rootDisplayName(file: File): String? = rootDirs.firstOrNull { type ->
        val rootPath = type.dir()?.let { runCatching { it.canonicalPath }.getOrNull() }
        rootPath != null && rootPath == runCatching { file.canonicalPath }.getOrNull()
    }?.title

    private fun uniqueFile(parent: File, name: String): File {
        var candidate = File(parent, name)
        if (!candidate.exists()) return candidate
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var index = 2
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

    private fun isEnabled(): Boolean =
        appContext.getSharedPreferences(PRIVATE_FILES_PROVIDER_PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_PRIVATE_FILES_PROVIDER_ENABLED, false)

    private fun File.mimeType(): String {
        if (isDirectory) return Document.MIME_TYPE_DIR
        val ext = extension.lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    private fun String.sanitizeFileName(): String =
        replace('/', '_').replace('\\', '_').ifBlank { "untitled" }

    private fun String.mtExtras(): String? = runCatching {
        val stat = Os.lstat(this)
        buildString {
            append(stat.st_mode).append('|').append(stat.st_uid).append('|').append(stat.st_gid)
            if ((stat.st_mode and SYMLINK_MODE_MASK) == SYMLINK_MODE) {
                append('|').append(Os.readlink(this@mtExtras))
            }
        }
    }.getOrNull()

    private fun Bundle.documentIdFromUri(): String? {
        @Suppress("DEPRECATION")
        val uri = getParcelable<android.net.Uri>("uri") ?: return null
        val segments = uri.pathSegments
        return when {
            segments.size >= 4 -> segments[3]
            segments.size >= 2 -> segments[1]
            else -> null
        }
    }

    private fun Bundle.withResult(success: Boolean, message: String? = null): Bundle {
        putBoolean("result", success)
        if (!message.isNullOrBlank()) putString("message", message)
        return this
    }

    private fun MatrixCursor.RowBuilder.addColumn(
        cursor: MatrixCursor,
        columnName: String,
        value: Any?,
    ): MatrixCursor.RowBuilder {
        if (cursor.columnNames.contains(columnName)) add(columnName, value)
        return this
    }

    private enum class RootType(val id: String, val title: String) {
        Data("data", "data"),
        Files("files", "files"),
        Cache("cache", "cache"),
        SharedPrefs("shared_prefs", "shared_prefs"),
        DataStore("datastore", "datastore"),
        Databases("databases", "databases"),
        AndroidData("android_data", "Android/data"),
        AndroidObb("android_obb", "Android/obb"),
        UserDeData("user_de_data", "user_de_data"),
    }

    private companion object {
        const val COLUMN_MT_EXTRAS = "mt_extras"
        const val COLUMN_MT_PATH = "mt_path"
        const val MT_METHOD_PREFIX = "mt:"
        const val METHOD_SET_LAST_MODIFIED = "mt:setLastModified"
        const val METHOD_SET_PERMISSIONS = "mt:setPermissions"
        const val METHOD_CREATE_SYMLINK = "mt:createSymlink"
        const val SYMLINK_MODE_MASK = 0xF000
        const val SYMLINK_MODE = 0xA000
        const val ROOT_TITLE = "课简"

        const val PRIVATE_FILES_PROVIDER_PREFS = "private_files_provider"
        const val KEY_PRIVATE_FILES_PROVIDER_ENABLED = "private_files_provider_enabled"
        val DEFAULT_ROOT_PROJECTION = arrayOf(
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_DOCUMENT_ID,
            Root.COLUMN_TITLE,
            Root.COLUMN_SUMMARY,
            Root.COLUMN_ICON,
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
            COLUMN_MT_PATH,
            COLUMN_MT_EXTRAS,
        )
    }
}
