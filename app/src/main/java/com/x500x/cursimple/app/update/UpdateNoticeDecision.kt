package com.x500x.cursimple.app.update

/**
 * Latest discovered [versionCode] and user-selected [ignoredVersionCode]; automatic checks use
 * badges only.
 */
data class UpdateNoticeState(
    val versionCode: Int = 0,
    val versionName: String = "",
    val ignoredVersionCode: Int? = null,
)

/** Show a badge for a newer installed-code comparison unless that version is ignored. */
fun shouldShowUpdateBadge(state: UpdateNoticeState, currentVersionCode: Int): Boolean =
    state.versionCode > currentVersionCode && state.versionCode != state.ignoredVersionCode
