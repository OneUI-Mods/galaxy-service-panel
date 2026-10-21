package dev.ryuya.galaxyservicepanel;

import android.content.*;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textview.MaterialTextView;
import java.util.*;
import java.util.function.*;

/** The same presentation is used by the live hook and the explicitly labelled sample preview. */
final class PanelView extends LinearLayout {
  private final ListView list;
  private final Rows adapter = new Rows();
  private final MaterialTextView summary;
  private final IntConsumer selected;
  private final Consumer<Throwable> failed;
  private List<DiagnosticDocument.Row> rows = List.of();
  private boolean original;
  private int font = 14;

  PanelView(Context context, IntConsumer selected, Consumer<Throwable> failed) {
    super(context);
    this.selected = selected;
    this.failed = failed;
    setOrientation(VERTICAL);
    setBackgroundColor(color(com.google.android.material.R.attr.colorSurface));
    summary = text(context.getString(R.string.waiting_data), 20, true);
    summary.setPadding(dp(24), dp(18), dp(24), dp(4));
    addView(summary);
    MaterialTextView note = text(context.getString(R.string.live_values), 12, false);
    note.setTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant));
    note.setPadding(dp(24), 0, dp(24), dp(14));
    addView(note);
    TabLayout tabs = new TabLayout(context);
    tabs.setTabIndicatorFullWidth(false);
    tabs.addTab(tabs.newTab().setText(R.string.formatted));
    tabs.addTab(tabs.newTab().setText(R.string.original_text));
    tabs.addOnTabSelectedListener(
        new TabLayout.OnTabSelectedListener() {
          public void onTabSelected(TabLayout.Tab tab) {
            original = tab.getPosition() == 1;
            adapter.notifyDataSetChanged();
          }

          public void onTabUnselected(TabLayout.Tab tab) {}

          public void onTabReselected(TabLayout.Tab tab) {}
        });
    addView(tabs, new LayoutParams(-1, -2));
    list = new ListView(context);
    list.setId(R.id.panel_list);
    list.setDivider(null);
    list.setPadding(dp(16), dp(12), dp(16), dp(24));
    list.setClipToPadding(false);
    list.setAdapter(adapter);
    list.setOnItemClickListener(
        (parent, view, position, id) -> {
          if (position < rows.size() && !rows.get(position).menuKey().isEmpty())
            selected.accept(rows.get(position).sourceIndex());
        });
    list.setOnItemLongClickListener(
        (parent, view, position, id) -> {
          if (position >= rows.size()) return false;
          try {
            var row = rows.get(position);
            new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.source_line, row.sourceIndex() + 1))
                .setMessage(row.raw())
                .setPositiveButton(
                    R.string.copy,
                    (dialog, which) -> {
                      context
                          .getSystemService(ClipboardManager.class)
                          .setPrimaryClip(ClipData.newPlainText("Service Mode", row.raw()));
                    })
                .setNegativeButton(R.string.close, null)
                .show();
          } catch (Throwable error) {
            failed.accept(error);
          }
          return true;
        });
    addView(list, new LayoutParams(-1, 0, 1));
  }

  void update(List<String> source, int textSize) {
    var next = DiagnosticDocument.parse(source);
    int nextFont = Math.max(12, Math.min(20, textSize));
    if (rows.equals(next) && font == nextFont) return;
    int first = list.getFirstVisiblePosition();
    int anchor = first < rows.size() ? rows.get(first).sourceIndex() : -1;
    int offset = list.getChildCount() == 0 ? 0 : list.getChildAt(0).getTop();
    boolean shapeChanged = rows.size() != next.size();
    rows = next;
    font = nextFont;
    String status = DiagnosticDocument.summary(rows);
    summary.setText(
        status.isEmpty()
            ? getContext()
                .getString(
                    rows.isEmpty()
                        ? R.string.waiting_data
                        : rows.get(0).section() == DiagnosticDocument.Section.SELECTION
                            ? R.string.selection
                            : R.string.preview_title)
            : status);
    adapter.notifyDataSetChanged();
    if (shapeChanged && anchor >= 0)
      for (int i = 0; i < rows.size(); i++)
        if (rows.get(i).sourceIndex() == anchor) {
          list.setSelectionFromTop(i, offset);
          break;
        }
  }

  private final class Rows extends BaseAdapter {
    public int getCount() {
      return rows.size();
    }

    public Object getItem(int position) {
      return rows.get(position);
    }

    public long getItemId(int position) {
      return rows.get(position).sourceIndex();
    }

    @Override
    public boolean hasStableIds() {
      return true;
    }

    @Override
    public boolean areAllItemsEnabled() {
      return true;
    }

    public View getView(int position, View recycled, ViewGroup parent) {
      try {
        return rowView(position);
      } catch (Throwable error) {
        // A render exception must not escape into the phone process layout traversal.
        failed.accept(error);
        TextView fallback = new TextView(getContext());
        fallback.setText(rows.get(position).raw());
        return fallback;
      }
    }
  }

  private View rowView(int position) {
    var row = rows.get(position);
    boolean start = position == 0 || rows.get(position - 1).section() != row.section();
    boolean end = position == rows.size() - 1 || rows.get(position + 1).section() != row.section();
    LinearLayout result = vertical();
    if (start && !original) {
      MaterialTextView heading =
          text(getContext().getString(sectionTitle(row.section())), 12, true);
      heading.setTextColor(color(androidx.appcompat.R.attr.colorPrimary));
      heading.setPadding(dp(8), dp(position == 0 ? 4 : 20), dp(8), dp(10));
      result.addView(heading);
    }
    MaterialCardView card = new MaterialCardView(getContext());
    card.setCardElevation(0);
    card.setStrokeWidth(0);
    card.setCardBackgroundColor(color(com.google.android.material.R.attr.colorSurfaceContainerLow));
    float top = start || original ? dp(20) : 0;
    float bottom = end || original ? dp(20) : 0;
    card.setShapeAppearanceModel(
        card.getShapeAppearanceModel().toBuilder()
            .setTopLeftCornerSize(top)
            .setTopRightCornerSize(top)
            .setBottomLeftCornerSize(bottom)
            .setBottomRightCornerSize(bottom)
            .build());
    LinearLayout body = vertical();
    body.setPadding(dp(20), dp(14), dp(20), dp(14));
    if (original) {
      MaterialTextView source =
          text(getContext().getString(R.string.source_line, row.sourceIndex() + 1), 11, false);
      source.setTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant));
      body.addView(source);
      MaterialTextView raw = text(row.raw(), font, false);
      raw.setTypeface(Typeface.MONOSPACE);
      raw.setPadding(0, dp(4), 0, 0);
      body.addView(raw);
    } else if (!row.menuKey().isEmpty()) {
      LinearLayout menu = new LinearLayout(getContext());
      menu.setGravity(Gravity.CENTER_VERTICAL);
      MaterialTextView number = text(row.menuKey(), 18, true);
      number.setTextColor(color(androidx.appcompat.R.attr.colorPrimary));
      menu.addView(number, new LayoutParams(dp(40), -2));
      MaterialTextView label = text(menuTitle(row.text()), font + 2, true);
      menu.addView(label, new LayoutParams(0, -2, 1));
      menu.addView(text("›", 24, false));
      body.setMinimumHeight(dp(68));
      body.setGravity(Gravity.CENTER_VERTICAL);
      body.addView(menu);
    } else if (row.values().isEmpty()) {
      body.addView(
          text(
              row.text().equals("BASIC INFORMATION")
                  ? getContext().getString(R.string.basic_info)
                  : row.text(),
              font,
              false));
    } else if (row.values().size() > 1) {
      LinearLayout metrics = new LinearLayout(getContext());
      for (var value : row.values()) {
        LinearLayout cell = vertical();
        MaterialTextView name = text(value.label(), 11, false);
        name.setTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant));
        cell.addView(name);
        MaterialTextView amount = text(value.text(), font + 2, true);
        amount.setPadding(0, dp(3), dp(6), 0);
        amount.setFontFeatureSettings("tnum");
        cell.addView(amount);
        metrics.addView(cell, new LayoutParams(0, -2, 1));
      }
      body.addView(metrics);
    } else {
      var value = row.values().get(0);
      LinearLayout pair = new LinearLayout(getContext());
      pair.setGravity(Gravity.CENTER_VERTICAL);
      MaterialTextView label = text(value.label(), font - 1, false);
      label.setTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant));
      label.setPadding(0, 0, dp(12), 0);
      pair.addView(label, new LayoutParams(0, -2, 1));
      MaterialTextView amount = text(value.text(), font, true);
      amount.setFontFeatureSettings("tnum");
      amount.setGravity(Gravity.END);
      pair.addView(amount, new LayoutParams(0, -2, 1));
      body.addView(pair);
    }
    card.addView(body, new FrameLayout.LayoutParams(-1, -2));
    LayoutParams size = new LayoutParams(-1, -2);
    size.bottomMargin = dp(original ? 8 : 1);
    result.addView(card, size);
    return result;
  }

  private String menuTitle(String text) {
    var matcher = java.util.regex.Pattern.compile("SIM([0-9]+) INFORMATION").matcher(text);
    return matcher.matches() ? getContext().getString(R.string.sim_info, matcher.group(1)) : text;
  }

  private static int sectionTitle(DiagnosticDocument.Section section) {
    return switch (section) {
      case SELECTION -> R.string.selection;
      case CONNECTION -> R.string.connection;
      case LTE -> R.string.lte;
      case NR -> R.string.nr;
      case DETAILS -> R.string.details;
    };
  }

  private LinearLayout vertical() {
    LinearLayout result = new LinearLayout(getContext());
    result.setOrientation(VERTICAL);
    return result;
  }

  private MaterialTextView text(String value, int sp, boolean strong) {
    MaterialTextView view = new MaterialTextView(getContext());
    view.setText(value);
    view.setTextSize(sp);
    view.setTextColor(color(com.google.android.material.R.attr.colorOnSurface));
    if (strong) view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    view.setLineSpacing(dp(2), 1);
    return view;
  }

  private int color(int attr) {
    return MaterialColors.getColor(this, attr);
  }

  private int dp(int value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }
}
