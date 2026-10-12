package com.x500x.cursimple.app.notice

import android.app.PendingIntent
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Bundle
import com.x500x.cursimple.R
import com.x500x.cursimple.core.reminder.logging.ReminderLogger
import com.x500x.cursimple.core.reminder.permission.VendorRom

/**
 * vivo's notification-borne island protocol. vivo does not document these keys; the wire format
 * comes from a reverse-engineered implementation of the OriginOS framework
 * (theVakhovskeIsTaken/CunnyPlayground, `originos-experimental`), and third-party apps report it
 * working without the vendor whitelist. Unknown keys are dropped silently, so an unreviewed
 * device stays unconfirmed until a real phone shows the capsule.
 */
internal object VivoAtomicNotice {
    const val TAG = "VIVO_SUPERX_TAG"
    const val OPERATION = "notification.superx.operation"
    const val DISPLAYS = "notification.superx.displays"
    const val SHORT_INFO = "notification.superx.shortInfos"
    const val ISLAND = "notification.superx.island"
    const val CAPSULE = "notification.superx.capsule"

    /** The scene the framework matches on. */
    const val SCENE = "TRAIN"

    fun usesProtocol(vendor: VendorRom, focus: Boolean, plain: Boolean) = vendor == VendorRom.Vivo && focus && !plain

    /**
     * The framework ignores a superx payload whose scene was never switched on, so registering
     * the scene is a prerequisite, not an optimisation. The method only exists on vivo builds.
     */
    fun registerScene(context: Context) {
        runCatching {
            val manager = context.getSystemService(android.app.NotificationManager::class.java) ?: return
            val method = manager.javaClass.getMethod(
                "setSuperXInfosSceneList",
                MutableList::class.java, MutableList::class.java,
                MutableList::class.java, MutableList::class.java,
            )
            method.invoke(
                manager,
                arrayListOf(SCENE), arrayListOf("true"),
                arrayListOf(context.packageName), arrayListOf("true"),
            )
        }.onFailure {
            ReminderLogger.info("class_notice.vivo.scene_unsupported", mapOf("reason" to it.javaClass.simpleName))
        }
    }

    fun displayMask(lockScreen: Boolean, aod: Boolean): Int =
        0x001 or 0x100 or (if (lockScreen) 0x010 else 0) or (if (lockScreen && aod) 0x10000 else 0)

    fun extras(
        context: Context, content: ClassNoticeNotifier.Content, theme: NoticeTheme,
        openApp: PendingIntent, lockScreen: Boolean, aod: Boolean, expiresAt: Long,
    ): Bundle {
        val icon = Icon.createWithResource(context, R.drawable.ic_notification).setTint(theme.primary)
        val course = content.courseTitle.take(40)
        val detail = listOf(content.timeRange, content.location).filter(String::isNotBlank).joinToString(" · ")
        val seconds = ((expiresAt - System.currentTimeMillis() + 999) / 1000).coerceIn(1, 86_400).toInt()
        val base = Bundle().apply {
            putParcelable("notification.superx.baseInfos.icon", icon)
            putCharSequence("notification.superx.baseInfos.title", course)
            putCharSequence("notification.superx.baseInfos.content", detail)
        }
        val info = Bundle().apply {
            putString("notification.superx.infos.describe", content.location.ifBlank { content.subText(context) })
            putString("notification.superx.infos.coreInfo", content.timeRange)
            putParcelable("notification.superx.infos.image", icon)
            putParcelable("notification.superx.infos.imageClickResp", openApp)
        }
        return Bundle().apply {
            putInt(OPERATION, if (content.inProgress) 1 else 0)
            // A course is a calendar event; use the documented calendar scene, not another app's ID.
            putString("notification.superx.scene", "METTING")
            putInt("notification.superx.template", 1)
            putBoolean("notification.superx.showNotify", true)
            putBoolean("notification.superx.sound", false)
            putInt("notification.superx.keepDuration", 0)
            putInt("notification.superx.newNode", if (content.inProgress) 2 else 1)
            putInt(DISPLAYS, displayMask(lockScreen, aod))
            putParcelable("notification.superx.clickResp", openApp)
            putBundle("notification.superx.baseInfos", base)
            putBundle("notification.superx.infos", info)
            putBundle(SHORT_INFO, Bundle().apply {
                putParcelable("notification.superx.shortInfos.icon", icon)
                putParcelable("notification.superx.shortInfos.image", icon)
                putParcelable("notification.superx.shortInfos.imageClickResp", openApp)
                putString("notification.superx.shortInfos.describeShort", detail)
                putString("notification.superx.shortInfos.coreInfoShort", course)
            })
            putBundle(CAPSULE, Bundle().apply {
                putInt("notification.superx.capsule.state", 1)
                putParcelable("notification.superx.capsule.icon", icon)
                putString("notification.superx.capsule.content", "$course ${content.startClock}")
                putInt("notification.superx.capsule.contentColor", theme.onPrimaryContainer)
                putInt("notification.superx.capsule.bgColor", theme.primaryContainer)
                putInt("notification.superx.capsule.showTime", seconds)
                putParcelable("notification.superx.capsule.clickResp", openApp)
            })
            putBundle(ISLAND, Bundle().apply {
                putInt("island.superx.leftTemplate", 1)
                putBundle("island.superx.leftInfo", Bundle().apply {
                    putParcelable("island.superx.leftInfo.icon", icon)
                    putCharSequence("island.superx.leftInfo.content", course)
                })
                putInt("island.superx.rightTemplate", 4)
                putBundle("island.superx.rightInfo", Bundle().apply {
                    putCharSequence("island.superx.rightInfo.content", if (content.inProgress) content.subText(context) else content.startClock)
                    putParcelable("island.superx.rightInfo.clickResp", openApp)
                })
                putInt("island.superx.islandClick", 0)
                putParcelable("island.superx.clickResp", openApp)
                putInt("island.superx.template", 1)
                putBundle("island.superx.baseInfos", base)
                putBundle("island.superx.infos", info)
                putInt("island.superx.showTime", seconds)
            })
        }
    }
}
