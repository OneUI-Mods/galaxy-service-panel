package dev.ryuya.galaxyservicepanel

import android.os.Build
import kotlin.jvm.JvmField
import kotlin.jvm.JvmRecord
import kotlin.jvm.JvmStatic

/*
 * Separate OS records; both require the native ListView/String ArrayAdapter contract at runtime.
 */
@JvmRecord
data class HookProfile(val name: String?, val sdk: Int) {

    companion object {
        @JvmField val ANDROID_16: HookProfile = HookProfile("Android 16", 36)
        @JvmField val ANDROID_17: HookProfile = HookProfile("Android 17", 37)

        @JvmStatic
        fun current(): HookProfile? {
            return when (Build.VERSION.SDK_INT) {
                36 -> HookProfile.ANDROID_16
                37 -> HookProfile.ANDROID_17
                else -> null
            }
        }
    }

    override fun toString(): String = "HookProfile[name=$name, sdk=$sdk]"
}
