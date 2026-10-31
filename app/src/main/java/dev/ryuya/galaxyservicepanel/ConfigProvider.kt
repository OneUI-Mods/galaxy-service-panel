package dev.ryuya.galaxyservicepanel

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager.NameNotFoundException
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic
import kotlin.math.max
import kotlin.math.min

/* Public display preferences; status writes are limited to this app and Samsung's phone UID. */
class ConfigProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<String?>?,
        selection: String?,
        args: Array<String?>?,
        order: String?,
    ): Cursor {
        require(ConfigProvider.URI == uri) { "Unknown settings URI" }
        val p = ConfigProvider.settings(getContext()!!)
        val result = MatrixCursor(arrayOf<String>("enabled", "font", "nonce"))
        result.addRow(
            arrayOf<Any>(
                if (p!!.getBoolean("enabled", true) && !p!!.getBoolean("blocked", false)) 1 else 0,
                max(12, min(20, p!!.getInt("font", 14))),
                p!!.getLong("nonce", 0),
            )
        )
        result.setNotificationUri(getContext()!!.getContentResolver(), ConfigProvider.URI)
        return result
    }

    override fun call(method: String, arg: String?, data: Bundle?): Bundle? {
        val uid = Binder.getCallingUid()
        val own: Boolean = uid == Process.myUid()
        try {
            if (
                !own &&
                    uid !=
                        getContext()!!
                            .getPackageManager()
                            .getApplicationInfo(ConfigProvider.TARGET, 0)
                            .uid
            )
                throw SecurityException("Samsung phone UID required")
        } catch (e: NameNotFoundException) {
            throw SecurityException(e)
        }
        if (method == "disable") {
            ConfigProvider.settings(getContext()!!)!!.edit().putBoolean("enabled", false).apply()
            ConfigProvider.changed(getContext()!!)
            return Bundle.EMPTY
        }
        require(!(method != "status" || data == null)) { "Unknown status operation" }
        val state = data.getString("state", "")
        require(
            java.util.Set.of<String?>("active", "disabled", "unsupported", "blocked", "waiting")
                .contains(state)
        ) {
            "Invalid state"
        }
        val edit =
            ConfigProvider.settings(getContext()!!)!!.edit()
                .putString("state", state)
                .putLong("checked_at", System.currentTimeMillis())
        if (state == "blocked") edit.putBoolean("blocked", true).putBoolean("enabled", false)
        edit.apply()
        // Status changes do not recursively trigger the hooked settings observer.
        return Bundle.EMPTY
    }

    override fun getType(uri: Uri): String? {
        return "vnd.android.cursor.item/vnd.galaxyservicepanel.config"
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        throw UnsupportedOperationException()
    }

    override fun delete(uri: Uri, where: String?, args: Array<String?>?): Int {
        throw UnsupportedOperationException()
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        where: String?,
        args: Array<String?>?,
    ): Int {
        throw UnsupportedOperationException()
    }

    companion object {
        const val PACKAGE: String = "dev.ryuya.galaxyservicepanel"
        const val TARGET: String = "com.sec.android.RilServiceModeApp"
        @JvmField
        val URI: Uri? = Uri.parse("content://" + ConfigProvider.PACKAGE + ".config/settings")

        @JvmStatic
        fun settings(c: Context): SharedPreferences? {
            return c.getSharedPreferences("config", Context.MODE_PRIVATE)
        }

        @JvmStatic
        fun changed(c: Context) {
            c.getContentResolver().notifyChange(ConfigProvider.URI!!, null)
        }
    }
}
