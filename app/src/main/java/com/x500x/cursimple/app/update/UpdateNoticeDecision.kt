package com.x500x.cursimple.app.update

/**
 * 更新提醒的当前状态。
 *
 * [versionCode] 为最近一次检查发现的版本，[mutedVersionCode] 是用户选择不再弹窗的版本，
 * [ignoredVersionCode] 是用户选择忽略的版本。
 */
data class UpdateNoticeState(
    val versionCode: Int = 0,
    val versionName: String = "",
    val mutedVersionCode: Int? = null,
    val ignoredVersionCode: Int? = null,
)

/**
 * 设置入口是否显示角标。
 *
 * 发现的版本高于当前安装版本即显示；用户忽略该版本后角标消失，
 * 只选了不再提醒时角标保留。
 */
fun shouldShowUpdateBadge(state: UpdateNoticeState, currentVersionCode: Int): Boolean =
    state.versionCode > currentVersionCode && state.versionCode != state.ignoredVersionCode

/**
 * 是否弹出更新提示。
 *
 * 在角标条件之上还要求该版本没被选过不再提醒，且本次启动还没弹过；
 * 只点了下次再说时 [promptedThisSession] 为真，下次启动会重新弹。
 */
fun shouldPromptUpdate(
    state: UpdateNoticeState,
    currentVersionCode: Int,
    promptedThisSession: Boolean,
): Boolean = shouldShowUpdateBadge(state, currentVersionCode) &&
    state.versionCode != state.mutedVersionCode &&
    !promptedThisSession
