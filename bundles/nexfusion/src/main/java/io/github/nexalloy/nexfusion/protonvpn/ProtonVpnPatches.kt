package io.github.nexalloy.nexfusion.protonvpn

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import io.github.nexalloy.PatcherLog
import io.github.nexalloy.patch

// Ported from hxreborn/morphe-patches (GPL-3.0), hoo-dles/morphe-patches (GPL-3.0),
// and Paresh-Maheshwari/paresh-patches (GPL-3.0):
// - Remove server change delay on free plans (AppConfigResponse)
// - Unlock advanced client settings: NetShield ad/malware blocking, split tunneling,
//   custom DNS, and LAN connections (LocalUserSettings & VpnUser)
// - Hide upgrade promotions: onboarding dialogs, upsell banners, and plan upgrade cards
// - Disable telemetry, analytics, and observability data collection

val ProtonVpnDisableTelemetry = patch(
    name = "Disable telemetry",
    description = "Blocks telemetry, analytics, and observability data collection in Proton VPN.",
) {
    // Block Proton Core telemetry worker scheduling
    runCatching {
        val workerClass = classLoader.loadClass("me.proton.core.telemetry.data.worker.TelemetryWorkerManagerImpl")
        workerClass.declaredMethods.filter { it.name.startsWith("enqueueOrKeep") }.forEach { method ->
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.result = null
                }
            })
        }
    }

    // Block observability events sending
    runCatching {
        val obsClass = classLoader.loadClass("me.proton.core.observability.data.usecase.SendObservabilityEventsImpl")
        obsClass.declaredMethods.filter { it.name == "invoke" }.forEach { method ->
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.result = Unit
                }
            })
        }
    }

    // Block VPN telemetry event recording
    runCatching {
        val vpnTelemetryClass = classLoader.loadClass("com.protonvpn.android.telemetry.VpnTelemetry")
        vpnTelemetryClass.declaredMethods.filter { it.name == "addEvent" }.forEach { method ->
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.result = Unit
                }
            })
        }
    }

    // Block telemetry background workers and heartbeat in 5.20+
    runCatching {
        val workers = listOf(
            "com.protonvpn.android.telemetry.TelemetryUploadWorker",
            "com.protonvpn.android.telemetry.SettingsHeartbeatWorker",
            "me.proton.core.telemetry.data.worker.TelemetryWorker",
        )
        for (workerName in workers) {
            val workerCls = runCatching { classLoader.loadClass(workerName) }.getOrNull() ?: continue
            workerCls.declaredMethods.filter { it.name == "doWork" }.forEach { method ->
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        // Return Result.success() to prevent worker execution and rescheduling
                        val successMethod = runCatching {
                            classLoader.loadClass("androidx.work.ListenableWorker\$Result").getMethod("success")
                        }.getOrNull()
                        if (successMethod != null) {
                            param.result = successMethod.invoke(null)
                        }
                    }
                })
            }
        }
    }
}

val ProtonVpnServerChangeDelay = patch(
    name = "Remove server change delay",
    description = "Removes the wait period between server changes on free plans.",
) {
    runCatching {
        val configClass = classLoader.loadClass("com.protonvpn.android.appconfig.AppConfigResponse")
        configClass.declaredMethods.filter {
            it.name == "getChangeServerLongDelayInSeconds" || it.name == "getChangeServerShortDelayInSeconds"
        }.forEach { method ->
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.result = 0
                }
            })
        }
    }
}

val ProtonVpnUnlockSettings = patch(
    name = "Unlock advanced settings & NetShield",
    description = "Unlocks NetShield ad/tracker blocking, split tunneling, custom DNS, and LAN connections.",
) {
    // NetShield availability -> AVAILABLE
    runCatching {
        val netShieldEnumClass = classLoader.loadClass("com.protonvpn.android.netshield.NetShieldAvailability")
        val availableConstant = netShieldEnumClass.enumConstants?.firstOrNull { (it as Enum<*>).name == "AVAILABLE" }
        if (availableConstant != null) {
            val netShieldManagerClass = classLoader.loadClass("com.protonvpn.android.netshield.NetShieldManager")
            netShieldManagerClass.declaredMethods.filter { it.returnType == netShieldEnumClass }.forEach { method ->
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = availableConstant
                    }
                })
            }
        }
    }

    // Hook VpnUser tier & free status to unlock client-side settings
    runCatching {
        val userClass = classLoader.loadClass("com.protonvpn.android.auth.data.VpnUser")
        XposedHelpers.findAndHookMethod(userClass, "getUserTier", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                param.result = 2
            }
        })
        XposedHelpers.findAndHookMethod(userClass, "isUserPlusOrAbove", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                param.result = true
            }
        })
        XposedHelpers.findAndHookMethod(userClass, "isFreeUser", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                param.result = false
            }
        })
        XposedHelpers.findAndHookMethod(userClass, "getUserTierName", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                param.result = "vpn2022"
            }
        })
        runCatching {
            XposedHelpers.findAndHookMethod(userClass, "getMaxTier", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.result = java.lang.Integer.valueOf(2)
                }
            })
        }
    }
}

val ProtonVpnHidePromotions = patch(
    name = "Hide upgrade promotions",
    description = "Hides upgrade dialogs, onboarding prompts, and account upgrade banners.",
) {
    // Skip upgrade onboarding dialog after login
    runCatching {
        val launcherClass = classLoader.loadClass("com.protonvpn.android.ui.planupgrade.UpgradeDialogLauncherVM")
        launcherClass.declaredMethods.filter { it.name.startsWith("launch") }.forEach { method ->
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.result = null
                }
            })
        }
    }

    // Suppress upgrade banners in AccountSettingsViewState
    runCatching {
        val accountStateClass = classLoader.loadClass("com.protonvpn.android.settings.AccountSettingsViewState")
        accountStateClass.declaredMethods.filter { it.name == "getUpgradeToPlusBanner" && it.returnType == java.lang.Boolean.TYPE }.forEach { method ->
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.result = false
                }
            })
        }
    }
}

val ProtonVpnPremiumUi = patch(
    name = "Plus UI badge",
    description = "Displays Plus plan UI indicators.",
) {
    // Ensure Plus tier presentation in client headers
    runCatching {
        val userClass = classLoader.loadClass("com.protonvpn.android.auth.data.VpnUser")
        XposedHelpers.findAndHookMethod(userClass, "getUserTierName", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                param.result = "vpn2022"
            }
        })
    }
}

val ProtonVpnPatches = arrayOf(
    ProtonVpnDisableTelemetry,
    ProtonVpnServerChangeDelay,
    ProtonVpnUnlockSettings,
    ProtonVpnHidePromotions,
    ProtonVpnPremiumUi,
)
