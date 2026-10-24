package com.x500x.cursimple.core.plugin.packageformat

/** Reject recursive/oversized metadata before kotlinx.serialization walks it. */
internal fun requireBoundedPluginJson(text: String) {
    require(text.length <= 1024 * 1024 && text.toByteArray(Charsets.UTF_8).size <= 1024 * 1024) { "Plugin JSON exceeds 1 MiB" }
    var depth = 0
    var quoted = false
    var escaped = false
    for (char in text) {
        if (quoted) {
            if (escaped) escaped = false
            else if (char == '\\') escaped = true
            else if (char == '"') quoted = false
        } else when (char) {
            '"' -> quoted = true
            '{', '[' -> { depth++; require(depth <= 64) { "Plugin JSON nesting limit exceeded" } }
            '}', ']' -> depth--
        }
    }
}
