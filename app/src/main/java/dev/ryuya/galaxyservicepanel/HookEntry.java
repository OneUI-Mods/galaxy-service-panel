package dev.ryuya.galaxyservicepanel;

import android.app.Activity;
import android.os.Bundle;
import io.github.libxposed.api.XposedModule;
import java.util.*;

/** Hooks only the Service Mode Activity; no system-wide Activity or telephony method hooks. */
public final class HookEntry extends XposedModule {
  private final Set<ClassLoader> installed = Collections.newSetFromMap(new WeakHashMap<>());

  @Override
  public void onPackageReady(PackageReadyParam param) {
    if (!ConfigProvider.TARGET.equals(param.getPackageName()) || HookProfile.current() == null)
      return;
    ClassLoader loader = param.getClassLoader();
    if (!installed.add(loader)) return;
    List<io.github.libxposed.api.XposedInterface.HookHandle> handles = new ArrayList<>();
    try {
      Class<?> activity = Class.forName(ConfigProvider.TARGET + ".ServiceModeApp", false, loader);
      handles.add(
          hook(activity.getDeclaredMethod("onCreate", Bundle.class))
              .intercept(
                  chain -> {
                    Object result = chain.proceed();
                    PanelController.created((Activity) chain.getThisObject());
                    return result;
                  }));
      handles.add(
          hook(activity.getDeclaredMethod("onResume"))
              .intercept(
                  chain -> {
                    Object result = chain.proceed();
                    PanelController.resumed((Activity) chain.getThisObject());
                    return result;
                  }));
      handles.add(
          hook(activity.getDeclaredMethod("onDestroy"))
              .intercept(
                  chain -> {
                    PanelController.destroyed((Activity) chain.getThisObject());
                    return chain.proceed();
                  }));
      log(
          4,
          "GalaxyServicePanel",
          "Service Mode presentation hooks ready: " + HookProfile.current().name());
    } catch (Throwable error) {
      handles.forEach(h -> h.unhook());
      log(
          6,
          "GalaxyServicePanel",
          "Unsupported Service Mode layout: " + error.getClass().getSimpleName());
    }
  }
}
