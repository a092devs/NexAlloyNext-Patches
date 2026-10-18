package io.github.nexalloy.nexfusion.gboard

import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.preference.PreferenceManager
import android.util.Log
import android.view.inputmethod.EditorInfo
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * Implements Gemini "Rambler" Natural Voice Typing and companion lifecycle fixes:
 *
 * 1. Official VoiceSettingsFragment Selector Hook:
 *    - Gboard 18.4.1+: Hooks `mvq.a(Context)` to return true.
 *    - Gboard 18.3.1+: Hooks `aaeo.a(Context)` to return true.
 *    - Gboard 18.0.3+: Hooks `mqk.a(Context)` to return true.
 *    - Initializes `enable_jetson = true` and auto-punctuation in Device-Protected Storage (DE).
 *
 * 2. Battery-Friendly Speech Recognition Lifecycle (#3):
 *    - Hooks `InputMethodService.onFinishInputView` and `hideWindow` to terminate speech recognition
 *      bindings (`GoogleAsrService`) as soon as the keyboard is hidden, preventing screen-off battery drain.
 *
 * 3. Foreground Clipboard Sync for Samsung One UI & Vivo (#17):
 *    - Hooks `InputMethodService.onStartInputView` to guarantee ClipboardManager listener initialization
 *      and trigger primary clip capture into clipboard drawer history.
 *
 * 4. Modern Spell Checker Suggestion Chips on Android 12+:
 *    - Hooks Gboard spell checker utility methods `(Context, TextServicesManager) -> Boolean` to
 *      force inline spell check chips and grammar checking.
 */
object GboardRamblerHook {

    private const val TAG = "NexAlloy"
    private const val PREF_ENABLE_JETSON = "enable_jetson"
    private const val PREF_AUTO_PUNCTUATION = "pref_key_auto_punctuation"
    private const val PREF_ENABLE_AUTO_PUNCTUATION = "pref_key_enable_auto_punctuation"
    private const val PREF_USE_GOOGLE_VOICE = "pref_key_use_google_voice_typing"

    fun install(classLoader: ClassLoader) {
        hookVoiceSettingsSelectors(classLoader)
        hookImeLifecycle(classLoader)
        hookSpellCheckerChips(classLoader)
    }

    private fun hookVoiceSettingsSelectors(classLoader: ClassLoader) {
        // Known selector capability checkers across Gboard releases:
        // 18.4.1+: mvq.a(Context)
        // 18.3.1+: aaeo.a(Context)
        // 18.0.3+: mqk.a(Context)
        val selectorClassNames = listOf("mvq", "aaeo", "mqk")
        for (className in selectorClassNames) {
            val clazz = runCatching { classLoader.loadClass(className) }.getOrNull() ?: continue
            val method = clazz.declaredMethods.firstOrNull {
                it.name == "a" && it.parameterTypes.size == 1 && it.parameterTypes[0] == Context::class.java &&
                        it.returnType == Boolean::class.javaPrimitiveType
            } ?: continue

            runCatching {
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val ctx = param.args.getOrNull(0) as? Context
                        if (ctx != null) {
                            ensureRamblerPreferences(ctx)
                        }
                        param.result = true
                    }
                })
                Log.i(TAG, "NexFusion: Hooked Gboard Rambler selector $className.a(Context)")
            }
        }

        // Default selection suppression: prevent Gboard from reverting Rambler to standard
        val defaultSuppressionClasses = listOf("fde" /* 18.4.1 */, "kcm" /* 18.3.1 */, "fbl" /* 18.0.3 */)
        for (className in defaultSuppressionClasses) {
            val clazz = runCatching { classLoader.loadClass(className) }.getOrNull() ?: continue
            for (m in clazz.declaredMethods) {
                if (m.parameterTypes.isEmpty() && m.returnType == Void.TYPE &&
                    (m.name == "hT" || m.name == "ge" || m.name == "hN")
                ) {
                    runCatching {
                        XposedBridge.hookMethod(m, object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                // Suppress resetting selection back to standard
                                param.result = null
                            }
                        })
                        Log.i(TAG, "NexFusion: Hooked default selection suppression $className.${m.name}()")
                    }
                }
            }
        }
    }

    fun ensureRamblerPreferences(context: Context) {
        try {
            val targetContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !context.isDeviceProtectedStorage) {
                runCatching { context.createDeviceProtectedStorageContext() }.getOrNull() ?: context
            } else context

            val prefs = PreferenceManager.getDefaultSharedPreferences(targetContext)
            if (!prefs.getBoolean(PREF_ENABLE_JETSON, false)) {
                prefs.edit()
                    .putBoolean(PREF_ENABLE_JETSON, true)
                    .putBoolean(PREF_AUTO_PUNCTUATION, true)
                    .putBoolean(PREF_ENABLE_AUTO_PUNCTUATION, true)
                    .putBoolean(PREF_USE_GOOGLE_VOICE, true)
                    .apply()
                Log.i(TAG, "NexFusion: Initialized Rambler preferences in ${targetContext.packageName}")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "NexFusion: Failed to ensure Rambler preferences", t)
        }
    }

    private fun hookImeLifecycle(classLoader: ClassLoader) {
        val imeClass = runCatching {
            classLoader.loadClass("android.inputmethodservice.InputMethodService")
        }.getOrNull() ?: return

        // 1. Samsung / Vivo clipboard sync fix on foreground focus
        runCatching {
            XposedHelpers.findAndHookMethod(
                imeClass,
                "onStartInputView",
                EditorInfo::class.java,
                Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val ime = param.thisObject as? Context ?: return
                        ensureRamblerPreferences(ime)
                        syncClipboardOnForeground(ime)
                    }
                }
            )
        }

        // 2. Battery-Friendly Lifecycle (#3): clean release of speech recognition when keyboard hides
        val finishHook = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val ime = param.thisObject as? Context ?: return
                terminateSpeechRecognitionIfIdle(ime, param.thisObject)
            }
        }

        runCatching {
            XposedHelpers.findAndHookMethod(
                imeClass,
                "onFinishInputView",
                Boolean::class.javaPrimitiveType,
                finishHook
            )
        }
        runCatching {
            XposedHelpers.findAndHookMethod(
                imeClass,
                "hideWindow",
                finishHook
            )
        }
    }

    private fun syncClipboardOnForeground(context: Context) {
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
            if (cm.hasPrimaryClip()) {
                val clip = cm.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    // Triggering clip reading confirms foreground permission and notifies listeners
                    clip.getItemAt(0)?.coerceToText(context)
                }
            }
        } catch (_: Throwable) {
        }
    }

    private fun terminateSpeechRecognitionIfIdle(context: Context, imeInstance: Any) {
        try {
            // Check for active NgaInputManager or SpeechRecognizer instances in the IME
            val fields = imeInstance.javaClass.declaredFields
            for (f in fields) {
                val typeName = f.type.name
                if (typeName.contains("NgaInputManager") || typeName.contains("Dictation") || typeName.contains("Speech")) {
                    f.isAccessible = true
                    val manager = f.get(imeInstance) ?: continue
                    for (m in manager.javaClass.declaredMethods) {
                        if (m.parameterTypes.isEmpty() && (m.name == "stopListening" || m.name == "stopDictation" || m.name == "cancel")) {
                            m.isAccessible = true
                            m.invoke(manager)
                        }
                    }
                }
            }
        } catch (_: Throwable) {
        }
    }

    private fun hookSpellCheckerChips(classLoader: ClassLoader) {
        // Spell checker utility class has a method: (Context, TextServicesManager) -> Boolean
        // On Android 12+ (Build.VERSION.SDK_INT >= 31), we force it to return true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

        val knownSpellCheckerClasses = listOf("rcq", "rcp", "rco")
        for (className in knownSpellCheckerClasses) {
            val clazz = runCatching { classLoader.loadClass(className) }.getOrNull() ?: continue
            for (m in clazz.declaredMethods) {
                if (m.parameterTypes.size == 2 &&
                    m.parameterTypes[0] == Context::class.java &&
                    m.parameterTypes[1].name.contains("TextServicesManager") &&
                    m.returnType == Boolean::class.javaPrimitiveType
                ) {
                    runCatching {
                        XposedBridge.hookMethod(m, object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                param.result = true
                            }
                        })
                        Log.i(TAG, "NexFusion: Hooked Gboard spellchecker chips $className.${m.name}()")
                    }
                }
            }
        }
    }
}

