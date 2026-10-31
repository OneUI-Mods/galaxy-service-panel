package dev.ryuya.galaxyservicepanel

import android.app.LocaleManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.pm.PackageManager.NameNotFoundException
import android.os.Bundle
import android.os.LocaleList
import android.view.MenuItem
import android.view.MenuItem.OnMenuItemClickListener
import android.view.View
import android.widget.CompoundButton
import android.widget.CompoundButton.OnCheckedChangeListener
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type
import androidx.core.widget.NestedScrollView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.slider.Slider.OnChangeListener
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import com.google.android.material.tabs.TabLayout.Tab
import com.google.android.material.textview.MaterialTextView
import java.text.DateFormat
import java.util.ArrayList
import java.util.Date
import java.util.function.Consumer
import java.util.function.IntConsumer
import kotlin.jvm.JvmStatic
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/* Settings, a synthetic preview, and component-by-component license pages. */
class MainActivity : AppCompatActivity() {
    private var enabled: MaterialSwitch? = null
    private var status: MaterialTextView? = null
    private var content: FrameLayout? = null
    private var tabs: TabLayout? = null
    private val pages: MutableList<View?> = ArrayList<View?>()
    private var font: Int = 0
    private var binding: Boolean = false
    private val changed: OnSharedPreferenceChangeListener =
        OnSharedPreferenceChangeListener { prefs: SharedPreferences?, key: String? ->
            refreshStatus()
        }
    private var previewPanel: PanelView? = null

    public override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false)
        val root = vertical()
        root.setBackgroundColor(color(com.google.android.material.R.attr.colorSurface))
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(
            root,
            OnApplyWindowInsetsListener { v: View?, insets: WindowInsetsCompat? ->
                val bars = insets!!.getInsets(Type.systemBars() or Type.displayCutout())
                v!!.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets!!
            },
        )
        val toolbar = MaterialToolbar(this)
        toolbar.setTitle(R.string.app_name)
        toolbar
            .getMenu()
            .add("Language / 言語")
            .setOnMenuItemClickListener(
                OnMenuItemClickListener { item: MenuItem? ->
                    language()
                    true
                }
            )
        root.addView(toolbar, android.widget.LinearLayout.LayoutParams(-1, -2))
        tabs = TabLayout(this)
        tabs!!.setTabIndicatorFullWidth(false)
        root.addView(tabs)
        content = FrameLayout(this)
        root.addView(content, android.widget.LinearLayout.LayoutParams(-1, 0, 1f))
        font = ConfigProvider.settings(this)!!.getInt("font", 14)
        val display = page(R.string.display_tab)
        appearance(display)
        preview()
        val about = page(R.string.about_tab)
        about(about)
        tabs!!.addOnTabSelectedListener(
            object : OnTabSelectedListener {
                override fun onTabSelected(tab: Tab) {
                    show(tab.getPosition())
                }

                override fun onTabUnselected(tab: Tab?) {}

                override fun onTabReselected(tab: Tab?) {}
            }
        )
        val initial: Int = if (state == null) 0 else state.getInt("tab", 0)
        val selected = tabs!!.getTabAt(max(0, min(initial, tabs!!.getTabCount() - 1)))
        if (selected != null) selected.select()
        show(max(0, tabs!!.getSelectedTabPosition()))
        ViewCompat.requestApplyInsets(root)
    }

    private fun appearance(page: LinearLayout) {
        val master = card(page, true)
        enabled = MaterialSwitch(this)
        enabled!!.setId(R.id.module_enabled)
        enabled!!.setText(R.string.enabled)
        enabled!!.setTextAppearance(
            com.google.android.material.R.style.TextAppearance_Material3_TitleMedium
        )
        enabled!!.setMinHeight(dp(56))
        enabled!!.setChecked(
            ConfigProvider.settings(this)!!.getBoolean("enabled", true) &&
                !ConfigProvider.settings(this)!!.getBoolean("blocked", false)
        )
        master.addView(enabled, android.widget.LinearLayout.LayoutParams(-1, -2))
        master.addView(text(getString(R.string.enabled_note), false))
        enabled!!.setOnCheckedChangeListener(
            OnCheckedChangeListener { button: CompoundButton?, checked: Boolean ->
                if (binding) return@OnCheckedChangeListener
                ConfigProvider.settings(this)!!.edit().putBoolean("enabled", checked).apply()
                ConfigProvider.changed(this)
                refreshStatus()
            }
        )
        val state = card(page, false)
        state.addView(text(getString(R.string.status_title), true))
        status = text("", false)
        status!!.setPadding(0, dp(8), 0, dp(4))
        state.addView(status)
        val refresh = button(getString(R.string.settings_refresh))
        refresh.setOnClickListener(
            android.view.View.OnClickListener { v: View? -> refreshStatus() }
        )
        state.addView(refresh)
        val looks = card(page, false)
        looks.addView(text(getString(R.string.appearance), true))
        val value = text(getString(R.string.font_size, font), false)
        value.setPadding(0, dp(12), 0, 0)
        looks.addView(value)
        val slider = Slider(this)
        slider.setId(R.id.font_slider)
        slider.setValueFrom(12f)
        slider.setValueTo(20f)
        slider.setStepSize(1f)
        slider.setValue(max(12, min(20, font)).toFloat())
        slider.setContentDescription(getString(R.string.appearance))
        slider.addOnChangeListener(
            OnChangeListener { view: Slider?, amount: Float, fromUser: Boolean ->
                font = Math.round(amount)
                value.setText(getString(R.string.font_size, font))
                if (fromUser) {
                    ConfigProvider.settings(this)!!.edit().putInt("font", font).apply()
                    ConfigProvider.changed(this)
                }
            }
        )
        looks.addView(slider, android.widget.LinearLayout.LayoutParams(-1, -2))
        looks.addView(text(getString(R.string.appearance_note), false))
        val setup = card(page, false)
        setup.addView(text(getString(R.string.setup_title), true))
        val body = text(getString(R.string.setup_body), false)
        body.setPadding(0, dp(10), 0, dp(10))
        setup.addView(body)
        val dialer = button(getString(R.string.open_phone))
        dialer.setOnClickListener(
            android.view.View.OnClickListener { v: View? ->
                try {
                    startActivity(
                        Intent(Intent.ACTION_DIAL).setPackage("com.samsung.android.dialer")
                    )
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(this, R.string.not_installed, Toast.LENGTH_SHORT).show()
                }
            }
        )
        setup.addView(dialer)
        val copy = button(getString(R.string.copy_code))
        copy.setOnClickListener(
            android.view.View.OnClickListener { v: View? ->
                getSystemService<ClipboardManager?>(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText(getString(R.string.dial_code), "*#0011#"))
                Toast.makeText(this, R.string.code_copied, Toast.LENGTH_SHORT).show()
            }
        )
        setup.addView(copy)
    }

    private fun preview() {
        val page = vertical()
        var ui: Context?
        try {
            ui = ModuleUiContext(this)
        } catch (e: NameNotFoundException) {
            ui = this
        }
        val panel = PanelView(ui, IntConsumer { i: Int -> }, Consumer { error: Throwable? -> })
        previewPanel = panel
        panel.sampleMode()
        panel.update(MainActivity.sample(), font)
        page.addView(panel, android.widget.LinearLayout.LayoutParams(-1, 0, 1f))
        addPage(R.string.preview_tab, page)
    }

    private fun about(page: LinearLayout) {
        val intro = card(page, false)
        intro.addView(text("Galaxy Service Panel", true))
        intro.addView(text(BuildConfig.VERSION_NAME, false))
        val description = text(getString(R.string.about_body), false)
        description.setPadding(0, dp(12), 0, 0)
        intro.addView(description)
        val compatibility = card(page, false)
        compatibility.addView(text(getString(R.string.compatibility), true))
        compatibility.addView(text(getString(R.string.compatibility_body), false))
        val guard = card(page, false)
        guard.addView(text(getString(R.string.guard_title), true))
        guard.addView(text(getString(R.string.guard_body), false))
        val reset = button(getString(R.string.guard_reset))
        reset.setId(R.id.guard_reset)
        reset.setOnClickListener(
            android.view.View.OnClickListener { v: View? ->
                ConfigProvider.settings(this)!!.edit()
                    .putBoolean("blocked", false)
                    .putBoolean("enabled", true)
                    .putLong("nonce", System.currentTimeMillis())
                    .putString("state", "waiting")
                    .apply()
                ConfigProvider.changed(this)
                enabled!!.setChecked(true)
                refreshStatus()
            }
        )
        guard.addView(reset)
        val licenses = button(getString(R.string.legal_title))
        licenses.setOnClickListener(
            android.view.View.OnClickListener { v: View? ->
                startActivity(Intent(this, LicensesActivity::class.java))
            }
        )
        page.addView(licenses, android.widget.LinearLayout.LayoutParams(-1, -2))
    }

    private fun language() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Language / 言語")
            .setItems(
                arrayOf<String>("System / システム", "日本語", "English"),
                android.content.DialogInterface.OnClickListener {
                    dialog: DialogInterface?,
                    which: Int ->
                    val tag: String? = if (which == 1) "ja" else if (which == 2) "en" else ""
                    getSystemService<LocaleManager?>(LocaleManager::class.java)
                        .setApplicationLocales(LocaleList.forLanguageTags(tag))
                },
            )
            .show()
    }

    private fun refreshStatus() {
        if (status == null) return
        val prefs = ConfigProvider.settings(this)
        val blocked = prefs!!.getBoolean("blocked", false)
        enabled!!.setEnabled(!blocked)
        binding = true
        enabled!!.setChecked(!blocked && prefs!!.getBoolean("enabled", true))
        binding = false
        val message: Int =
            if (blocked) R.string.status_guard
            else if (!prefs!!.getBoolean("enabled", true)) R.string.status_disabled
            else
                when (prefs!!.getString("state", "waiting")) {
                    "active" -> R.string.status_active
                    "unsupported" -> R.string.status_unsupported
                    else -> R.string.status_pending
                }
        val time = prefs!!.getLong("checked_at", 0)
        val stamp: String? =
            if (time == 0L) ""
            else
                ("\n" +
                    getString(
                        R.string.status_updated,
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                            .format(Date(time)),
                    ))
        status!!.setText(getString(message) + stamp)
    }

    override fun onStart() {
        super.onStart()
        ConfigProvider.settings(this)!!.registerOnSharedPreferenceChangeListener(changed)
    }

    override fun onStop() {
        ConfigProvider.settings(this)!!.unregisterOnSharedPreferenceChangeListener(changed)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onSaveInstanceState(out: Bundle) {
        out.putInt("tab", tabs!!.getSelectedTabPosition())
        super.onSaveInstanceState(out)
    }

    private fun page(title: Int): LinearLayout {
        val scroll = NestedScrollView(this)
        scroll.setFillViewport(true)
        val body = vertical()
        body.setPadding(dp(20), dp(16), dp(20), dp(24))
        scroll.addView(body)
        addPage(title, scroll)
        return body
    }

    private fun addPage(title: Int, page: View) {
        pages.add(page)
        content!!.addView(page, android.widget.FrameLayout.LayoutParams(-1, -1))
        page.setVisibility(if (pages.size == 1) View.VISIBLE else View.GONE)
        tabs!!.addTab(tabs!!.newTab().setText(title))
    }

    private fun show(selected: Int) {
        if (selected == 1 && previewPanel != null)
            previewPanel!!.update(MainActivity.sample(), font)
        for (i in pages.indices) pages
            .get(i)!!
            .setVisibility(if (i == selected) View.VISIBLE else View.GONE)
    }

    private fun card(page: LinearLayout, master: Boolean): LinearLayout {
        val card = MaterialCardView(this)
        card.setRadius(dp(24).toFloat())
        card.setCardElevation(0f)
        card.setStrokeWidth(0)
        card.setCardBackgroundColor(
            color(
                if (master) com.google.android.material.R.attr.colorPrimaryContainer
                else com.google.android.material.R.attr.colorSurfaceContainerLow
            )
        )
        val body = vertical()
        body.setPadding(dp(20), dp(16), dp(20), dp(16))
        card.addView(body)
        val size = android.widget.LinearLayout.LayoutParams(-1, -2)
        size.bottomMargin = dp(16)
        page.addView(card, size)
        return body
    }

    private fun button(label: String?): MaterialButton {
        val result =
            MaterialButton(
                this,
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle,
            )
        result.setText(label)
        result.setMinHeight(dp(48))
        return result
    }

    private fun text(value: String?, heading: Boolean): MaterialTextView {
        val result = MaterialTextView(this)
        result.setText(value)
        result.setTextAppearance(
            if (heading) com.google.android.material.R.style.TextAppearance_Material3_TitleMedium
            else com.google.android.material.R.style.TextAppearance_Material3_BodyMedium
        )
        result.setLineSpacing(dp(3).toFloat(), 1f)
        return result
    }

    private fun vertical(): LinearLayout {
        val result = LinearLayout(this)
        result.setOrientation(LinearLayout.VERTICAL)
        return result
    }

    private fun color(attr: Int): Int {
        return MaterialColors.getColor(this, attr, "GalaxyServicePanel")
    }

    private fun dp(value: Int): Int {
        return Math.round(value * getResources().getDisplayMetrics().density)
    }

    companion object {
        @JvmStatic
        fun sample(): MutableList<String?>? {
            return java.util.List.of<String?>(
                "DEBUG INFO : sample",
                "Slot 1",
                "Serving PLMN(001-01)-LTE",
                "NW sel mode : Auto",
                "LTE RRC:IDLE BAND:3 BW:20",
                "Earfcn: 1300, PCI: 12",
                "RSRP:-96 RSRQ:-9 SNR:14.2",
                "PRX0 RSRP:-96.4, RSRQ:-10",
                "DL Modulation-Layer1:64QAM",
                "PCC MIMO Configured: 2",
                "NR-RSRP:-- SINR:-- RSRQ:--",
                "NR_ARFCN: --",
                "NR_PCI: --",
                "NR_BAND: --",
            )
        }
    }
}
