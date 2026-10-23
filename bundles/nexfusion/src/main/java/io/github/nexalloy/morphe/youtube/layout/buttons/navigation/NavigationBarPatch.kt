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

    // TODO Navigation search and settings button
    // TODO useTranslucentNavigation
    // TODO Disable the A/B layout with Subscriptions as a tab of Home.
    // TODO upper navigation toolbar
}
