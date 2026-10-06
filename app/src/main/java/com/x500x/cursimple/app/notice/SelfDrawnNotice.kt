package com.x500x.cursimple.app.notice

import com.x500x.cursimple.core.reminder.permission.VendorRom

/**
 * Add a vendor-specific enhanced layer while preserving the ordinary system notification.
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
