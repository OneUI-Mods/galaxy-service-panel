package dev.ryuya.galaxyservicepanel;

import android.content.*;
import android.content.res.*;
import android.view.*;

/** Module resources and classes, but the host Activity's window token and display services. */
final class ModuleUiContext extends ContextThemeWrapper {
  private final Resources resources;
  private LayoutInflater inflater;

  ModuleUiContext(Context host) throws android.content.pm.PackageManager.NameNotFoundException {
    super(host, R.style.AppTheme);
    resources =
        host.createPackageContext(ConfigProvider.PACKAGE, Context.CONTEXT_IGNORE_SECURITY)
            .getResources();
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
