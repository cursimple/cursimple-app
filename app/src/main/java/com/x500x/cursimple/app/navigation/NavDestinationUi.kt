package com.x500x.cursimple.app.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import com.x500x.cursimple.app.MainActivity
import com.x500x.cursimple.core.data.navigation.NavDestination

fun NavDestination.toAppScreen(): MainActivity.AppScreen = when (this) {
    NavDestination.Schedule -> MainActivity.AppScreen.Schedule
    NavDestination.Courses -> MainActivity.AppScreen.Courses
    NavDestination.Memos -> MainActivity.AppScreen.Memos
    NavDestination.Components -> MainActivity.AppScreen.ComponentsHub
    NavDestination.Plugins -> MainActivity.AppScreen.Plugins
    NavDestination.Reminders -> MainActivity.AppScreen.Reminders
    NavDestination.Settings -> MainActivity.AppScreen.Settings
    NavDestination.About -> MainActivity.AppScreen.About
}

/** Null for the me page, which is not a destination. */
fun MainActivity.AppScreen.toDestination(): NavDestination? = when (this) {
    MainActivity.AppScreen.Schedule -> NavDestination.Schedule
    MainActivity.AppScreen.Courses -> NavDestination.Courses
    MainActivity.AppScreen.Memos -> NavDestination.Memos
    MainActivity.AppScreen.ComponentsHub -> NavDestination.Components
    MainActivity.AppScreen.Plugins -> NavDestination.Plugins
    MainActivity.AppScreen.Reminders -> NavDestination.Reminders
    MainActivity.AppScreen.Settings -> NavDestination.Settings
    MainActivity.AppScreen.About -> NavDestination.About
    MainActivity.AppScreen.Me -> null
}

val NavDestination.labelRes: Int get() = toAppScreen().labelRes

val NavDestination.icon: ImageVector get() = toAppScreen().icon
