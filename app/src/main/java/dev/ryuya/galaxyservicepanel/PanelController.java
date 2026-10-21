package dev.ryuya.galaxyservicepanel;

import android.app.*;
import android.content.*;
import android.database.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.google.android.material.appbar.MaterialToolbar;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.*;

/** Owns presentation only. The native adapter, listener, handler and modem commands stay intact. */
final class PanelController {
  private record Preferences(boolean enabled, int font, long nonce) {}

  private record Child(View view, ViewGroup.LayoutParams params) {}

  private static final Map<Activity, WeakReference<PanelController>> ACTIVE = new WeakHashMap<>();
  private static final ExecutorService IO =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread thread = new Thread(r, "ServicePanelConfig");
            thread.setDaemon(true);
            return thread;
          });
  private final Activity host;
  private final Handler main = new Handler(Looper.getMainLooper());
  private final ListView nativeList;
  private final ArrayAdapter<?> nativeAdapter;
  private final ViewGroup content;
  private final PhoneGuard guard;
  private final DataSetObserver data;
  private final ContentObserver config;
  private final Runnable render = this::render;
  private final List<Child> children = new ArrayList<>();
  private List<String> snapshot = List.of();
  private LinearLayout root;
  private PanelView panel;
  private Preferences preferences;
  private boolean destroyed, attached, pending, querying, faulted, actionBarWasVisible, observing;
  private long settingsGeneration;

  static void created(Activity activity) {
    if (!"0011".equals(activity.getIntent().getStringExtra("keyString"))) return;
    try {
      HookProfile profile = HookProfile.current();
      int listId = activity.getResources().getIdentifier("list", "id", ConfigProvider.TARGET);
      View candidate = activity.findViewById(listId);
      if (profile == null
          || !(candidate instanceof ListView list)
          || !(list.getAdapter() instanceof ArrayAdapter<?> adapter)) {
        status(activity, "unsupported");
        return;
      }
      PanelController controller = new PanelController(activity, list, adapter);
      ACTIVE.put(activity, new WeakReference<>(controller));
      controller.refresh();
    } catch (Throwable error) {
      status(activity, "blocked");
    }
  }

  static void resumed(Activity activity) {
    var ref = ACTIVE.get(activity);
    var controller = ref == null ? null : ref.get();
    if (controller != null) controller.refresh();
  }

  static void destroyed(Activity activity) {
    var ref = ACTIVE.remove(activity);
    var controller = ref == null ? null : ref.get();
    if (controller != null) controller.close();
  }

  private PanelController(Activity host, ListView list, ArrayAdapter<?> adapter) {
    this.host = host;
    nativeList = list;
    nativeAdapter = adapter;
    content = host.findViewById(android.R.id.content);
    guard = new PhoneGuard(host);
    data =
        new DataSetObserver() {
          @Override
          public void onChanged() {
            schedule();
          }

          @Override
          public void onInvalidated() {
            schedule();
          }
        };
    config =
        new ContentObserver(main) {
          @Override
          public void onChange(boolean self) {
            settingsGeneration++;
            refresh();
          }
        };
    host.getContentResolver().registerContentObserver(ConfigProvider.URI, false, config);
  }

  private void refresh() {
    if (destroyed || querying) return;
    querying = true;
    long generation = settingsGeneration;
    IO.execute(
        () -> {
          Preferences read = null;
          try (Cursor cursor =
              host.getContentResolver().query(ConfigProvider.URI, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst())
              read = new Preferences(cursor.getInt(0) != 0, cursor.getInt(1), cursor.getLong(2));
          } catch (RuntimeException ignored) {
          }
          Preferences next = read;
          main.post(
              () -> {
                querying = false;
                if (destroyed) return;
                if (generation != settingsGeneration) {
                  refresh();
                  return;
                }
                try {
                  if (next == null || !next.enabled()) {
                    restore();
                    status(host, "disabled");
                    return;
                  }
                  if (preferences != null && preferences.nonce() != next.nonce()) faulted = false;
                  preferences = next;
                  if (faulted) return;
                  if (!attached) attach();
                  schedule();
                } catch (Throwable error) {
                  fail(error);
                }
              });
        });
  }

  private void attach() throws Exception {
    if (!guard.arm(preferences.nonce())) {
      faulted = true;
      status(host, "blocked");
      return;
    }
    ModuleUiContext context = new ModuleUiContext(host);
    LinearLayout nextRoot = new LinearLayout(context);
    nextRoot.setOrientation(LinearLayout.VERTICAL);
    MaterialToolbar toolbar = new MaterialToolbar(context);
    toolbar.setTitle(R.string.preview_title);
    toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
    toolbar.setNavigationContentDescription(R.string.back);
    toolbar.setNavigationOnClickListener(
        v -> {
          try {
            if (snapshot.stream()
                .filter(Objects::nonNull)
                .anyMatch(s -> s.strip().equals("BASIC INFORMATION"))) host.finish();
            else nativeMenu("Back");
          } catch (Throwable error) {
            fail(error);
          }
        });
    // Native menu titles are kept exact because Samsung dispatches these actions by title.
    host.onCreateOptionsMenu(toolbar.getMenu());
    toolbar.getMenu().add(Menu.NONE, 0x47535001, 100, R.string.show_stock);
    toolbar.setOnMenuItemClickListener(
        item -> {
          try {
            if (item.getItemId() == 0x47535001) {
              IO.execute(
                  () -> {
                    try {
                      host.getContentResolver().call(ConfigProvider.URI, "disable", null, null);
                    } catch (RuntimeException ignored) {
                    }
                  });
            } else host.onMenuItemSelected(Window.FEATURE_OPTIONS_PANEL, item);
          } catch (Throwable error) {
            fail(error);
          }
          return true;
        });
    nextRoot.addView(toolbar, new LinearLayout.LayoutParams(-1, -2));
    PanelView nextPanel = new PanelView(context, this::select, this::fail);
    nextRoot.addView(nextPanel, new LinearLayout.LayoutParams(-1, 0, 1));
    nextRoot.setOnApplyWindowInsetsListener(
        (view, insets) -> {
          var bars =
              insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
          view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
          return insets;
        });
    for (int i = 0; i < content.getChildCount(); i++) {
      View child = content.getChildAt(i);
      children.add(new Child(child, child.getLayoutParams()));
    }
    root = nextRoot;
    panel = nextPanel;
    attached = true;
    ActionBar bar = host.getActionBar();
    actionBarWasVisible = bar != null && bar.isShowing();
    if (actionBarWasVisible) bar.hide();
    content.removeAllViews();
    content.addView(root, new ViewGroup.LayoutParams(-1, -1));
    nativeAdapter.registerDataSetObserver(data);
    observing = true;
    root.requestApplyInsets();
    status(host, "active");
  }

  private void nativeMenu(String title) {
    android.widget.PopupMenu menu = new android.widget.PopupMenu(host, nativeList);
    host.onMenuItemSelected(Window.FEATURE_OPTIONS_PANEL, menu.getMenu().add(title));
  }

  private void select(int index) {
    try {
      if (!attached || index >= snapshot.size() || index >= nativeAdapter.getCount()) return;
      Object current = nativeAdapter.getItem(index);
      if (!Objects.equals(snapshot.get(index), current)) {
        schedule();
        return;
      }
      nativeList.performItemClick(null, index, nativeAdapter.getItemId(index));
    } catch (Throwable error) {
      fail(error);
    }
  }

  private void schedule() {
    if (destroyed || !attached || pending) return;
    pending = true;
    // clear/addAll/explicit notify are coalesced into one complete native snapshot per frame.
    main.post(render);
  }

  private void render() {
    pending = false;
    if (!attached || destroyed) return;
    try {
      List<String> values = new ArrayList<>();
      for (int i = 0; i < nativeAdapter.getCount(); i++) {
        Object value = nativeAdapter.getItem(i);
        if (value != null && !(value instanceof String))
          throw new IllegalStateException("Unexpected native row type");
        values.add(value == null ? "" : (String) value);
      }
      snapshot = List.copyOf(values);
      panel.update(snapshot, preferences.font());
    } catch (Throwable error) {
      fail(error);
    }
  }

  private void restore() {
    main.removeCallbacks(render);
    pending = false;
    guard.disarm();
    if (!attached) return;
    if (observing) {
      nativeAdapter.unregisterDataSetObserver(data);
      observing = false;
    }
    content.removeView(root);
    for (Child child : children) content.addView(child.view(), child.params());
    children.clear();
    if (actionBarWasVisible && host.getActionBar() != null) host.getActionBar().show();
    attached = false;
    panel = null;
    root = null;
    snapshot = List.of();
  }

  private void fail(Throwable error) {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      main.post(() -> fail(error));
      return;
    }
    faulted = true;
    // getView may fail during layout. Restore on the next main-loop turn, never within traversal.
    main.post(
        () -> {
          try {
            restore();
          } catch (Throwable ignored) {
          }
          status(host, "blocked");
        });
    android.util.Log.e(
        "GalaxyServicePanel", "Formatting disabled: " + error.getClass().getSimpleName());
  }

  private void close() {
    if (destroyed) return;
    destroyed = true;
    try {
      host.getContentResolver().unregisterContentObserver(config);
    } catch (RuntimeException ignored) {
    }
    try {
      restore();
    } catch (Throwable ignored) {
    }
    if (!faulted) status(host, "waiting");
  }

  private static void status(Context context, String state) {
    IO.execute(
        () -> {
          try {
            Bundle data = new Bundle();
            data.putString("state", state);
            context.getContentResolver().call(ConfigProvider.URI, "status", null, data);
          } catch (RuntimeException ignored) {
          }
        });
  }
}
