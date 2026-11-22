package dev.ryuya.galaxyservicepanel

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import dev.ryuya.galaxyservicepanel.compat.DexSymbols
import dev.ryuya.galaxyservicepanel.compat.compatibleDeclaredMethod
import dev.ryuya.galaxyservicepanel.startup.FirstUnlockGate
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.HookHandle
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.HotReloadedParam
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.util.ArrayList
import java.util.Collections
import java.util.WeakHashMap
import java.util.function.Consumer
import kotlin.math.log

/* Hooks only the Service Mode Activity; no system-wide Activity or telephony method hooks. */
class HookEntry : XposedModule() {
    private val firstUnlock = FirstUnlockGate()
    private val main = Handler(Looper.getMainLooper())
    private var host: Context? = null
    private var hostLoader: ClassLoader? = null
    @Volatile private var retired = false
    private val installed: MutableSet<ClassLoader?>? =
        Collections.newSetFromMap<ClassLoader?>(WeakHashMap<ClassLoader?, Boolean?>())

    override fun onPackageReady(param: PackageReadyParam) {
        if (ConfigProvider.TARGET != param.getPackageName() || HookProfile.current() == null) return
        val loader = param.getClassLoader()
        if (!installed!!.add(loader)) return
        val bootstrap =
            Application::class.java.compatibleDeclaredMethod("attach", Context::class.java)
        hook(bootstrap).intercept { chain ->
            val result = chain.proceed()
            val context = chain.args[0] as Context
            firstUnlock.run(context) {
                DexSymbols.whenPrepared(context) {
                    if (!retired && installActivity(loader)) {
                        host = context
                        hostLoader = loader
                    }
                }
            }
            result
        }
    }

    override fun onHotReloading(param: HotReloadingParam): Boolean {
        return MainThreadReload.run(main) handOff@{
            val context = host ?: return@handOff false
            val loader = hostLoader ?: return@handOff false
            if (!PanelController.canRetire()) return@handOff false
            param.setSavedInstanceState(arrayOf(context, loader, Application.getProcessName()))
            retired = true
            DexSymbols.detachObserver()
            PanelController.retire()
            true
        }
    }

    override fun onHotReloaded(param: HotReloadedParam) {
        val state = param.savedInstanceState as? Array<*> ?: return
        val context = state.getOrNull(0) as? Context ?: return
        val loader = state.getOrNull(1) as? ClassLoader ?: return
        if (state.getOrNull(2) != param.processName) return
        val oldHooks = param.oldHookHandles
        main.post {
            DexSymbols.whenPrepared(context) {
                if (!installActivity(loader)) return@whenPrepared
                host = context
                hostLoader = loader
                oldHooks.forEach { it.unhook() }
                log(4, "GalaxyServicePanel", "ONEUI_MODS_HOT_RELOAD package=${BuildConfig.APPLICATION_ID} " +
                    "version=${BuildConfig.VERSION_CODE} pid=${Process.myPid()}")
            }
        }
    }

    private fun installActivity(loader: ClassLoader): Boolean {
        val handles = ArrayList<HookHandle?>()
        try {
            val activity =
                DexSymbols.findClass(ConfigProvider.TARGET + ".ServiceModeApp", false, loader)
            handles.add(
                hook(activity.compatibleDeclaredMethod("onCreate", Bundle::class.java))
                    .setId("service-panel-create")
                    .intercept(
                        Hooker { chain: Chain? ->
                            val result = chain!!.proceed()
                            if (!retired) PanelController.created(chain!!.getThisObject() as Activity)
                            result
                        },
                    ),
            )
            handles.add(
                hook(activity.compatibleDeclaredMethod("onResume"))
                    .setId("service-panel-resume")
                    .intercept(
                        Hooker { chain: Chain? ->
                            val result = chain!!.proceed()
                            if (!retired) PanelController.resumed(chain!!.getThisObject() as Activity)
                            result
                        },
                    ),
            )
            handles.add(
                hook(activity.compatibleDeclaredMethod("onPause"))
                    .setId("service-panel-pause")
                    .intercept(
                        Hooker { chain: Chain? ->
                            val result = chain!!.proceed()
                            if (!retired) PanelController.paused(chain!!.getThisObject() as Activity)
                            result
                        },
                    ),
            )
            handles.add(
                hook(activity.compatibleDeclaredMethod("onDestroy"))
                    .setId("service-panel-destroy")
                    .intercept(
                        Hooker { chain: Chain? ->
                            if (!retired) PanelController.destroyed(chain!!.getThisObject() as Activity?)
                            chain!!.proceed()
                        },
                    ),
            )
            log(
                4,
                "GalaxyServicePanel",
                "Service Mode presentation hooks ready: " + HookProfile.current()!!.name,
            )
            return true
        } catch (error: Throwable) {
            handles.forEach(Consumer { h: HookHandle? -> h!!.unhook() })
            log(
                6,
                "GalaxyServicePanel",
                "Unsupported Service Mode layout: " + error.javaClass.getSimpleName(),
            )
            return false
        }
    }
}
