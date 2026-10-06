package com.x500x.cursimple.app.notice

import com.x500x.cursimple.core.reminder.permission.VendorRom

/**
 * Use overlay-only fallback for explicitly documented vendor restrictions. Preserve system
 * notifications in the shade without unsupported ongoing or chip flags.
 */
object SelfDrawnNotice {

    enum class Reason {
        /**
         * OriginOS docs 577 and 930 describe ongoing-banner restrictions and
         * unregistered-channel suppression; standard chips require separate vendor approval.
         */
        Vivo,

        /**
         * Vendor classification can restrict unregistered local notices to silent,
         * quota-limited delivery.
         */
        Huawei,
    }

    /** Null means the native notification path remains suitable. */
    fun reason(): Reason? = reasonFor(VendorRom.current())

    fun only(): Boolean = reason() != null

    internal fun reasonFor(vendor: VendorRom): Reason? = when (vendor) {
        VendorRom.Vivo -> Reason.Vivo
        VendorRom.Huawei -> Reason.Huawei
        else -> null
    }
}
