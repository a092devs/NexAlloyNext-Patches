package io.github.nexalloy.morphe.youtube.layout.buttons.navigation

import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import app.morphe.extension.youtube.patches.NavigationBarPatch
import io.github.nexalloy.morphe.setExtensionIsPatchIncluded
import io.github.nexalloy.morphe.shared.misc.settings.preference.PreferenceScreenPreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.PreferenceScreenPreference.Sorting
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.morphe.youtube.insertLiteralOverride
import io.github.nexalloy.morphe.youtube.misc.contexthook.Endpoint
import io.github.nexalloy.morphe.youtube.misc.contexthook.addOSNameHook
import io.github.nexalloy.morphe.youtube.misc.contexthook.clientContextHookPatch
import io.github.nexalloy.morphe.youtube.misc.navigation.NavigationBarHook
import io.github.nexalloy.morphe.youtube.misc.navigation.hookNavigationButtonCreated
import io.github.nexalloy.morphe.youtube.misc.playservice.VersionCheck
import io.github.nexalloy.morphe.youtube.misc.playservice.is_20_31_or_greater
import io.github.nexalloy.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexalloy.patch
import io.github.nexalloy.scopedHook
import org.luckypray.dexkit.wrap.DexMethod

val NavigationBar = patch(
    name = "Navigation bar",
    description = "Adds options to hide and change the bottom navigation bar (such as the Shorts button) "
            + "and the upper navigation toolbar.",
) {
    dependsOn(
        NavigationBarHook,
        VersionCheck,
        clientContextHookPatch,
    )

    val navPreferences = mutableSetOf(
        SwitchPreference("morphe_hide_home_button"),
        SwitchPreference("morphe_hide_shorts_button"),
        SwitchPreference("morphe_hide_create_button"),
        SwitchPreference("morphe_hide_subscriptions_button"),
        SwitchPreference("morphe_hide_notifications_button"),
//        SwitchPreference("morphe_show_search_button"),
//        ListPreference("morphe_show_search_button_index"),
//        SwitchPreference("morphe_show_settings_button"),
//        ListPreference("morphe_show_settings_button_index"),
//        SwitchPreference("morphe_show_settings_button_type", summary = true),
        SwitchPreference("morphe_swap_create_with_notifications_button", summary = true),
//        SwitchPreference("morphe_hide_navigation_bar"),
//        SwitchPreference("morphe_narrow_navigation_buttons", summary = true),
        SwitchPreference("morphe_hide_navigation_button_labels"),
        SwitchPreference("morphe_hide_navigation_new_content_dot"),
        SwitchPreference("morphe_navigation_bar_animations", summary = true),
//        SwitchPreference("morphe_disable_translucent_navigation", summary = true)
    )

    if (is_20_31_or_greater) {
        navPreferences += SwitchPreference("morphe_disable_auto_hide_navigation_bar", summary = true)
    }

    PreferenceScreen.GENERAL.addPreferences(
        PreferenceScreenPreference(
            key = "morphe_navigation_buttons_screen",
            sorting = Sorting.UNSORTED,
            preferences = navPreferences
        )
    )

    // Swap create with notifications button.
    addOSNameHook(
        Endpoint.GUIDE,
        NavigationBarPatch::swapCreateWithNotificationButton
    )
    setExtensionIsPatchIncluded(NavigationBarPatch::class.java)

    // Hide navigation button labels.
    CreatePivotBarFingerprint.hookMethod(scopedHook(DexMethod("Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V").toMethod()) {
        before { param ->
            NavigationBarPatch.hideNavigationButtonLabels(param.thisObject as TextView)
        }
    })

    // Hook navigation button created, in order to hide them.
    hookNavigationButtonCreated.add { button, view ->
        NavigationBarPatch.navigationTabCreated(button, view)
    }

    // TODO Hide navigation bar

    // TODO Paint over the translucent status bar and navigation bar

    // Animated navigation tabs.
    insertLiteralOverride(45680008L, NavigationBarPatch::useAnimatedNavigationButtons)

    // Translucent navigation bar (fixes raised navigation bar / bottom empty space)
    insertLiteralOverride(45630927L, NavigationBarPatch::useTranslucentNavigationBar)
    insertLiteralOverride(45632194L, NavigationBarPatch::useTranslucentNavigationBar)
    insertLiteralOverride(45631257L, NavigationBarPatch::useTranslucentNavigationBar)

    // Frosted-glass blur draw crash on Android 12/13 (port of upstream a6db8d7ab): the blurred
    // background copies the watch layout onto the bar, so the player surface is drawn twice per
    // frame and both positions are pushed to the surface from two threads at once while the
    // player is dragged down to the miniplayer. Upstream forces the bar canvas to look not
    // hardware accelerated on those versions, so the copy draw is skipped while the bar color
    // is still painted. Scoped to the blur draw method, only installed on affected versions.
    if (Build.VERSION.SDK_INT < 34) {
        NavigationBarBlurDrawFingerprint.dexMethod.hookMethod(
            scopedHook(
                DexMethod("Landroid/view/View;->draw(Landroid/graphics/Canvas;)V").toMethod()
            ) {
                before {
                    it.result = null
                }
            }
        )
    }

    // TODO Narrow navigation buttons

    // Fix navigation bar growing and covering the page when the keyboard is open
    runCatching {
        val pivotBarClass = classLoader.loadClass("com.google.android.libraries.youtube.rendering.ui.pivotbar.PivotBar")
        val setPaddingMethod = pivotBarClass.methods.firstOrNull {
            it.name == "setPadding" && it.parameterTypes.contentEquals(
                arrayOf(
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                )
            )
        }
        if (setPaddingMethod != null) {
            XposedBridge.hookMethod(setPaddingMethod, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val bar = param.thisObject as? View ?: return
                    val bottom = param.args[3] as? Int ?: return
                    if (bottom != 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val insets = bar.rootWindowInsets
                        if (insets != null && insets.isVisible(WindowInsets.Type.ime())) {
                            param.args[3] = Math.min(bottom, insets.getInsets(WindowInsets.Type.systemBars()).bottom)
                        }
                    }
                }
            })
        }
    }

    // disableAutoHidingNavigationBar

    if (is_20_31_or_greater) {
        listOf(
            AutoHideNavigationBarOnFeedScrollingFingerprint,
            AutoHideNavigationBarOnDismissMiniplayerFingerprint,
        ).forEach {
            it.hookMethod {
                before { param ->
                    if (NavigationBarPatch.disableAutoHidingNavigationBar()) {
                        param.result = null
                    }
                }
            }
        }
    }

    // Restore offline subscriptions button
    insertLiteralOverride(45780222L, false)

    // Show only the selected tab of Home feed (removes Home/Subs/Music/Live/More tab bar and restores category chips)
    BrowseResponseTabsFingerprint.hookMethod {
        before { param ->
            val model = param.thisObject ?: return@before
            try {
                val bghkField = model.javaClass.declaredFields.firstOrNull { it.name == "a" } ?: return@before
                bghkField.isAccessible = true
                val bghk = bghkField.get(model) ?: return@before
                val singleColField = bghk.javaClass.declaredFields.firstOrNull { it.name == "i" } ?: return@before
                singleColField.isAccessible = true
                val bghm = singleColField.get(bghk) ?: return@before
                val tabsObjField = bghm.javaClass.declaredFields.firstOrNull { it.name == "c" } ?: return@before
                tabsObjField.isAccessible = true
                val bghw = tabsObjField.get(bghm) ?: return@before
                val listField = bghw.javaClass.declaredFields.firstOrNull { List::class.java.isAssignableFrom(it.type) } ?: return@before
                listField.isAccessible = true
                val rawList = listField.get(bghw) as? List<Any> ?: return@before
                if (rawList.size > 1) {
                    val filtered = app.morphe.extension.youtube.patches.ClassicSubscriptionsLayoutPatch.filterBrowseTabs(rawList as List<Any>)
                    if (filtered.size != rawList.size) {
                        listField.set(bghw, filtered)
                        val cachedTabsField = model.javaClass.declaredFields.firstOrNull { it.name == "e" }
                        cachedTabsField?.isAccessible = true
                        cachedTabsField?.set(model, null)
                    }
                }
            } catch (_: Throwable) {}
        }
    }

    // Add back the Subscriptions button in the bottom navigation bar if server removed it in A/B test
    runCatching {
        val protoFactoryMethod = runCatching { PivotBarItemProtoFactoryFingerprint.method }.getOrNull()
        val rendererMethod = runCatching { PivotBarRendererFingerprint.method }.getOrNull()
        XposedBridge.log("NexAlloyNav: protoFactory=" + protoFactoryMethod + ", renderer=" + rendererMethod)

        ::pivotBarRendererListMethod.hookMethod {
            after { param ->
                val list = param.result as? List<Any> ?: return@after
                if (list.isEmpty()) return@after

                fun getPivotIdentifier(item: Any): String? {
                    for (f in item.javaClass.declaredFields) {
                        f.isAccessible = true
                        val proto = f.get(item) ?: continue
                        val toByteArray = proto.javaClass.methods.firstOrNull {
                            it.name == "toByteArray" && it.parameterTypes.isEmpty()
                        } ?: continue
                        val bytes = toByteArray.invoke(proto) as? ByteArray ?: continue
                        var i = 0
                        while (i < bytes.size) {
                            val tag = bytes[i].toInt() and 0xFF
                            if (tag == 0x0A) {
                                var len = 0
                                var shift = 0
                                i++
                                while (i < bytes.size) {
                                    val b = bytes[i].toInt() and 0xFF
                                    i++
                                    len = len or ((b and 0x7F) shl shift)
                                    if ((b and 0x80) == 0) break
                                    shift += 7
                                }
                                if (i + len <= bytes.size) {
                                    return String(bytes, i, len, Charsets.UTF_8)
                                }
                                break
                            }
                            break
                        }
                    }
                    return null
                }

                fun getHomeIconNumber(item: Any): Int {
                    for (f in item.javaClass.declaredFields) {
                        f.isAccessible = true
                        val proto = f.get(item) ?: continue
                        val toByteArray = proto.javaClass.methods.firstOrNull {
                            it.name == "toByteArray" && it.parameterTypes.isEmpty()
                        } ?: continue
                        val bytes = toByteArray.invoke(proto) as? ByteArray ?: continue
                        for (j in 0 until bytes.size - 1) {
                            if (bytes[j] == 0x82.toByte() && bytes[j + 1] == 0x09.toByte()) {
                                return 1155 // TAB_SUBSCRIPTIONS_CAIRO
                            }
                        }
                    }
                    return 1155
                }

                var hasSubscriptions = false
                var homeItem: Any? = null
                for (item in list) {
                    val id = getPivotIdentifier(item)
                    XposedBridge.log("NexAlloyNav: item id=" + id)
                    if (id == "FEsubscriptions") {
                        hasSubscriptions = true
                        break
                    }
                    if (id == "FEwhat_to_watch") {
                        homeItem = item
                    }
                }

                if (hasSubscriptions || homeItem == null || protoFactoryMethod == null || rendererMethod == null) {
                    XposedBridge.log("NexAlloyNav: skip injection. hasSubs=" + hasSubscriptions + ", homeFound=" + (homeItem != null))
                    return@after
                }

                XposedBridge.log("NexAlloyNav: FEsubscriptions missing, injecting...")
                val subsIconTypeNumber = getHomeIconNumber(homeItem)

                val iconTypeClass = protoFactoryMethod.parameterTypes[2]
                val iconLookupMethod = iconTypeClass.methods.firstOrNull {
                    java.lang.reflect.Modifier.isStatic(it.modifiers) &&
                    it.returnType == iconTypeClass &&
                    it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType))
                } ?: iconTypeClass.methods.firstOrNull {
                    java.lang.reflect.Modifier.isStatic(it.modifiers) &&
                    it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType))
                } ?: run {
                    XposedBridge.log("NexAlloyNav: iconLookupMethod not found")
                    return@after
                }
                val iconEnum = iconLookupMethod.invoke(null, subsIconTypeNumber) ?: run {
                    XposedBridge.log("NexAlloyNav: iconEnum lookup failed for " + subsIconTypeNumber)
                    return@after
                }

                val label = runCatching {
                    app.morphe.extension.youtube.patches.ClassicSubscriptionsLayoutPatch.getSubscriptionsLabel()
                }.getOrNull()?.takeIf { it.isNotEmpty() } ?: "Subscriptions"

                val itemProto = protoFactoryMethod.invoke(null, "FEsubscriptions", label, iconEnum, false) ?: run {
                    XposedBridge.log("NexAlloyNav: protoFactoryMethod failed")
                    return@after
                }

                val opt = rendererMethod.invoke(null, itemProto)
                val avaaItem = when {
                    opt == null -> null
                    opt.javaClass.name.contains("Optional") -> {
                        val isPresent = opt.javaClass.getMethod("isPresent").invoke(opt) as? Boolean ?: false
                        if (isPresent) opt.javaClass.getMethod("get").invoke(opt) else null
                    }
                    else -> opt
                } ?: run {
                    XposedBridge.log("NexAlloyNav: rendererMethod did not produce avaa item")
                    return@after
                }

                val newList = ArrayList<Any>(list)
                var insertIndex = (newList.size - 1).coerceAtLeast(0)
                for (i in newList.indices) {
                    val id = getPivotIdentifier(newList[i])
                    if (id == "FEshorts") {
                        insertIndex = i + 1
                    } else if (id == "FElibrary" || id == "FEactivity" || id == "FEhistory" || id == "FEuser") {
                        insertIndex = i
                        break
                    }
                }
                newList.add(insertIndex, avaaItem)

                val listClass: Class<*> = list.javaClass
                val candidateClasses = listOfNotNull(listClass, listClass.superclass, listClass.superclass?.superclass)
                var copyOfMethod: java.lang.reflect.Method? = null
                for (cls in candidateClasses) {
                    copyOfMethod = cls.methods.firstOrNull {
                        java.lang.reflect.Modifier.isStatic(it.modifiers) &&
                        it.returnType.isAssignableFrom(cls) &&
                        it.parameterTypes.contentEquals(arrayOf(java.util.Collection::class.java))
                    }
                    if (copyOfMethod != null) break
                }

                val finalResult = copyOfMethod?.invoke(null, newList) ?: newList
                param.result = finalResult
                XposedBridge.log("NexAlloyNav: Subscriptions button successfully injected at index " + insertIndex + "! New count = " + newList.size)
            }
        }
    }.onFailure {
        XposedBridge.log("NexAlloyNav: initialization failed: " + it)
    }
}
