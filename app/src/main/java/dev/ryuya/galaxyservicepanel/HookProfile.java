package dev.ryuya.galaxyservicepanel;

import android.os.Build;

/**
 * Separate OS records; both require the native ListView/String ArrayAdapter contract at runtime.
 */
record HookProfile(String name, int sdk) {
  static final HookProfile ANDROID_16 = new HookProfile("Android 16", 36);
  static final HookProfile ANDROID_17 = new HookProfile("Android 17", 37);

  static HookProfile current() {
    return switch (Build.VERSION.SDK_INT) {
      case 36 -> ANDROID_16;
      case 37 -> ANDROID_17;
      default -> null;
    };
  }
}
