package io.github.nexalloy.nexfusion.spotify.misc

import android.content.SharedPreferences
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import io.github.nexalloy.PatcherLog
import io.github.nexalloy.hookMethod
import io.github.nexalloy.patch
import io.github.nexalloy.nexfusion.spotify.buildQueryParametersMethod
import io.github.nexalloy.nexfusion.spotify.contextFromJsonMethod
import io.github.nexalloy.nexfusion.spotify.productStateProtoMethod
import java.lang.reflect.Field
import java.lang.reflect.Method

private object SpotifyAuthCache {
    @Volatile var body: String? = null
    @Volatile var contentType: Any? = null
}

private val PREMIUM_OVERRIDES = listOf(
    "ads" to java.lang.Boolean.FALSE,
    "player-license" to "premium",
    "player-license-v2" to "premium",
    "shuffle" to java.lang.Boolean.FALSE,
    "on-demand" to java.lang.Boolean.TRUE,
    "streaming" to java.lang.Boolean.TRUE,
    "pick-and-shuffle" to java.lang.Boolean.FALSE,
    "streaming-rules" to "",
    "nft-disabled" to "1",
    "type" to "premium",
)

val UnlockPremium = patch(
    name = "Unlock Spotify Premium",
    description = "Unlocks on-demand playback, playlist track selection, unforced shuffle, and unlimited skips with a 4-layer anti-logout safety shield.",
) {
    val cl = appContext.classLoader ?: return@patch

    // 1. Core on-demand and unforced shuffle playback attribute overrides
    val productStateMethod = ::productStateProtoMethod.memberOrNull
    if (productStateMethod != null) {
        productStateMethod.hookMethod {
            after { param ->
                val result = param.result as? Map<String, *> ?: return@after
                for ((key, overrideValue) in PREMIUM_OVERRIDES) {
                    val attribute = result[key] ?: continue
                    runCatching {
                        val originalValue = XposedHelpers.getObjectField(attribute, "value_")
                        if (originalValue != overrideValue) {
                            XposedHelpers.setObjectField(attribute, "value_", overrideValue)
                        }
                    }
                }
            }
        }
    } else {
        PatcherLog.info { "ProductStateProto method not found; skipping attribute overrides." }
    }

    // 2. Artist page popular tracks query parameter check
    val queryParamsMethod = ::buildQueryParametersMethod.memberOrNull
    if (queryParamsMethod != null) {
        queryParamsMethod.hookMethod {
            after { param ->
                val result = param.result ?: return@after
                if (result.toString().contains("checkDeviceCapability=")) {
                    runCatching {
                        param.result = XposedBridge.invokeOriginalMethod(
                            param.method, param.thisObject, arrayOf(param.args[0], true)
                        )
                    }
                }
            }
        }
    }

    // 3. Google Assistant & Voice Assistant URI un-shuffling
    val assistantMethod = ::contextFromJsonMethod.memberOrNull
    if (assistantMethod != null) {
        assistantMethod.hookMethod {
            fun safeCleanStationUri(field: Field?, obj: Any?) {
                if (field == null || obj == null) return
                runCatching {
                    val value = field.get(obj) as? String ?: return
                    if (value.contains("spotify:station:")) {
                        field.set(obj, value.replace("spotify:station:", "spotify:"))
                    }
                }
            }

            after { param ->
                val result = param.result ?: return@after
                val clazz = result.javaClass
                safeCleanStationUri(runCatching { clazz.getDeclaredField("uri").apply { isAccessible = true } }.getOrNull(), result)
                safeCleanStationUri(runCatching { clazz.getDeclaredField("url").apply { isAccessible = true } }.getOrNull(), result)
            }
        }
    }

    // 4. Disable forced shuffle when requesting an album or playlist
    runCatching {
        XposedHelpers.findAndHookMethod(
            "com.spotify.player.model.command.options.AutoValue_PlayerOptionOverrides\$Builder",
            cl,
            "build",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    runCatching {
                        XposedHelpers.callMethod(param.thisObject, "shufflingContext", false)
                    }
                }
            }
        )
    }

    // 5. Anti-logout Shield: OkHttp Detection Blocker & Token Cache/Replay
    runCatching {
        installOkHttpShield(cl)
    }.onFailure {
        PatcherLog.error(it) { "Failed to install OkHttp anti-logout shield" }
    }

    // 6. Anti-logout Shield: SharedPreferences Session Credential Lock
    runCatching {
        installSharedPreferencesShield(cl)
    }.onFailure {
        PatcherLog.error(it) { "Failed to install SharedPreferences shield" }
    }
}

private fun findMethodSafe(clazz: Class<*>, name: String, vararg params: Class<*>): Method? {
    return runCatching { clazz.getDeclaredMethod(name, *params).apply { isAccessible = true } }.getOrNull()
        ?: runCatching { clazz.methods.firstOrNull { it.name == name && it.parameterTypes.size == params.size }?.apply { isAccessible = true } }.getOrNull()
}

private fun createResponseBody(bodyClass: Class<*>, mtClass: Class<*>, contentType: Any?, bodyString: String): Any? {
    // 1. Static ResponseBody.create(MediaType, String)
    runCatching {
        val m = bodyClass.getDeclaredMethod("create", mtClass, String::class.java)
        m.isAccessible = true
        return m.invoke(null, contentType, bodyString)
    }
    // 2. Static ResponseBody.create(String, MediaType)
    runCatching {
        val m = bodyClass.getDeclaredMethod("create", String::class.java, mtClass)
        m.isAccessible = true
        return m.invoke(null, bodyString, contentType)
    }
    // 3. ResponseBody.Companion.create(...)
    runCatching {
        val companionField = bodyClass.getDeclaredField("Companion")
        companionField.isAccessible = true
        val companion = companionField.get(null)
        val m = companion.javaClass.methods.firstOrNull {
            it.name == "create" && it.parameterTypes.size == 2
        }
        if (m != null) {
            m.isAccessible = true
            return if (m.parameterTypes[0] == String::class.java) {
                m.invoke(companion, bodyString, contentType)
            } else {
                m.invoke(companion, contentType, bodyString)
            }
        }
    }
    return null
}

private fun createEmpty204Response(
    builderClass: Class<*>,
    bodyClass: Class<*>,
    mtClass: Class<*>,
    protocolClass: Class<*>,
    req: Any,
    cl: ClassLoader
): Any? {
    return runCatching {
        val builder = builderClass.getConstructor().newInstance()
        val http11 = protocolClass.getField("HTTP_1_1").get(null)
        val reqClass = req.javaClass

        findMethodSafe(builderClass, "request", reqClass)?.invoke(builder, req)
            ?: runCatching {
                val okHttpReq = Class.forName("okhttp3.Request", false, cl)
                findMethodSafe(builderClass, "request", okHttpReq)?.invoke(builder, req)
            }.getOrNull()

        findMethodSafe(builderClass, "protocol", protocolClass)?.invoke(builder, http11)
        findMethodSafe(builderClass, "code", java.lang.Integer.TYPE)?.invoke(builder, 204)
        findMethodSafe(builderClass, "message", String::class.java)?.invoke(builder, "Blocked")

        val emptyBody = createResponseBody(bodyClass, mtClass, null, "")
        if (emptyBody != null) {
            findMethodSafe(builderClass, "body", bodyClass)?.invoke(builder, emptyBody)
        }
        findMethodSafe(builderClass, "build")?.invoke(builder)
    }.getOrNull()
}

private fun createReplaySuccessResponse(
    resp: Any,
    builderClass: Class<*>,
    bodyClass: Class<*>,
    mtClass: Class<*>
): Any? {
    val cachedBody = SpotifyAuthCache.body ?: return null
    return runCatching {
        val builder = findMethodSafe(resp.javaClass, "newBuilder")?.invoke(resp) ?: return null
        findMethodSafe(builder.javaClass, "code", java.lang.Integer.TYPE)?.invoke(builder, 200)
        findMethodSafe(builder.javaClass, "message", String::class.java)?.invoke(builder, "OK")

        val replayBody = createResponseBody(bodyClass, mtClass, SpotifyAuthCache.contentType, cachedBody)
        if (replayBody != null) {
            findMethodSafe(builder.javaClass, "body", bodyClass)?.invoke(builder, replayBody)
        }
        findMethodSafe(builder.javaClass, "build")?.invoke(builder)
    }.getOrNull()
}

private fun installOkHttpShield(cl: ClassLoader) {
    val chainClasses = listOfNotNull(
        runCatching { Class.forName("okhttp3.internal.http.RealInterceptorChain", false, cl) }.getOrNull(),
        runCatching { Class.forName("okhttp3.Interceptor\$Chain", false, cl) }.getOrNull()
    ).distinct()

    if (chainClasses.isEmpty()) {
        PatcherLog.info { "OkHttp classes not found; skipping network interceptor shield." }
        return
    }

    val reqClass = runCatching { Class.forName("okhttp3.Request", false, cl) }.getOrNull() ?: return
    val builderClass = runCatching { Class.forName("okhttp3.Response\$Builder", false, cl) }.getOrNull() ?: return
    val bodyClass = runCatching { Class.forName("okhttp3.ResponseBody", false, cl) }.getOrNull() ?: return
    val mtClass = runCatching { Class.forName("okhttp3.MediaType", false, cl) }.getOrNull() ?: return
    val protocolClass = runCatching { Class.forName("okhttp3.Protocol", false, cl) }.getOrNull() ?: return

    val hookedMethods = mutableSetOf<Method>()

    chainClasses.forEach { chainClass ->
        val proceedMethod = chainClass.methods.firstOrNull {
            it.name == "proceed" && it.parameterTypes.size == 1 && it.parameterTypes[0].isAssignableFrom(reqClass)
        } ?: chainClass.declaredMethods.firstOrNull {
            it.name == "proceed" && it.parameterTypes.size == 1 && it.parameterTypes[0].isAssignableFrom(reqClass)
        } ?: return@forEach

        if (!hookedMethods.add(proceedMethod)) return@forEach

        XposedBridge.hookMethod(proceedMethod, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val req = param.args.getOrNull(0) ?: return
                val urlObj = runCatching { findMethodSafe(req.javaClass, "url")?.invoke(req) }.getOrNull() ?: return
                val host = runCatching { findMethodSafe(urlObj.javaClass, "host")?.invoke(urlObj) as? String }.getOrNull() ?: ""
                val path = runCatching { findMethodSafe(urlObj.javaClass, "encodedPath")?.invoke(urlObj) as? String }.getOrNull() ?: ""
                val fullUrl = urlObj.toString()

                val isDetectionEndpoint = host.contains("spclient") && (
                    path.contains("dual-sync") ||
                    path.contains("social-connect") ||
                    path.contains("melody/v1/check")
                )

                val isTelemetryOrAd = fullUrl.contains("analytics.spotify.com") ||
                    fullUrl.contains("tracking.spotify.com") ||
                    fullUrl.contains("log.spotify.com") ||
                    fullUrl.contains("crashdump.spotify.com") ||
                    (host.contains("spclient") && (path.contains("/ads/") || path.contains("/ad-logic/")))

                if (isDetectionEndpoint || isTelemetryOrAd) {
                    val mockResponse = createEmpty204Response(builderClass, bodyClass, mtClass, protocolClass, req, cl)
                    if (mockResponse != null) {
                        PatcherLog.info { "Safely intercepted and neutralized telemetry/detection request: $path" }
                        param.result = mockResponse
                    }
                }
            }

            override fun afterHookedMethod(param: MethodHookParam) {
                val resp = param.result ?: return
                val code = runCatching { findMethodSafe(resp.javaClass, "code")?.invoke(resp) as? Int }.getOrNull() ?: return
                val req = runCatching { findMethodSafe(resp.javaClass, "request")?.invoke(resp) }.getOrNull() ?: return
                val urlObj = runCatching { findMethodSafe(req.javaClass, "url")?.invoke(req) }.getOrNull() ?: return
                val host = runCatching { findMethodSafe(urlObj.javaClass, "host")?.invoke(urlObj) as? String }.getOrNull() ?: ""

                val isAuthEndpoint = host.contains("login5") || host.contains("spotify.com") || host.contains("googleusercontent")

                if (code in 200..299 && isAuthEndpoint) {
                    val bodyObj = runCatching { findMethodSafe(resp.javaClass, "body")?.invoke(resp) }.getOrNull() ?: return
                    val peeked = runCatching {
                        findMethodSafe(resp.javaClass, "peekBody", java.lang.Long.TYPE)?.invoke(resp, 65536L)
                    }.getOrNull() ?: bodyObj

                    val text = runCatching { findMethodSafe(peeked.javaClass, "string")?.invoke(peeked) as? String }.getOrNull()
                    if (text?.contains("access_token") == true) {
                        SpotifyAuthCache.body = text
                        SpotifyAuthCache.contentType = runCatching { findMethodSafe(peeked.javaClass, "contentType")?.invoke(peeked) }.getOrNull()
                        PatcherLog.info { "Spotify authentication token successfully cached for session preservation." }
                    }
                } else if ((code == 401 || code == 403) && isAuthEndpoint && SpotifyAuthCache.body != null) {
                    PatcherLog.info { "Auth endpoint returned HTTP $code; replaying cached session to prevent forced logout." }
                    val replay = createReplaySuccessResponse(resp, builderClass, bodyClass, mtClass)
                    if (replay != null) {
                        param.result = replay
                    }
                }
            }
        })
    }
}

private fun installSharedPreferencesShield(cl: ClassLoader) {
    val editorClasses = listOfNotNull(
        runCatching { Class.forName("android.app.SharedPreferencesImpl\$EditorImpl") }.getOrNull(),
        runCatching { SharedPreferences.Editor::class.java }.getOrNull()
    ).distinct()

    val protectedTokens = setOf("token", "session", "auth", "login", "account", "credential")

    editorClasses.forEach { editorClass ->
        runCatching {
            XposedHelpers.findAndHookMethod(editorClass, "remove", String::class.java, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val key = param.args.getOrNull(0) as? String ?: return
                    if (protectedTokens.any { key.contains(it, ignoreCase = true) }) {
                        PatcherLog.info { "Prevented remote clearing of auth credential in SharedPreferences: $key" }
                        param.result = param.thisObject
                    }
                }
            })
        }

        runCatching {
            XposedHelpers.findAndHookMethod(editorClass, "clear", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    PatcherLog.info { "Prevented SharedPreferences.clear() to preserve active session." }
                    param.result = param.thisObject
                }
            })
        }
    }
}

