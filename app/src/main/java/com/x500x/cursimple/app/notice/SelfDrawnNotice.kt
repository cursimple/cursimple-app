package com.x500x.cursimple.app.notice

import com.x500x.cursimple.core.reminder.permission.VendorRom

/**
 * 这台手机上系统通知的横幅、锁屏、胶囊指不指望得上；指望不上就只用自己画的那一套。
 *
 * 判断只能按系统认：被厂商管住的时候，应用这头什么都读不出来——渠道照样显示 HIGH、
 * 通知照样被标成实时活动，诊断全绿，现象照旧。所以名单里只放**厂商文档写明了**的系统，
 * 没写明的一律按系统原生来，免得把本来好好的系统横幅换掉。
 *
 * 自己画的那一套：亮屏解锁时是悬浮窗横幅（[ClassNoticeOverlay]），锁屏或熄屏时是压在锁屏上的
 * 那一页（[ClassNoticeLockActivity]）。系统通知照发，只是不再申请常驻和胶囊，留在通知栏里备查。
 */
object SelfDrawnNotice {

    /** 系统不行的原因写在这里，设置页的说明卡片按它挑文案 */
    enum class Reason {
        /**
         * vivo OriginOS，两道关都是 vivo 开放平台写明的：
         * - 《OriginOS 通知样式规范》（doc 577）6.1：ongoing 通知不悬浮、不做锁屏提醒；
         *   Android 16 的胶囊又要求 ongoing，标准胶囊 OriginOS 也不画（原子岛要单独申请）。
         * - 《本地通知分类管理指南》（doc 930）：渠道没在 vivo 平台备案就按「运营消息」静默，
         *   只在下拉通知栏时看得到。不打算备案，所以横幅一律自己画。
         */
        Vivo,

        /**
         * 华为 EMUI / HarmonyOS 2-4（Android 版）。
         *
         * 《消息分类标准》+《本地通知频次及分类管控通知》：2023-09-15 起本地通知也要分类，
         * 没在 AppGallery Connect 申请「自分类权益」的应用，本地通知全按资讯营销消息处理——
         * 静默、只在通知中心里，每天还有条数限制（2 或 5 条）。课程提醒属于客服说的
         * 「工作事项提醒」（教学任务/课程提醒），但要申请权益、审 5 个工作日，还得先上架。
         */
        Huawei,
    }

    /** 这台手机要不要只用自己画的；null 表示系统原生的就行。 */
    fun reason(): Reason? = reasonFor(VendorRom.current())

    fun only(): Boolean = reason() != null

    internal fun reasonFor(vendor: VendorRom): Reason? = when (vendor) {
        VendorRom.Vivo -> Reason.Vivo
        VendorRom.Huawei -> Reason.Huawei
        else -> null
    }
}
