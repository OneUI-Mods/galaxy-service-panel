package dev.ryuya.galaxyservicepanel;

import android.content.*;
import android.content.res.*;
import android.view.*;

/** Module resources and classes, but the host Activity's window token and display services. */
final class ModuleUiContext extends ContextThemeWrapper {
  private final Resources resources;
  private LayoutInflater inflater;
  private Resources.Theme theme;

  ModuleUiContext(Context host) throws android.content.pm.PackageManager.NameNotFoundException {
    super(host, R.style.AppTheme);
    resources =
        host.createPackageContext(ConfigProvider.PACKAGE, Context.CONTEXT_IGNORE_SECURITY)
            .getResources();
  }

  @Override
  public Resources.Theme getTheme() {
    if (theme == null) {
      // Copying the host Theme also copies unrelated 0x7f attribute IDs and can silently
      // turn fitsSystemWindows on for every child, discarding explicit view padding.
      theme = resources.newTheme();
      theme.applyStyle(R.style.AppTheme, true);
    }
    return theme;
  }

  @Override
  public void setTheme(int resource) {
    getTheme().applyStyle(resource, true);
  }

  @Override
  public Resources getResources() {
    return resources;
  }

  @Override
  public AssetManager getAssets() {
    return resources.getAssets();
  }

  @Override
  public ClassLoader getClassLoader() {
    return ModuleUiContext.class.getClassLoader();
  }

  @Override
  public Object getSystemService(String name) {
    if (Context.LAYOUT_INFLATER_SERVICE.equals(name)) {
      if (inflater == null) inflater = LayoutInflater.from(getBaseContext()).cloneInContext(this);
      return inflater;
    }
    return super.getSystemService(name);
  }
}
