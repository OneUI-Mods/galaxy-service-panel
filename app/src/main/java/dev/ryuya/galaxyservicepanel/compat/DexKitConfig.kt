package dev.ryuya.galaxyservicepanel.compat

internal object DexKitConfig {
    const val SCHEMA = 2
    const val MODULE_ID = "dev.ryuya.galaxyservicepanel"
    val MODULE_VERSION
        get() = dev.ryuya.galaxyservicepanel.BuildConfig.VERSION_CODE

    const val PROFILE_HASH = "49e2df9461658daa9ada5987b286d90b321f0bbc72cf30ee9a29e69a5d65f79f"
    val FEATURE_PREFIXES = setOf<String>()
    val TARGETS = setOf("com.sec.android.RilServiceModeApp")
    val HOSTS = setOf("com.sec.android.RilServiceModeApp")
    val CLASSES_BY_TARGET =
        mapOf(
            "com.sec.android.RilServiceModeApp" to
                listOf("com.sec.android.RilServiceModeApp.ServiceModeApp")
        )
    val KNOWN_VERSIONS = mapOf("com.sec.android.RilServiceModeApp" to setOf(37L))
    val PROFILE_PACKAGES =
        mapOf(
            36 to setOf("com.sec.android.RilServiceModeApp"),
            37 to setOf("com.sec.android.RilServiceModeApp"),
        )
}
