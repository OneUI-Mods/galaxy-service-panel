package dev.ryuya.galaxyservicepanel

import android.app.Activity
import android.os.Bundle
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.HookHandle
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.util.ArrayList
import java.util.Collections
import java.util.WeakHashMap
import java.util.function.Consumer
import kotlin.math.log

/* Hooks only the Service Mode Activity; no system-wide Activity or telephony method hooks. */
class HookEntry : XposedModule() {
    private val installed: MutableSet<ClassLoader?>? =
        Collections.newSetFromMap<ClassLoader?>(WeakHashMap<ClassLoader?, Boolean?>())

    override fun onPackageReady(param: PackageReadyParam) {
        if (ConfigProvider.TARGET != param.getPackageName() || HookProfile.current() == null) return
        val loader = param.getClassLoader()
        if (!installed!!.add(loader)) return
        val handles = ArrayList<HookHandle?>()
        try {
            val activity = Class.forName(ConfigProvider.TARGET + ".ServiceModeApp", false, loader)
            handles.add(
                hook(activity.getDeclaredMethod("onCreate", Bundle::class.java))
                    .intercept(
                        Hooker { chain: Chain? ->
                            val result = chain!!.proceed()
                            PanelController.created(chain!!.getThisObject() as Activity)
                            result
                        }
                    )
            )
            handles.add(
                hook(activity.getDeclaredMethod("onResume"))
                    .intercept(
                        Hooker { chain: Chain? ->
                            val result = chain!!.proceed()
                            PanelController.resumed(chain!!.getThisObject() as Activity)
                            result
                        }
                    )
            )
            handles.add(
                hook(activity.getDeclaredMethod("onPause"))
                    .intercept(
                        Hooker { chain: Chain? ->
                            val result = chain!!.proceed()
                            PanelController.paused(chain!!.getThisObject() as Activity)
                            result
                        }
                    )
            )
            handles.add(
                hook(activity.getDeclaredMethod("onDestroy"))
                    .intercept(
                        Hooker { chain: Chain? ->
                            PanelController.destroyed(chain!!.getThisObject() as Activity?)
                            chain!!.proceed()
                        }
                    )
            )
            log(
                4,
                "GalaxyServicePanel",
                "Service Mode presentation hooks ready: " + HookProfile.current()!!.name,
            )
        } catch (error: Throwable) {
            handles.forEach(Consumer { h: HookHandle? -> h!!.unhook() })
            log(
                6,
                "GalaxyServicePanel",
                "Unsupported Service Mode layout: " + error.javaClass.getSimpleName(),
            )
        }
    }
}
