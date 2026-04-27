package com.kebiao.viewer.core.plugin.packageformat

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class PluginPackageReader {
    fun read(bytes: ByteArray): PluginPackageLayout {
        val files = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) {
                    files[entry.name] = zip.readBytes()
                }
                zip.closeEntry()
            }
        }
        return PluginPackageLayout(files)
    }
}
