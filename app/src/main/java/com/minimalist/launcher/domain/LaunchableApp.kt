package com.minimalist.launcher.domain

/**
 * One launcher entry. [key] is the flattened component name ("package/activity"), which stays
 * stable across reinstalls. [userSerial] identifies the profile it lives in; ColorOS app clones
 * and work profiles share a package name with the main copy but have a different serial.
 */
data class LaunchableApp(
    val key: String,
    val packageName: String,
    val label: String,
    val userSerial: Long,
)
