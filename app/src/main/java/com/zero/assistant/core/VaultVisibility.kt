package com.zero.assistant.core

object VaultVisibility {
    const val PREF_FILE = "zero_privacy"
    const val PREF_VISIBLE = "vault_visible"

    fun action(visible: Boolean): Boolean = visible

    fun setVisible(current: Boolean, visible: Boolean): Boolean = visible

    fun visibleEntry(visible: Boolean, vaultEntry: Any?): Any? =
        if (visible) vaultEntry else null
}
