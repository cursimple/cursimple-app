package com.x500x.cursimple.feature.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

/**
 * Fixed provider slots so each component-declared widget is its own launcher entry. A slot is
 * enabled only while a widget is assigned to it; the host never names or interprets the owner.
 */
object ComponentWidgetSlots {
    const val COUNT = 6
    private const val PREFS = "component_widget_slots"
    private const val CLASS_PREFIX = "com.x500x.cursimple.feature.widget.ComponentWidgetSlot"
    private val lock = Any()

    fun provider(context: Context, slot: Int) = ComponentName(context.packageName, "$CLASS_PREFIX${slot}Receiver")
    fun vendorProvider(context: Context, slot: Int) = ComponentName(context.packageName, "$CLASS_PREFIX${slot}ReceiverMIUI")
    fun providers(context: Context, slot: Int) = listOf(provider(context, slot), vendorProvider(context, slot))

    fun slotOf(provider: ComponentName): Int? = provider.className.takeIf { it.startsWith(CLASS_PREFIX) }
        ?.removePrefix(CLASS_PREFIX)?.substringBefore("Receiver")?.toIntOrNull()?.takeIf { it in 0 until COUNT }

    fun assignedKey(context: Context, slot: Int): String? = prefs(context).getString("slot_$slot", null)

    fun slotFor(context: Context, key: String): Int? = (0 until COUNT).firstOrNull { assignedKey(context, it) == key }

    /** Keeps existing assignments stable; a returning owner reclaims its previous slot. */
    fun assign(context: Context, keys: List<String>): Boolean = synchronized(lock) {
        val prefs = prefs(context)
        val current = (0 until COUNT).map { prefs.getString("slot_$it", null) }
        val last = (0 until COUNT).map { prefs.getString("last_$it", null) }
        val next = current.map { key -> key?.takeIf { it in keys } }.toMutableList()
        val manager = AppWidgetManager.getInstance(context)
        fun placed(slot: Int) = providers(context, slot).any { runCatching { manager.getAppWidgetIds(it).isNotEmpty() }.getOrDefault(false) }
        for (key in keys.distinct().filter { it !in next }) {
            val free = (0 until COUNT).filter { next[it] == null }
            val slot = free.firstOrNull { last[it] == key }
                ?: free.firstOrNull { last[it] == null && !placed(it) }
                ?: free.firstOrNull { !placed(it) }
                ?: break
            next[slot] = key
        }
        if (next == current) return false
        prefs.edit().apply {
            next.forEachIndexed { slot, key ->
                if (key == null) remove("slot_$slot") else putString("slot_$slot", key).putString("last_$slot", key)
            }
        }.apply()
        true
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

class ComponentWidgetSlot0Receiver : ComponentWidgetReceiver()
class ComponentWidgetSlot0ReceiverMIUI : ComponentWidgetReceiver()
class ComponentWidgetSlot1Receiver : ComponentWidgetReceiver()
class ComponentWidgetSlot1ReceiverMIUI : ComponentWidgetReceiver()
class ComponentWidgetSlot2Receiver : ComponentWidgetReceiver()
class ComponentWidgetSlot2ReceiverMIUI : ComponentWidgetReceiver()
class ComponentWidgetSlot3Receiver : ComponentWidgetReceiver()
class ComponentWidgetSlot3ReceiverMIUI : ComponentWidgetReceiver()
class ComponentWidgetSlot4Receiver : ComponentWidgetReceiver()
class ComponentWidgetSlot4ReceiverMIUI : ComponentWidgetReceiver()
class ComponentWidgetSlot5Receiver : ComponentWidgetReceiver()
class ComponentWidgetSlot5ReceiverMIUI : ComponentWidgetReceiver()
