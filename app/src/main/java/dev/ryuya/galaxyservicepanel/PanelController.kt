package dev.ryuya.galaxyservicepanel

import android.app.Activity
import android.content.Context
import android.database.ContentObserver
import android.database.DataSetObserver
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.View.OnApplyWindowInsetsListener
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsets.Type
import android.view.WindowInsetsController
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.PopupMenu
import androidx.appcompat.widget.Toolbar.OnMenuItemClickListener
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.MaterialColors
import java.lang.ref.WeakReference
import java.util.ArrayList
import java.util.List
import java.util.Objects
import java.util.WeakHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.function.Consumer
import java.util.function.IntConsumer
import kotlin.jvm.JvmRecord
import kotlin.jvm.JvmStatic

/* Owns presentation only. The native adapter, listener, handler and modem commands stay intact. */
class PanelController
private constructor(host: Activity, list: ListView, adapter: ArrayAdapter<*>) {
    @JvmRecord
    private data class Preferences(val enabled: Boolean, val font: Int, val nonce: Long) {
        override fun toString(): String = "Preferences[enabled=$enabled, font=$font, nonce=$nonce]"
    }

    @JvmRecord
    private data class Child(val view: View?, val params: android.view.ViewGroup.LayoutParams?) {
        override fun toString(): String = "Child[view=$view, params=$params]"
    }

    private val host: Activity
    private val main: Handler = Handler(Looper.getMainLooper())
    private val nativeList: ListView
    private val nativeAdapter: ArrayAdapter<*>
    private val content: ViewGroup
    private val guard: PhoneGuard
    private val data: DataSetObserver
    private val config: ContentObserver
    private val render: Runnable = Runnable { this.render() }
    private val children: MutableList<PanelController.Child?> = ArrayList<PanelController.Child?>()
    private var snapshot: MutableList<String?>? = java.util.List.of<String?>()
    private var root: LinearLayout? = null
    private var panel: PanelView? = null
    private var preferences: PanelController.Preferences? = null
    private var destroyed: Boolean = false
    private var attached: Boolean = false
    private var pending: Boolean = false
    private var querying: Boolean = false
    private var faulted: Boolean = false
    private var actionBarWasVisible: Boolean = false
    private var observing: Boolean = false
    private var paused: Boolean = false
    private var settingsGeneration: Long = 0
    private var originalBackground: Drawable? = null
    private var originalBarAppearance: Int = 0

    init {
        this.host = host
        nativeList = list
        nativeAdapter = adapter
        content = host.findViewById<ViewGroup?>(android.R.id.content)
        guard = PhoneGuard(host)
        data =
            object : DataSetObserver() {
                override fun onChanged() {
                    schedule()
                }

                override fun onInvalidated() {
                    schedule()
                }
            }
        config =
            object : ContentObserver(main) {
                override fun onChange(self: Boolean) {
                    settingsGeneration++
                    refresh()
                }
            }
        host.getContentResolver().registerContentObserver(ConfigProvider.URI!!, false, config)
    }

    private fun refresh() {
        if (destroyed || querying) return
        querying = true
        val generation: Long = settingsGeneration
        PanelController.IO!!.execute(
            Runnable {
                var read: PanelController.Preferences? = null
                try {
                    host
                        .getContentResolver()
                        .query(ConfigProvider.URI!!, null, null, null, null)
                        .use { cursor ->
                            if (cursor != null && cursor.moveToFirst())
                                read =
                                    PanelController.Preferences(
                                        cursor.getInt(0) != 0,
                                        cursor.getInt(1),
                                        cursor.getLong(2),
                                    )
                        }
                } catch (ignored: RuntimeException) {}
                val next: PanelController.Preferences? = read
                main.post(
                    Runnable {
                        querying = false
                        if (destroyed) return@Runnable
                        if (generation != settingsGeneration) {
                            refresh()
                            return@Runnable
                        }
                        try {
                            if (next == null || !next.enabled) {
                                restore()
                                PanelController.status(host, "disabled")
                                return@Runnable
                            }
                            if (preferences != null && preferences!!.nonce != next.nonce)
                                faulted = false
                            preferences = next
                            if (faulted || paused) return@Runnable
                            if (!attached) attach()
                            schedule()
                        } catch (error: Throwable) {
                            fail(error)
                        }
                    }
                )
            }
        )
    }

    @Throws(Exception::class)
    private fun attach() {
        if (!guard.arm(preferences!!.nonce)) {
            faulted = true
            PanelController.status(host, "blocked")
            return
        }
        val context = ModuleUiContext(host)
        val nextRoot = LinearLayout(context)
        nextRoot.setOrientation(LinearLayout.VERTICAL)
        val surface =
            MaterialColors.getColor(
                context,
                com.google.android.material.R.attr.colorSurface,
                "ServicePanel",
            )
        nextRoot.setBackgroundColor(surface)
        val toolbar = MaterialToolbar(context)
        toolbar.setTitle(R.string.preview_title)
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        toolbar.setNavigationContentDescription(R.string.back)
        toolbar.setNavigationOnClickListener(
            OnClickListener { v: View? ->
                try {
                    if (
                        snapshot!!
                            .stream()
                            .filter { obj: Any? -> Objects.nonNull(obj) }
                            .anyMatch { s: String? -> s!!.trim() == "BASIC INFORMATION" }
                    )
                        host.finish()
                    else nativeMenu("Back")
                } catch (error: Throwable) {
                    fail(error)
                }
            }
        )
        // Native menu titles are kept exact because Samsung dispatches these actions by title.
        host.onCreateOptionsMenu(toolbar.getMenu())
        toolbar.getMenu().add(Menu.NONE, 0x47535001, 100, R.string.show_stock)
        toolbar.setOnMenuItemClickListener(
            OnMenuItemClickListener { item: MenuItem? ->
                try {
                    if (item!!.getItemId() == 0x47535001) {
                        PanelController.IO!!.execute(
                            Runnable {
                                try {
                                    host
                                        .getContentResolver()
                                        .call(ConfigProvider.URI!!, "disable", null, null)
                                } catch (ignored: RuntimeException) {}
                            }
                        )
                    } else host.onMenuItemSelected(Window.FEATURE_OPTIONS_PANEL, item!!)
                } catch (error: Throwable) {
                    fail(error)
                }
                true
            }
        )
        nextRoot.addView(toolbar, android.widget.LinearLayout.LayoutParams(-1, -2))
        val nextPanel =
            PanelView(
                context,
                IntConsumer { index: Int -> this.select(index) },
                Consumer { error: Throwable -> this.fail(error) },
            )
        nextRoot.addView(nextPanel, android.widget.LinearLayout.LayoutParams(-1, 0, 1f))
        nextRoot.setOnApplyWindowInsetsListener(
            OnApplyWindowInsetsListener { view: View?, insets: WindowInsets? ->
                val bars = insets!!.getInsets(Type.systemBars() or Type.displayCutout())
                view!!.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets!!
            }
        )
        for (i in 0 until content.getChildCount()) {
            val child = content.getChildAt(i)
            children.add(PanelController.Child(child, child.getLayoutParams()))
        }
        root = nextRoot
        panel = nextPanel
        attached = true
        val bar = host.getActionBar()
        actionBarWasVisible = bar != null && bar.isShowing()
        if (actionBarWasVisible) bar!!.hide()
        originalBackground = host.getWindow().getDecorView().getBackground()
        host.getWindow().setBackgroundDrawable(ColorDrawable(surface))
        val controller = host.getWindow().getInsetsController()
        if (controller != null) {
            originalBarAppearance = controller.getSystemBarsAppearance()
            val mask: Int =
                (WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
            controller.setSystemBarsAppearance(
                if (MaterialColors.isColorLight(surface)) mask else 0,
                mask,
            )
        }
        content.removeAllViews()
        content.addView(root, android.view.ViewGroup.LayoutParams(-1, -1))
        nativeAdapter.registerDataSetObserver(data)
        observing = true
        root!!.requestApplyInsets()
        PanelController.status(host, "active")
    }

    private fun nativeMenu(title: String?) {
        val menu = PopupMenu(host, nativeList)
        host.onMenuItemSelected(Window.FEATURE_OPTIONS_PANEL, menu.getMenu().add(title))
    }

    private fun select(index: Int) {
        try {
            if (!attached || index >= snapshot!!.size || index >= nativeAdapter.getCount()) return
            val current = nativeAdapter.getItem(index)
            if (snapshot!!.get(index) != current) {
                schedule()
                return
            }
            nativeList.performItemClick(null, index, nativeAdapter.getItemId(index))
        } catch (error: Throwable) {
            fail(error)
        }
    }

    private fun schedule() {
        if (destroyed || !attached || pending) return
        pending = true
        // clear/addAll/explicit notify are coalesced into one complete native snapshot per frame.
        main.post(render)
    }

    private fun render() {
        pending = false
        if (!attached || destroyed) return
        try {
            val values = ArrayList<String?>()
            for (i in 0 until nativeAdapter.getCount()) {
                val value = nativeAdapter.getItem(i)
                check(!(value != null && value !is String)) { "Unexpected native row type" }
                values.add(if (value == null) "" else value as String?)
            }
            snapshot = List.copyOf<String?>(values)
            panel!!.update(snapshot, preferences!!.font)
        } catch (error: Throwable) {
            fail(error)
        }
    }

    private fun restore() {
        main.removeCallbacks(render)
        pending = false
        guard.disarm()
        if (!attached) return
        if (observing) {
            nativeAdapter.unregisterDataSetObserver(data)
            observing = false
        }
        content.removeView(root)
        for (child in children) content.addView(child!!.view, child!!.params)
        children.clear()
        host.getWindow().setBackgroundDrawable(originalBackground)
        val controller = host.getWindow().getInsetsController()
        if (controller != null) {
            val mask: Int =
                (WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
            controller.setSystemBarsAppearance(originalBarAppearance, mask)
        }
        if (actionBarWasVisible && host.getActionBar() != null) host.getActionBar()!!.show()
        attached = false
        panel = null
        root = null
        snapshot = java.util.List.of<String?>()
    }

    private fun fail(error: Throwable) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(Runnable { fail(error) })
            return
        }
        if (faulted || destroyed) return
        faulted = true
        // getView may fail during layout. Restore on the next main-loop turn, never within
        // traversal.
        main.post(
            Runnable {
                try {
                    restore()
                } catch (ignored: Throwable) {}
                PanelController.status(host, "blocked")
            }
        )
        Log.e(
            "GalaxyServicePanel",
            "Formatting disabled: " + error.javaClass.getSimpleName(),
        )
    }

    private fun close() {
        if (destroyed) return
        destroyed = true
        try {
            host.getContentResolver().unregisterContentObserver(config)
        } catch (ignored: RuntimeException) {}
        try {
            restore()
        } catch (ignored: Throwable) {}
        if (!faulted) PanelController.status(host, "waiting")
    }

    companion object {
        private val ACTIVE: MutableMap<Activity?, WeakReference<PanelController?>?> =
            WeakHashMap<Activity?, WeakReference<PanelController?>?>()
        private val IO: ExecutorService? =
            Executors.newSingleThreadExecutor(
                ThreadFactory { r: Runnable? ->
                    val thread = Thread(r, "ServicePanelConfig")
                    thread.setDaemon(true)
                    thread
                }
            )

        @JvmStatic
        fun created(activity: Activity) {
            if ("0011" != activity.getIntent().getStringExtra("keyString")) return
            try {
                val profile = HookProfile.current()
                val listId =
                    activity.getResources().getIdentifier("list", "id", ConfigProvider.TARGET)
                val candidate = activity.findViewById<View?>(listId)
                if (
                    profile == null ||
                        (candidate !is ListView) ||
                        (candidate.getAdapter() !is ArrayAdapter<*>)
                ) {
                    PanelController.status(activity, "unsupported")
                    return
                }
                val controller =
                    PanelController(activity, candidate, candidate.adapter as ArrayAdapter<*>)
                PanelController.ACTIVE.put(activity, WeakReference<PanelController?>(controller))
                controller.refresh()
            } catch (error: Throwable) {
                PanelController.status(activity, "blocked")
            }
        }

        @JvmStatic
        fun resumed(activity: Activity) {
            val ref = PanelController.ACTIVE.get(activity)
            val controller: PanelController? = if (ref == null) null else ref.get()
            if (controller != null) {
                controller.paused = false
                try {
                    if (controller.attached && controller.preferences != null) {
                        if (!controller.guard.arm(controller!!.preferences!!.nonce)) {
                            controller.restore()
                            controller.faulted = true
                            PanelController.status(activity, "blocked")
                            return
                        }
                        PanelController.status(activity, "active")
                    }
                    controller.refresh()
                } catch (error: Throwable) {
                    controller.fail(error)
                }
            }
        }

        @JvmStatic
        fun paused(activity: Activity) {
            val ref = PanelController.ACTIVE.get(activity)
            val controller: PanelController? = if (ref == null) null else ref.get()
            if (controller != null) {
                controller.paused = true
                try {
                    controller.guard.disarm()
                } catch (ignored: RuntimeException) {}
                if (!controller.faulted) PanelController.status(activity, "waiting")
            }
        }

        @JvmStatic
        fun destroyed(activity: Activity?) {
            val ref = PanelController.ACTIVE.remove(activity)
            val controller: PanelController? = if (ref == null) null else ref.get()
            if (controller != null) controller.close()
        }

        @JvmStatic
        private fun status(context: Context, state: String?) {
            PanelController.IO!!.execute(
                Runnable {
                    try {
                        val data = Bundle()
                        data.putString("state", state)
                        context
                            .getContentResolver()
                            .call(ConfigProvider.URI!!, "status", null, data)
                    } catch (ignored: RuntimeException) {}
                }
            )
        }
    }
}
