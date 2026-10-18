package io.github.nexalloy.nexfusion.gboard

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Proxy

/**
 * Injects "Advanced settings" into Gboard's main settings menu and removes promotional entries.
 *
 * Targets:
 * - `com.google.android.libraries.inputmethod.preferencewidgets.PreferenceHeaderFragment`
 * - Preference hierarchy cleanup ("Rate Gboard" / "Help & feedback")
 * - Dynamic preference injection with Material 3 sparkle branding
 */
object GboardSettingsHook {

    private const val TAG = "NexAlloy"
    private const val ADVANCED_SETTINGS_KEY = "gboard_patches_entry"

    fun install(classLoader: ClassLoader) {
        val headerFragmentClass = runCatching {
            classLoader.loadClass("com.google.android.libraries.inputmethod.preferencewidgets.PreferenceHeaderFragment")
        }.getOrNull()

        val commonFragmentClass = runCatching {
            classLoader.loadClass("com.google.android.libraries.inputmethod.preferencewidgets.CommonPreferenceFragment")
        }.getOrNull()

        val prefFragmentCompatClass = runCatching {
            classLoader.loadClass("ebw")
        }.getOrNull() ?: runCatching {
            classLoader.loadClass("androidx.preference.PreferenceFragmentCompat")
        }.getOrNull()

        if (headerFragmentClass == null && commonFragmentClass == null && prefFragmentCompatClass == null) {
            Log.w(TAG, "GboardSettingsHook: Neither PreferenceHeaderFragment nor CommonPreferenceFragment found")
            return
        }

        val injectionHook = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val fragment = param.thisObject ?: return
                val context = resolveContext(fragment)
                    ?: (param.args.firstOrNull { it is Context } as? Context)
                    ?: (param.args.firstOrNull { it is View } as? View)?.context
                    ?: return

                val explicitGroup = param.args.firstOrNull {
                    it != null && it.javaClass.name.contains("PreferenceGroup")
                }

                injectAdvancedSettingsPreference(fragment, context, classLoader, explicitGroup)
            }
        }

        // 1. Hook methods on PreferenceHeaderFragment
        if (headerFragmentClass != null) {
            for (methodName in listOf("aE", "aK", "b", "aL")) {
                runCatching {
                    val methods = headerFragmentClass.declaredMethods.filter { it.name == methodName }
                    for (m in methods) {
                        XposedBridge.hookMethod(m, injectionHook)
                    }
                }
            }
        }

        // 2. Hook methods on CommonPreferenceFragment
        if (commonFragmentClass != null) {
            for (methodName in listOf("ao", "bb")) {
                runCatching {
                    val methods = commonFragmentClass.declaredMethods.filter { it.name == methodName }
                    for (m in methods) {
                        XposedBridge.hookMethod(m, injectionHook)
                    }
                }
            }
        }

        // 3. Hook methods on PreferenceFragmentCompat (ebw)
        if (prefFragmentCompatClass != null) {
            for (methodName in listOf("az", "setPreferenceScreen")) {
                runCatching {
                    val methods = prefFragmentCompatClass.declaredMethods.filter { it.name == methodName }
                    for (m in methods) {
                        XposedBridge.hookMethod(m, injectionHook)
                    }
                }
            }
        }

        // 4. Hook preference clicks on the fragment hierarchy to show the Advanced Settings dialog
        val clickHook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val pref = param.args.getOrNull(0) ?: return
                val key = getPreferenceKey(pref)
                val title = getPreferenceTitle(pref)
                if (key == ADVANCED_SETTINGS_KEY || title.contains("Advanced settings", ignoreCase = true)) {
                    Log.i(TAG, "NexFusion: Intercepted click on Advanced settings preference!")
                    val ctx = resolveContext(param.thisObject)
                        ?: resolveContext(pref)
                    val act = resolveActivity(ctx)
                    if (act != null) {
                        Log.i(TAG, "NexFusion: Showing Advanced settings dialog on $act")
                        act.runOnUiThread {
                            GboardSettingsDialog.show(act)
                        }
                    } else {
                        Log.w(TAG, "NexFusion: Could not resolve Activity from $ctx / ${param.thisObject}")
                    }
                    if (param.method is java.lang.reflect.Method && (param.method as java.lang.reflect.Method).returnType == java.lang.Boolean.TYPE) {
                        param.result = true
                    } else {
                        param.result = null
                    }
                }
            }
        }

        val classesToHookClicks = listOfNotNull(headerFragmentClass, commonFragmentClass, prefFragmentCompatClass)
        for (clazz in classesToHookClicks) {
            runCatching {
                val methods = clazz.declaredMethods.filter {
                    (it.name == "aA" || it.name == "p" || it.name == "onPreferenceTreeClick" ||
                        (it.parameterTypes.size == 1 && it.parameterTypes[0].name.contains("Preference") && it.returnType == java.lang.Boolean.TYPE)) &&
                        it.parameterTypes.size == 1 &&
                        it.parameterTypes[0].name.contains("Preference")
                }
                for (m in methods) {
                    XposedBridge.hookMethod(m, clickHook)
                    Log.i(TAG, "NexFusion: Hooked click method ${clazz.name}.${m.name}")
                }
            }
        }

        Log.i(TAG, "NexFusion: GboardSettingsHook installed successfully on preference fragment hierarchy")
    }

    private fun injectAdvancedSettingsPreference(
        fragment: Any,
        context: Context,
        classLoader: ClassLoader,
        explicitGroup: Any? = null
    ) {
        try {
            val screen = explicitGroup ?: getPreferenceScreen(fragment) ?: return
            removeBloatPreferences(screen)

            // Only add the "Advanced settings" entry to the top-level settings header screen
            if (!fragment.javaClass.name.contains("PreferenceHeaderFragment")) {
                return
            }

            if (findPreference(screen, ADVANCED_SETTINGS_KEY) != null) {
                return
            }

            val prefClass = runCatching { classLoader.loadClass("androidx.preference.Preference") }.getOrNull() ?: return
            val pref = prefClass.getConstructor(Context::class.java).newInstance(context)

            setPreferenceKey(pref, ADVANCED_SETTINGS_KEY)
            setPreferenceTitle(pref, "Advanced settings")
            setPreferenceOrder(pref, 9999)

            // Dynamic sparkle icon (Material 3 purple/accent)
            val isDark = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
            val sparkleCol = if (isDark) 0xFFD0BCFF.toInt() else 0xFF65558F.toInt()
            val sparkleDrawable = GboardSettingsDialog.SparkleDrawable(sparkleCol)
            setPreferenceIcon(pref, sparkleDrawable)

            // Set OnPreferenceClickListener proxy (supports standard and obfuscated ebl)
            val listenerClassNames = listOf(
                "androidx.preference.Preference\$OnPreferenceClickListener",
                "ebl"
            )
            for (name in listenerClassNames) {
                val lClass = runCatching { classLoader.loadClass(name) }.getOrNull() ?: continue
                val proxy = Proxy.newProxyInstance(
                    classLoader,
                    arrayOf(lClass)
                ) { _, _, _ ->
                    val act = resolveActivity(context) ?: resolveActivity(resolveContext(fragment))
                    if (act != null) {
                        GboardSettingsDialog.show(act)
                    }
                    true
                }

                val method = pref.javaClass.methods.firstOrNull {
                    it.parameterTypes.size == 1 && it.parameterTypes[0].isAssignableFrom(lClass)
                }
                if (method != null) {
                    method.invoke(pref, proxy)
                    break
                }
                val field = pref.javaClass.declaredFields.firstOrNull {
                    it.type.isAssignableFrom(lClass)
                }
                if (field != null) {
                    field.isAccessible = true
                    field.set(pref, proxy)
                    break
                }
            }

            addPreferenceToScreen(screen, pref)
            Log.i(TAG, "NexFusion: Gboard 'Advanced settings' injected successfully into settings screen")
        } catch (t: Throwable) {
            Log.w(TAG, "NexFusion: Failed to inject Advanced settings preference", t)
        }
    }

    private fun removeBloatPreferences(screen: Any) {
        val count = getPreferenceCount(screen)
        for (i in count - 1 downTo 0) {
            val pref = getPreferenceAtIndex(screen, i) ?: continue
            val title = getPreferenceTitle(pref)
            val key = getPreferenceKey(pref)
            if (title.contains("Rate Gboard", ignoreCase = true) ||
                title.contains("Help & feedback", ignoreCase = true) ||
                title.contains("Help and feedback", ignoreCase = true) ||
                (title.contains("Feedback", ignoreCase = true) && !title.contains("haptic", ignoreCase = true)) ||
                key.contains("rate", ignoreCase = true) ||
                key.contains("feedback", ignoreCase = true)
            ) {
                removePreferenceFromScreen(screen, pref)
                Log.i(TAG, "NexFusion: Removed bloat preference: '$title' ($key)")
            }
        }
    }

    fun resolveContext(obj: Any?): Context? {
        if (obj == null) return null
        if (obj is Context) return obj
        if (obj is View) return obj.context

        runCatching { obj.javaClass.getMethod("getActivity").invoke(obj) as? Context }.getOrNull()?.let { return it }
        runCatching { obj.javaClass.getMethod("getContext").invoke(obj) as? Context }.getOrNull()?.let { return it }
        runCatching { (obj.javaClass.getMethod("getView").invoke(obj) as? View)?.context }.getOrNull()?.let { return it }
        runCatching { obj.javaClass.getMethod("w").invoke(obj) as? Context }.getOrNull()?.let { return it }
        runCatching { obj.javaClass.getMethod("x").invoke(obj) as? Context }.getOrNull()?.let { return it }

        var curr: Class<*>? = obj.javaClass
        while (curr != null && curr != Any::class.java) {
            for (f in curr.declaredFields) {
                if (Context::class.java.isAssignableFrom(f.type)) {
                    f.isAccessible = true
                    (f.get(obj) as? Context)?.let { return it }
                }
            }
            curr = curr.superclass
        }
        return null
    }

    fun resolveActivity(ctx: Context?): Activity? {
        var c = ctx
        while (c is ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        if (c is Activity) return c

        // Robust fallback: retrieve active activity from ActivityThread
        runCatching {
            val atClass = Class.forName("android.app.ActivityThread")
            val currentAt = atClass.getMethod("currentActivityThread").invoke(null)
            val mActivitiesField = atClass.getDeclaredField("mActivities").apply { isAccessible = true }
            val activities = mActivitiesField.get(currentAt) as? Map<*, *>
            if (activities != null) {
                for (record in activities.values) {
                    if (record == null) continue
                    val actField = record.javaClass.getDeclaredField("activity").apply { isAccessible = true }
                    val activity = actField.get(record) as? Activity
                    if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                        return activity
                    }
                }
            }
        }
        return null
    }

    private fun getPreferenceScreen(fragment: Any): Any? {
        val method = fragment.javaClass.methods.firstOrNull {
            (it.name == "getPreferenceScreen" || it.name == "n") &&
                    it.parameterTypes.isEmpty() &&
                    it.returnType.name.contains("Preference")
        }
        return method?.invoke(fragment)
    }

    private fun getPreferenceCount(screen: Any): Int {
        val method = screen.javaClass.methods.firstOrNull {
            (it.name == "getPreferenceCount" || it.name == "k") &&
                    it.parameterTypes.isEmpty() &&
                    it.returnType == Int::class.javaPrimitiveType
        }
        return (method?.invoke(screen) as? Int) ?: 0
    }

    private fun getPreferenceAtIndex(screen: Any, index: Int): Any? {
        val method = screen.javaClass.methods.firstOrNull {
            (it.name == "getPreference" || it.name == "o") &&
                    it.parameterTypes.size == 1 &&
                    it.parameterTypes[0] == Int::class.javaPrimitiveType
        }
        return method?.invoke(screen, index)
    }

    private fun findPreference(screen: Any, key: String): Any? {
        val method = screen.javaClass.methods.firstOrNull {
            (it.name == "findPreference" || it.name == "l") &&
                    it.parameterTypes.size == 1 &&
                    CharSequence::class.java.isAssignableFrom(it.parameterTypes[0])
        }
        return method?.invoke(screen, key)
    }

    private fun addPreferenceToScreen(screen: Any, pref: Any) {
        val method = screen.javaClass.methods.firstOrNull {
            (it.name == "addPreference" || it.name == "an") &&
                    it.parameterTypes.size == 1 &&
                    it.parameterTypes[0].name.contains("Preference")
        }
        method?.invoke(screen, pref)
    }

    private fun removePreferenceFromScreen(screen: Any, pref: Any) {
        val method = screen.javaClass.methods.firstOrNull {
            (it.name == "removePreference" || it.name == "ao") &&
                    it.parameterTypes.size == 1 &&
                    it.parameterTypes[0].name.contains("Preference")
        }
        method?.invoke(screen, pref)
    }

    private fun setPreferenceKey(pref: Any, key: String) {
        val m = pref.javaClass.methods.firstOrNull {
            (it.name == "setKey" || it.name == "P") &&
                it.parameterTypes.size == 1 && it.parameterTypes[0] == String::class.java
        }
        if (m != null) {
            val ok = runCatching { m.invoke(pref, key) }.isSuccess
            if (ok) return
        }
        for (f in pref.javaClass.declaredFields) {
            if (f.name == "r" || (f.type == String::class.java && !java.lang.reflect.Modifier.isStatic(f.modifiers))) {
                f.isAccessible = true
                f.set(pref, key)
                break
            }
        }
    }

    private fun setPreferenceTitle(pref: Any, title: CharSequence) {
        val m = pref.javaClass.methods.firstOrNull {
            (it.name == "setTitle" || it.name == "V" || it.name == "n") &&
                it.parameterTypes.size == 1 &&
                CharSequence::class.java.isAssignableFrom(it.parameterTypes[0])
        }
        if (m != null) {
            val ok = runCatching { m.invoke(pref, title) }.isSuccess
            if (ok) return
        }
        for (f in pref.javaClass.declaredFields) {
            if (f.name == "q" || (CharSequence::class.java.isAssignableFrom(f.type) && f.type != String::class.java)) {
                f.isAccessible = true
                f.set(pref, title)
                break
            }
        }
    }

    private fun setPreferenceOrder(pref: Any, order: Int) {
        val m = pref.javaClass.methods.firstOrNull {
            (it.name == "setOrder" || it.name == "S" || it.name == "Q" || it.name == "U") &&
                it.parameterTypes.size == 1 && it.parameterTypes[0] == Int::class.javaPrimitiveType
        }
        if (m != null) {
            val ok = runCatching { m.invoke(pref, order) }.isSuccess
            if (ok) return
        }
        runCatching {
            val f = pref.javaClass.getDeclaredField("p").apply { isAccessible = true }
            f.set(pref, order)
        }
    }

    private fun setPreferenceIcon(pref: Any, icon: android.graphics.drawable.Drawable) {
        val m = pref.javaClass.methods.firstOrNull {
            (it.name == "setIcon" || it.name == "N") &&
                it.parameterTypes.size == 1 &&
                android.graphics.drawable.Drawable::class.java.isAssignableFrom(it.parameterTypes[0])
        }
        if (m != null) {
            val ok = runCatching { m.invoke(pref, icon) }.isSuccess
            if (ok) return
        }
        runCatching {
            val f = pref.javaClass.getDeclaredField("c").apply { isAccessible = true }
            f.set(pref, icon)
        }
    }

    private fun getPreferenceKey(pref: Any): String {
        return runCatching { XposedHelpers.callMethod(pref, "getKey") as? String }.getOrNull()
            ?: runCatching {
                pref.javaClass.methods.firstOrNull {
                    (it.name == "getKey" || it.name == "m") && it.parameterTypes.isEmpty()
                }?.invoke(pref)?.toString()
            }.getOrNull()
            ?: runCatching {
                val f = pref.javaClass.getDeclaredField("r").apply { isAccessible = true }
                f.get(pref)?.toString()
            }.getOrNull() ?: ""
    }

    private fun getPreferenceTitle(pref: Any): String {
        return runCatching { XposedHelpers.callMethod(pref, "getTitle")?.toString() }.getOrNull()
            ?: runCatching {
                pref.javaClass.methods.firstOrNull {
                    (it.name == "getTitle" || it.name == "v") && it.parameterTypes.isEmpty()
                }?.invoke(pref)?.toString()
            }.getOrNull()
            ?: runCatching {
                val f = pref.javaClass.getDeclaredField("q").apply { isAccessible = true }
                f.get(pref)?.toString()
            }.getOrNull() ?: ""
    }
}
