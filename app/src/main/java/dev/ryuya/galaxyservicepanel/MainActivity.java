package dev.ryuya.galaxyservicepanel;

import android.app.LocaleManager;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.*;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textview.MaterialTextView;
import java.text.DateFormat;
import java.util.*;

/** Settings, a synthetic preview, and component-by-component license pages. */
public final class MainActivity extends AppCompatActivity {
  private MaterialSwitch enabled;
  private MaterialTextView status;
  private FrameLayout content;
  private TabLayout tabs;
  private final List<View> pages = new ArrayList<>();
  private int font;
  private boolean binding;
  private final SharedPreferences.OnSharedPreferenceChangeListener changed =
      (prefs, key) -> refreshStatus();
  private PanelView previewPanel;

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    LinearLayout root = vertical();
    root.setBackgroundColor(color(com.google.android.material.R.attr.colorSurface));
    setContentView(root);
    ViewCompat.setOnApplyWindowInsetsListener(
        root,
        (v, insets) -> {
          var bars =
              insets.getInsets(
                  WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
          v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
          return insets;
        });
    MaterialToolbar toolbar = new MaterialToolbar(this);
    toolbar.setTitle(R.string.app_name);
    toolbar
        .getMenu()
        .add("Language / 言語")
        .setOnMenuItemClickListener(
            item -> {
              language();
              return true;
            });
    root.addView(toolbar, new LinearLayout.LayoutParams(-1, -2));
    tabs = new TabLayout(this);
    tabs.setTabIndicatorFullWidth(false);
    root.addView(tabs);
    content = new FrameLayout(this);
    root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
    font = ConfigProvider.settings(this).getInt("font", 14);
    LinearLayout display = page(R.string.display_tab);
    appearance(display);
    preview();
    LinearLayout about = page(R.string.about_tab);
    about(about);
    tabs.addOnTabSelectedListener(
        new TabLayout.OnTabSelectedListener() {
          public void onTabSelected(TabLayout.Tab tab) {
            show(tab.getPosition());
          }

          public void onTabUnselected(TabLayout.Tab tab) {}

          public void onTabReselected(TabLayout.Tab tab) {}
        });
    int initial = state == null ? 0 : state.getInt("tab", 0);
    var selected = tabs.getTabAt(Math.max(0, Math.min(initial, tabs.getTabCount() - 1)));
    if (selected != null) selected.select();
    show(Math.max(0, tabs.getSelectedTabPosition()));
    ViewCompat.requestApplyInsets(root);
  }

  private void appearance(LinearLayout page) {
    LinearLayout master = card(page, true);
    enabled = new MaterialSwitch(this);
    enabled.setId(R.id.module_enabled);
    enabled.setText(R.string.enabled);
    enabled.setTextAppearance(
        com.google.android.material.R.style.TextAppearance_Material3_TitleMedium);
    enabled.setMinHeight(dp(56));
    enabled.setChecked(
        ConfigProvider.settings(this).getBoolean("enabled", true)
            && !ConfigProvider.settings(this).getBoolean("blocked", false));
    master.addView(enabled, new LinearLayout.LayoutParams(-1, -2));
    master.addView(text(getString(R.string.enabled_note), false));
    enabled.setOnCheckedChangeListener(
        (button, checked) -> {
          if (binding) return;
          ConfigProvider.settings(this).edit().putBoolean("enabled", checked).apply();
          ConfigProvider.changed(this);
          refreshStatus();
        });
    LinearLayout state = card(page, false);
    state.addView(text(getString(R.string.status_title), true));
    status = text("", false);
    status.setPadding(0, dp(8), 0, dp(4));
    state.addView(status);
    MaterialButton refresh = button(getString(R.string.settings_refresh));
    refresh.setOnClickListener(v -> refreshStatus());
    state.addView(refresh);
    LinearLayout looks = card(page, false);
    looks.addView(text(getString(R.string.appearance), true));
    MaterialTextView value = text(getString(R.string.font_size, font), false);
    value.setPadding(0, dp(12), 0, 0);
    looks.addView(value);
    Slider slider = new Slider(this);
    slider.setId(R.id.font_slider);
    slider.setValueFrom(12);
    slider.setValueTo(20);
    slider.setStepSize(1);
    slider.setValue(Math.max(12, Math.min(20, font)));
    slider.setContentDescription(getString(R.string.appearance));
    slider.addOnChangeListener(
        (view, amount, fromUser) -> {
          font = Math.round(amount);
          value.setText(getString(R.string.font_size, font));
          if (fromUser) {
            ConfigProvider.settings(this).edit().putInt("font", font).apply();
            ConfigProvider.changed(this);
          }
        });
    looks.addView(slider, new LinearLayout.LayoutParams(-1, -2));
    looks.addView(text(getString(R.string.appearance_note), false));
    LinearLayout setup = card(page, false);
    setup.addView(text(getString(R.string.setup_title), true));
    MaterialTextView body = text(getString(R.string.setup_body), false);
    body.setPadding(0, dp(10), 0, dp(10));
    setup.addView(body);
    MaterialButton dialer = button(getString(R.string.open_phone));
    dialer.setOnClickListener(
        v -> {
          try {
            startActivity(new Intent(Intent.ACTION_DIAL).setPackage("com.samsung.android.dialer"));
          } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.not_installed, Toast.LENGTH_SHORT).show();
          }
        });
    setup.addView(dialer);
    MaterialButton copy = button(getString(R.string.copy_code));
    copy.setOnClickListener(
        v -> {
          getSystemService(ClipboardManager.class)
              .setPrimaryClip(ClipData.newPlainText(getString(R.string.dial_code), "*#0011#"));
          Toast.makeText(this, R.string.code_copied, Toast.LENGTH_SHORT).show();
        });
    setup.addView(copy);
  }

  private void preview() {
    LinearLayout page = vertical();
    android.content.Context ui;
    try {
      ui = new ModuleUiContext(this);
    } catch (android.content.pm.PackageManager.NameNotFoundException e) {
      ui = this;
    }
    PanelView panel = new PanelView(ui, i -> {}, error -> {});
    previewPanel = panel;
    panel.sampleMode();
    panel.update(sample(), font);
    page.addView(panel, new LinearLayout.LayoutParams(-1, 0, 1));
    addPage(R.string.preview_tab, page);
  }

  static List<String> sample() {
    return List.of(
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
        "NR_BAND: --");
  }

  private void about(LinearLayout page) {
    LinearLayout intro = card(page, false);
    intro.addView(text("Galaxy Service Panel", true));
    intro.addView(text(BuildConfig.VERSION_NAME, false));
    MaterialTextView description = text(getString(R.string.about_body), false);
    description.setPadding(0, dp(12), 0, 0);
    intro.addView(description);
    LinearLayout compatibility = card(page, false);
    compatibility.addView(text(getString(R.string.compatibility), true));
    compatibility.addView(text(getString(R.string.compatibility_body), false));
    LinearLayout guard = card(page, false);
    guard.addView(text(getString(R.string.guard_title), true));
    guard.addView(text(getString(R.string.guard_body), false));
    MaterialButton reset = button(getString(R.string.guard_reset));
    reset.setId(R.id.guard_reset);
    reset.setOnClickListener(
        v -> {
          ConfigProvider.settings(this)
              .edit()
              .putBoolean("blocked", false)
              .putBoolean("enabled", true)
              .putLong("nonce", System.currentTimeMillis())
              .putString("state", "waiting")
              .apply();
          ConfigProvider.changed(this);
          enabled.setChecked(true);
          refreshStatus();
        });
    guard.addView(reset);
    MaterialButton licenses = button(getString(R.string.legal_title));
    licenses.setOnClickListener(v -> startActivity(new Intent(this, LicensesActivity.class)));
    page.addView(licenses, new LinearLayout.LayoutParams(-1, -2));
  }

  private void language() {
    new MaterialAlertDialogBuilder(this)
        .setTitle("Language / 言語")
        .setItems(
            new String[] {"System / システム", "日本語", "English"},
            (dialog, which) -> {
              String tag = which == 1 ? "ja" : which == 2 ? "en" : "";
              getSystemService(LocaleManager.class)
                  .setApplicationLocales(LocaleList.forLanguageTags(tag));
            })
        .show();
  }

  private void refreshStatus() {
    if (status == null) return;
    var prefs = ConfigProvider.settings(this);
    boolean blocked = prefs.getBoolean("blocked", false);
    enabled.setEnabled(!blocked);
    binding = true;
    enabled.setChecked(!blocked && prefs.getBoolean("enabled", true));
    binding = false;
    int message =
        blocked
            ? R.string.status_guard
            : !prefs.getBoolean("enabled", true)
                ? R.string.status_disabled
                : switch (prefs.getString("state", "waiting")) {
                  case "active" -> R.string.status_active;
                  case "unsupported" -> R.string.status_unsupported;
                  default -> R.string.status_pending;
                };
    long time = prefs.getLong("checked_at", 0);
    String stamp =
        time == 0
            ? ""
            : "\n"
                + getString(
                    R.string.status_updated,
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(new Date(time)));
    status.setText(getString(message) + stamp);
  }

  @Override
  protected void onStart() {
    super.onStart();
    ConfigProvider.settings(this).registerOnSharedPreferenceChangeListener(changed);
  }

  @Override
  protected void onStop() {
    ConfigProvider.settings(this).unregisterOnSharedPreferenceChangeListener(changed);
    super.onStop();
  }

  @Override
  protected void onResume() {
    super.onResume();
    refreshStatus();
  }

  @Override
  protected void onSaveInstanceState(Bundle out) {
    out.putInt("tab", tabs.getSelectedTabPosition());
    super.onSaveInstanceState(out);
  }

  private LinearLayout page(int title) {
    androidx.core.widget.NestedScrollView scroll = new androidx.core.widget.NestedScrollView(this);
    scroll.setFillViewport(true);
    LinearLayout body = vertical();
    body.setPadding(dp(20), dp(16), dp(20), dp(24));
    scroll.addView(body);
    addPage(title, scroll);
    return body;
  }

  private void addPage(int title, View page) {
    pages.add(page);
    content.addView(page, new FrameLayout.LayoutParams(-1, -1));
    page.setVisibility(pages.size() == 1 ? View.VISIBLE : View.GONE);
    tabs.addTab(tabs.newTab().setText(title));
  }

  private void show(int selected) {
    if (selected == 1 && previewPanel != null) previewPanel.update(sample(), font);
    for (int i = 0; i < pages.size(); i++)
      pages.get(i).setVisibility(i == selected ? View.VISIBLE : View.GONE);
  }

  private LinearLayout card(LinearLayout page, boolean master) {
    MaterialCardView card = new MaterialCardView(this);
    card.setRadius(dp(24));
    card.setCardElevation(0);
    card.setStrokeWidth(0);
    card.setCardBackgroundColor(
        color(
            master
                ? com.google.android.material.R.attr.colorPrimaryContainer
                : com.google.android.material.R.attr.colorSurfaceContainerLow));
    LinearLayout body = vertical();
    body.setPadding(dp(20), dp(16), dp(20), dp(16));
    card.addView(body);
    LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, -2);
    size.bottomMargin = dp(16);
    page.addView(card, size);
    return body;
  }

  private MaterialButton button(String label) {
    MaterialButton result =
        new MaterialButton(
            this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
    result.setText(label);
    result.setMinHeight(dp(48));
    return result;
  }

  private MaterialTextView text(String value, boolean heading) {
    MaterialTextView result = new MaterialTextView(this);
    result.setText(value);
    result.setTextAppearance(
        heading
            ? com.google.android.material.R.style.TextAppearance_Material3_TitleMedium
            : com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
    result.setLineSpacing(dp(3), 1);
    return result;
  }

  private LinearLayout vertical() {
    LinearLayout result = new LinearLayout(this);
    result.setOrientation(LinearLayout.VERTICAL);
    return result;
  }

  private int color(int attr) {
    return MaterialColors.getColor(this, attr, "GalaxyServicePanel");
  }

  private int dp(int value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }
}
