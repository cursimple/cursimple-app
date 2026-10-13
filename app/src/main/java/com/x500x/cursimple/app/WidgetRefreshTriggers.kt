package com.x500x.cursimple.app

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.withIndex

/**
 * Widgets keep what they were last drawn with, so a process start alone is no reason to redraw
 * them. A launcher holds that redraw behind a widget popup and animates the card when it returns.
 * Only later changes refresh, plus a starting value [refreshInitial] accepts, which a render that
 * raced ahead of it may have missed.
 */
internal fun <T> Flow<T>.widgetRefreshes(refreshInitial: (T) -> Boolean = { false }): Flow<T> =
    distinctUntilChanged().withIndex().filter { (index, value) -> index > 0 || refreshInitial(value) }.map { it.value }
