package com.x500x.cursimple.app

/** Immutable store selection used by the local backup UI. */
internal data class LocalBackupSelection private constructor(
    val storeNames: List<String>,
    val selectedStoreNames: List<String>,
) {
    fun toggle(storeName: String): LocalBackupSelection {
        require(storeName in storeNames) { "backup_unknown_store" }
        val next = if (storeName in selectedStoreNames) {
            selectedStoreNames.filterNot { it == storeName }
        } else {
            storeNames.filter { it in selectedStoreNames || it == storeName }
        }
        return LocalBackupSelection(storeNames, next)
    }

    companion object {
        fun all(storeNames: List<String>): LocalBackupSelection =
            from(storeNames, storeNames.toSet())

        fun from(
            storeNames: List<String>,
            selectedStoreNames: Set<String>,
        ): LocalBackupSelection {
            val distinctStores = storeNames.distinct()
            require(distinctStores.isNotEmpty()) { "backup_no_stores" }
            require(selectedStoreNames.all { it in distinctStores }) {
                "backup_unknown_store"
            }
            return LocalBackupSelection(
                storeNames = distinctStores,
                selectedStoreNames = distinctStores.filter { it in selectedStoreNames },
            )
        }
    }
}
