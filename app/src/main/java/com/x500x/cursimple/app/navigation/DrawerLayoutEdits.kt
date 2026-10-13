package com.x500x.cursimple.app.navigation

import com.x500x.cursimple.core.data.navigation.DrawerLayout
import com.x500x.cursimple.core.data.navigation.NavDestination

/** Hides or restores [destination]; locked destinations always stay visible. */
fun DrawerLayout.toggled(destination: NavDestination): DrawerLayout = when {
    destination.locked -> this
    destination in hidden -> copy(hidden = hidden - destination)
    else -> copy(hidden = hidden + destination)
}

/** Moves the item at [from] to [to] (clamped); out-of-range sources leave the order unchanged. */
fun DrawerLayout.moved(from: Int, to: Int): DrawerLayout {
    if (from !in order.indices) return this
    val next = order.toMutableList()
    next.add(to.coerceIn(0, order.lastIndex), next.removeAt(from))
    return copy(order = next)
}
