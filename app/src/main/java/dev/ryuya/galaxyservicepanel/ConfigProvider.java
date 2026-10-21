package dev.ryuya.galaxyservicepanel;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.*;
import android.os.Process;

/** Public display preferences; status writes are limited to this app and Samsung's phone UID. */
public final class ConfigProvider extends ContentProvider {
  static final String PACKAGE = "dev.ryuya.galaxyservicepanel";
  static final String TARGET = "com.sec.android.RilServiceModeApp";
  static final Uri URI = Uri.parse("content://" + PACKAGE + ".config/settings");

  static SharedPreferences settings(Context c) {
    return c.getSharedPreferences("config", Context.MODE_PRIVATE);
  }

  static void changed(Context c) {
    c.getContentResolver().notifyChange(URI, null);
  }

  @Override
  public boolean onCreate() {
    return true;
  }

  @Override
  public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
    if (!URI.equals(uri)) throw new IllegalArgumentException("Unknown settings URI");
    var p = settings(getContext());
    MatrixCursor result = new MatrixCursor(new String[] {"enabled", "font", "nonce"});
    result.addRow(
        new Object[] {
          p.getBoolean("enabled", true) && !p.getBoolean("blocked", false) ? 1 : 0,
          Math.max(12, Math.min(20, p.getInt("font", 14))),
          p.getLong("nonce", 0)
        });
    result.setNotificationUri(getContext().getContentResolver(), URI);
    return result;
  }

  @Override
  public Bundle call(String method, String arg, Bundle data) {
    int uid = Binder.getCallingUid();
    boolean own = uid == Process.myUid();
    try {
      if (!own && uid != getContext().getPackageManager().getApplicationInfo(TARGET, 0).uid)
        throw new SecurityException("Samsung phone UID required");
    } catch (android.content.pm.PackageManager.NameNotFoundException e) {
      throw new SecurityException(e);
    }
    if (method.equals("disable")) {
      settings(getContext()).edit().putBoolean("enabled", false).apply();
      changed(getContext());
      return Bundle.EMPTY;
    }
    if (!method.equals("status") || data == null)
      throw new IllegalArgumentException("Unknown status operation");
    String state = data.getString("state", "");
    if (!java.util.Set.of("active", "disabled", "unsupported", "blocked", "waiting")
        .contains(state)) throw new IllegalArgumentException("Invalid state");
    var edit =
        settings(getContext())
            .edit()
            .putString("state", state)
            .putLong("checked_at", System.currentTimeMillis());
    if (state.equals("blocked")) edit.putBoolean("blocked", true).putBoolean("enabled", false);
    edit.apply();
    // Status changes do not recursively trigger the hooked settings observer.
    return Bundle.EMPTY;
  }

  @Override
  public String getType(Uri uri) {
    return "vnd.android.cursor.item/vnd.galaxyservicepanel.config";
  }

  @Override
  public Uri insert(Uri uri, ContentValues values) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int delete(Uri uri, String where, String[] args) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int update(Uri uri, ContentValues values, String where, String[] args) {
    throw new UnsupportedOperationException();
  }
}
