package dev.ryuya.galaxyservicepanel

import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import android.content.res.Resources.Theme
import android.view.ContextThemeWrapper
import android.view.LayoutInflater

/* Module resources and classes, but the host Activity's window token and display services. */
class ModuleUiContext(host: Context) : ContextThemeWrapper(host, R.style.AppTheme) {
    private val resources: Resources
    private var inflater: LayoutInflater? = null
    private var theme: Theme? = null

    init {
        resources =
            host
                .createPackageContext(ConfigProvider.PACKAGE, Context.CONTEXT_IGNORE_SECURITY)
                .getResources()
    }

    override fun getTheme(): Theme? {
        if (theme == null) {
            // Copying the host Theme also copies unrelated 0x7f attribute IDs and can silently
            // turn fitsSystemWindows on for every child, discarding explicit view padding.
            theme = resources.newTheme()
            theme!!.applyStyle(R.style.AppTheme, true)
        }
        return theme
    }

    override fun setTheme(resource: Int) {
        getTheme()!!.applyStyle(resource, true)
    }

    override fun getResources(): Resources {
        return resources
    }

    override fun getAssets(): AssetManager? {
        return resources.getAssets()
    }

    override fun getClassLoader(): ClassLoader? {
        return ModuleUiContext::class.java.getClassLoader()
    }

    override fun getSystemService(name: String): Any? {
        if (Context.LAYOUT_INFLATER_SERVICE == name) {
            if (inflater == null)
                inflater = LayoutInflater.from(getBaseContext()).cloneInContext(this)
            return inflater
        }
        return super.getSystemService(name!!)
    }
}
