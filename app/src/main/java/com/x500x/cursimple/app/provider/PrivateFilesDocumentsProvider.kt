package com.x500x.cursimple.app.provider

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.net.Uri
import android.provider.DocumentsContract
import android.system.OsConstants
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
import com.x500x.cursimple.R

class PrivateFilesDocumentsProvider : DocumentsProvider() {
    private lateinit var authority: String
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
        authority = info.authority
        packageName = context.packageName
        appInfo = context.applicationInfo
        dataDir = requireNotNull(context.filesDir.parentFile)
        val dataPath = dataDir.path
        // These are private multi-user storage paths, not shared storage; device-protected paths have no reverse lookup API.
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
            .addColumn(cursor, Root.COLUMN_TITLE, appContext.getString(R.string.app_name))
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
                // A private file manager must not turn symlinks into grants outside its roots.
                runCatching { resolveDocument(childId) }.getOrNull()?.let { addDocument(cursor, childId, it) }
            }
        return cursor
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        ensureEnabled()
        val file = resolveDocument(documentId) ?: throw FileNotFoundException(documentId)
        if (!file.isFile) throw FileNotFoundException(documentId)
        val parsed = ParcelFileDescriptor.parseMode(mode)
        var flags = when (parsed and ParcelFileDescriptor.MODE_READ_WRITE) {
            ParcelFileDescriptor.MODE_READ_ONLY -> OsConstants.O_RDONLY
            ParcelFileDescriptor.MODE_WRITE_ONLY -> OsConstants.O_WRONLY
            else -> OsConstants.O_RDWR
        }
        if (parsed and ParcelFileDescriptor.MODE_TRUNCATE != 0) flags = flags or OsConstants.O_TRUNC
        if (parsed and ParcelFileDescriptor.MODE_APPEND != 0) flags = flags or OsConstants.O_APPEND
        if (android.os.Build.VERSION.SDK_INT >= 27) flags = flags or OsConstants.O_CLOEXEC
        signal?.throwIfCanceled()
        return try {
            val fd = Os.open(file.path, flags or OsConstants.O_NOFOLLOW, 0)
            try {
                ParcelFileDescriptor.dup(fd)
            } finally { Os.close(fd) }
        } catch (error: android.system.ErrnoException) {
            throw FileNotFoundException(documentId).apply { initCause(error) }
        }
    }

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        ensureEnabled()
        val parent = resolveDocument(parentDocumentId) ?: throw FileNotFoundException(parentDocumentId)
        if (!parent.isDirectory) throw FileNotFoundException(parentDocumentId)
        val child = uniqueFile(parent, displayName.sanitizeFileName())
        val created = if (mimeType == Document.MIME_TYPE_DIR) child.mkdirs() else child.createNewFile()
        check(created) { appContext.getString(R.string.provider_root_create_failed) }
        return "${parentDocumentId.trimEnd('/')}/${child.name}"
    }

    override fun deleteDocument(documentId: String) {
        ensureEnabled()
        val file = resolveDocument(documentId) ?: throw FileNotFoundException(appContext.getString(R.string.provider_root_delete_denied))
        requireMutableDocument(file)
        val deleted = deleteWithoutFollowingLinks(file)
        check(deleted) { appContext.getString(R.string.provider_delete_failed) }
    }

    override fun removeDocument(documentId: String, parentDocumentId: String) {
        deleteDocument(documentId)
    }

    override fun renameDocument(documentId: String, displayName: String): String {
        ensureEnabled()
        val file = resolveDocument(documentId) ?: throw FileNotFoundException(appContext.getString(R.string.provider_root_rename_denied))
        requireMutableDocument(file)
        val parent = file.parentFile ?: throw FileNotFoundException(documentId)
        val target = uniqueFile(parent, displayName.sanitizeFileName().ifBlank { file.name })
        check(file.renameTo(target)) { appContext.getString(R.string.provider_rename_failed) }
        return documentId.substringBeforeLast('/', packageName) + "/" + target.name
    }

    override fun moveDocument(
        sourceDocumentId: String,
        sourceParentDocumentId: String,
        targetParentDocumentId: String,
    ): String {
        ensureEnabled()
        val source = resolveDocument(sourceDocumentId) ?: throw FileNotFoundException(sourceDocumentId)
        requireMutableDocument(source)
        val targetDir = resolveDocument(targetParentDocumentId) ?: throw FileNotFoundException(targetParentDocumentId)
        if (!targetDir.isDirectory) throw FileNotFoundException(targetParentDocumentId)
        val target = File(targetDir, source.name)
        check(!target.exists() && source.renameTo(target)) { appContext.getString(R.string.provider_move_failed) }
        return "${targetParentDocumentId.trimEnd('/')}/${target.name}"
    }

    override fun getDocumentType(documentId: String): String {
        ensureEnabled()
        return resolveDocument(documentId)?.mimeType() ?: Document.MIME_TYPE_DIR
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        ensureEnabled()
        return runCatching {
            resolveDocument(parentDocumentId)
            resolveDocument(documentId)
            documentId == parentDocumentId || documentId.startsWith("$parentDocumentId/")
        }.getOrDefault(false)
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (!method.startsWith(MT_METHOD_PREFIX)) return super.call(method, arg, extras)
        ensureEnabled()
        val uri = extras?.documentUri() ?: throw SecurityException("Invalid document URI")
        val uid = Binder.getCallingUid()
        if (uid != android.os.Process.myUid() &&
            appContext.checkCallingPermission("android.permission.MANAGE_DOCUMENTS") != PackageManager.PERMISSION_GRANTED &&
            appContext.checkUriPermission(uri, Binder.getCallingPid(), uid, Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != PackageManager.PERMISSION_GRANTED
        ) throw SecurityException("Write permission required")
        val documentId = DocumentsContract.getDocumentId(uri)
        if (method == METHOD_CREATE_SYMLINK) return Bundle().withResult(false, "Symbolic links are not supported")
        val file = resolveDocument(documentId)
        file?.let { requireMutableDocument(it) }
        val out = Bundle()
        when (method) {
            METHOD_SET_LAST_MODIFIED -> {
                val time = extras.getLong("time")
                out.putBoolean("result", file?.setLastModified(time) == true)
            }

            METHOD_SET_PERMISSIONS -> {
                val target = file ?: return out.withResult(false, appContext.getString(R.string.provider_file_missing))
                runCatching {
                    val permissions = extras.getInt("permissions")
                    require(permissions >= 0 && permissions and 0x1C0 == permissions) { "Only owner permissions are supported" }
                    Os.chmod(target.path, permissions)
                }.onSuccess {
                    out.putBoolean("result", true)
                }.onFailure {
                    out.withResult(false, it.message.orEmpty())
                }
            }


            else -> out.withResult(false, appContext.getString(R.string.provider_method_unsupported, method))
        }
        return out
    }

    private fun addDocument(cursor: MatrixCursor, documentId: String, file: File? = null) {
        val resolved = file ?: resolveDocument(documentId)
        if (resolved == null) {
            val applicationInfo = appInfo ?: appContext.applicationInfo
            cursor.newRow()
                .addColumn(cursor, Document.COLUMN_DOCUMENT_ID, packageName)
                .addColumn(cursor, Document.COLUMN_DISPLAY_NAME, appContext.getString(R.string.app_name))
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
        if (documentId == packageName) return null
        val segments = documentId.split('/')
        if (segments.firstOrNull() != packageName || segments.size < 2 ||
            segments.any { it.isBlank() || it == "." || it == ".." || it.contains('\u0000') || it.contains('\\') }
        ) throw FileNotFoundException(documentId)
        val rootType = rootDirs.firstOrNull { it.id == segments[1] } ?: throw FileNotFoundException(documentId)
        val root = rootType.dir()?.canonicalFile ?: throw FileNotFoundException(documentId)
        var target = root
        for ((index, segment) in segments.drop(2).withIndex()) {
            target = File(target, segment)
            val stat = runCatching { Os.lstat(target.path) }.getOrNull()
            if (stat != null && OsConstants.S_ISLNK(stat.st_mode)) throw FileNotFoundException(documentId)
            if (stat == null && (checkExists || index != segments.size - 3)) throw FileNotFoundException(documentId)
        }
        val canonicalTarget = target.canonicalFile
        if (canonicalTarget != root && !canonicalTarget.path.startsWith(root.path + File.separator)) {
            throw FileNotFoundException(documentId)
        }
        if (checkExists && !target.exists()) throw FileNotFoundException(documentId)
        return target
    }

    private fun requireMutableDocument(file: File) {
        if (rootDisplayName(file) != null) throw FileNotFoundException("Root modification denied")
    }

    private fun deleteWithoutFollowingLinks(file: File): Boolean {
        val stat = Os.lstat(file.path)
        if (OsConstants.S_ISDIR(stat.st_mode)) {
            val children = file.listFiles() ?: return false
            if (!children.all { deleteWithoutFollowingLinks(it) }) return false
        }
        return file.delete()
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
        if (!isEnabled()) throw FileNotFoundException(appContext.getString(R.string.provider_access_disabled))
    }

    private fun isEnabled(): Boolean =
        appContext.getSharedPreferences(PRIVATE_FILES_PROVIDER_PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_PRIVATE_FILES_PROVIDER_ENABLED, false)

    private fun File.mimeType(): String {
        if (isDirectory) return Document.MIME_TYPE_DIR
        val ext = extension.lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    private fun String.sanitizeFileName(): String {
        require(this != "." && this != ".." && !contains('\u0000') && length <= 255) { "Invalid file name" }
        return replace('/', '_').replace('\\', '_').ifBlank { "untitled" }
    }

    private fun String.mtExtras(): String? = runCatching {
        val stat = Os.lstat(this)
        buildString {
            append(stat.st_mode).append('|').append(stat.st_uid).append('|').append(stat.st_gid)
            if ((stat.st_mode and SYMLINK_MODE_MASK) == SYMLINK_MODE) {
                append('|').append(Os.readlink(this@mtExtras))
            }
        }
    }.getOrNull()

    private fun Bundle.documentUri(): Uri? {
        @Suppress("DEPRECATION")
        val uri = getParcelable<Uri>("uri") ?: return null
        return uri.takeIf {
            it.scheme == "content" && it.authority == authority && DocumentsContract.isDocumentUri(appContext, it)
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
