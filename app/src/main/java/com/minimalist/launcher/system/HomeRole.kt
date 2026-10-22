package com.minimalist.launcher.system

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.provider.Settings

object HomeRole {

    fun isDefaultHome(context: Context): Boolean =
        context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME)

    /** System dialog that asks to make this the Home app. */
    fun requestIntent(context: Context): Intent =
        context.getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_HOME)

    /** Settings → Apps → Default apps → Home app, for when the dialog is unavailable. */
    fun settingsIntent(): Intent = Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
