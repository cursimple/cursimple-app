package com.x500x.cursimple.core.data.widget

import android.content.Context
import com.x500x.cursimple.core.plugin.manifest.PluginWidgetSpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class ComponentWidgetDefinition(val componentId: String, val revision: String, val spec: PluginWidgetSpec) {
    val key: String get() = "$componentId/${spec.id}"
}

/** Only installed, enabled owners publish definitions. No widget is inferred from feed types. */
object ComponentWidgetRegistry {
    private val codec = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(ComponentWidgetDefinition.serializer())
    private val lock = Any()
    fun read(context: Context): List<ComponentWidgetDefinition> = synchronized(lock) {
        runCatching { codec.decodeFromString(serializer, file(context).readText()) }.getOrDefault(emptyList())
    }
    fun write(context: Context, definitions: List<ComponentWidgetDefinition>): Boolean = synchronized(lock) {
        val f = file(context)
        val value = codec.encodeToString(serializer, definitions.distinctBy { it.key })
        if (f.isFile && f.readText() == value) return false
        f.parentFile?.mkdirs()
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(value)
        check(tmp.renameTo(f)) { "Widget registry write failed" }
        true
    }
    private fun file(context: Context) = File(context.applicationContext.filesDir, "widget/component_widgets.json")
}

object ComponentWidgetBindings {
    private const val FILE = "component_widget_bindings"
    fun get(context: Context, id: Int): String? = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(id.toString(), null)
    fun set(context: Context, id: Int, key: String) {
        require(ComponentWidgetRegistry.read(context).any { it.key == key }) { "Widget owner unavailable" }
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(id.toString(), key).apply()
    }
    fun remove(context: Context, id: Int) { context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().remove(id.toString()).apply() }
}
