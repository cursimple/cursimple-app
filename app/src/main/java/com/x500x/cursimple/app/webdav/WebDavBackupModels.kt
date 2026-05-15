package com.x500x.cursimple.app.webdav

data class WebDavConfig(
    val url: String,
    val username: String,
    val password: String,
) {
    val isComplete: Boolean
        get() = url.isNotBlank() && username.isNotBlank() && password.isNotBlank()
}

data class WebDavBackupFile(
    val name: String,
    val href: String,
    val size: Long,
    val lastModified: String?,
)

