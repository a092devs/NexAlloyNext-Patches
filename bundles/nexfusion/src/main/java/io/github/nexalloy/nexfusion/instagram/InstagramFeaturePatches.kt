package io.github.nexalloy.nexfusion.instagram

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.text.format.DateUtils
import android.view.Display
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.TextView
import android.widget.Toast
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import io.github.nexalloy.PatcherLog
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.patch
import org.luckypray.dexkit.query.enums.StringMatchType
import java.lang.reflect.Modifier

/**
 * Additional Instagram feature patches ported and adapted from SysAdminDoc/HushGram (GPL-3.0),
 * supporting Instagram 451.0.0.53.80, 450.0.0.46.79, 447.0.0.55.81, and compatible versions.
 *
 * 1. Turn off HDR brightness boosts
 * 2. View DM photos and videos anonymously
 * 3. Upload stories at full quality
 * 4. Tap a feed video to pause it
 * 5. Show if a profile follows you
 * 6. See who a story mentions
 * 7. Show a post's exact time
 * 8. Save profile picture
 * 9. Open developer options
 *
 * All anchors were reverse-engineered against Instagram 451 and are covered by
 * [Fingerprints.kt] so a renamed anchor fails the test suite instead of silently
 * doing nothing on the device.
 */

// 1. Turn off HDR brightness boosts
val TurnOffHdrBoosts = patch(
    name = "Turn off HDR brightness boosts",
    description = "Stops HDR photos and reels from making the screen brighter than everything else.",
) {
    // Window.setColorMode: replace HDR modes (2, 3) with COLOR_MODE_DEFAULT (0)
    for (windowClassName in listOf("android.view.Window", "com.android.internal.policy.PhoneWindow")) {
        runCatching {
            val winClass = classLoader.loadClass(windowClassName)
            winClass.declaredMethods.filter { it.name == "setColorMode" }.forEach { method ->
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val mode = param.args.getOrNull(0) as? Int ?: return
                        if (mode == 2 || mode == 3) {
                            param.args[0] = 0
                        }
                    }
                })
            }
        }
    }

    // Display.isHdr() -> false
    runCatching {
        val displayClass = Display::class.java
        displayClass.declaredMethods.filter { it.name == "isHdr" && it.returnType == java.lang.Boolean.TYPE }.forEach { method ->
            XposedBridge.hookMethod(method, object : XC_MethodReplacement() {
                override fun replaceHookedMethod(param: MethodHookParam): Any = false
            })
        }
    }

    // Display.HdrCapabilities.getSupportedHdrTypes() -> empty IntArray
    runCatching {
        val hdrCapClass = Class.forName("android.view.Display\$HdrCapabilities")
        hdrCapClass.declaredMethods.filter { it.name == "getSupportedHdrTypes" }.forEach { method ->
            XposedBridge.hookMethod(method, object : XC_MethodReplacement() {
                override fun replaceHookedMethod(param: MethodHookParam): Any = intArrayOf()
            })
        }
    }

    // Android 14+ (API >= 34)
    if (Build.VERSION.SDK_INT >= 34) {
        runCatching {
            Display::class.java.declaredMethods.filter { it.name == "getHdrSdrRatio" }.forEach { method ->
                XposedBridge.hookMethod(method, object : XC_MethodReplacement() {
                    override fun replaceHookedMethod(param: MethodHookParam): Any = 1.0f
                })
            }
        }
        runCatching {
            Display.Mode::class.java.declaredMethods.filter { it.name == "getSupportedHdrTypes" }.forEach { method ->
                XposedBridge.hookMethod(method, object : XC_MethodReplacement() {
                    override fun replaceHookedMethod(param: MethodHookParam): Any = intArrayOf()
                })
            }
        }
        runCatching {
            val scTransClass = Class.forName("android.view.SurfaceControl\$Transaction")
            scTransClass.declaredMethods.filter { it.name == "setExtendedRangeBrightness" }.forEach { method ->
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (param.args.size >= 3) {
                            param.args[2] = 1.0f
                        }
                    }
                })
            }
        }
    }

    // Android 15+ (API >= 35)
    if (Build.VERSION.SDK_INT >= 35) {
        runCatching {
            Window::class.java.declaredMethods.filter { it.name == "setDesiredHdrHeadroom" }.forEach { method ->
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.args[0] = 1.0f
                    }
                })
            }
        }
        runCatching {
            SurfaceView::class.java.declaredMethods.filter { it.name == "setDesiredHdrHeadroom" }.forEach { method ->
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.args[0] = 1.0f
                    }
                })
            }
        }
    }
    PatcherLog.info { "NexFusion Instagram: Turn off HDR brightness boosts installed" }
}

// 2. View DM photos and videos anonymously

val ViewDmMediaAnonymously = patch(
    name = "View DM photos and videos anonymously",
    description = "Holds back the Opened notice for view-once photos and videos in direct messages.",
) {
    InstagramRequestFilter.enable(InstagramRequestFilter.Rule.DM_SEEN)
    InstagramRequestFilter.ensureHooks(classLoader)

    runCatching {
        ::visualSeenMethod.hookMethod(object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val callback = param.args.getOrNull(1)
                if (callback != null) {
                    val completeMethod = callback.javaClass.methods.firstOrNull { m ->
                        m.parameterTypes.size == 2 && Modifier.isPublic(m.modifiers)
                    }
                    runCatching { completeMethod?.invoke(callback, null, null) }
                }
                param.result = null
                PatcherLog.info { "NexFusion Instagram: DM visual seen marker completed locally without sending" }
            }
        })
    }.onFailure { PatcherLog.info(it) { "NexFusion Instagram: visualSeenMethod hook skipped, relying on Tigon filter" } }
}

// 3. Upload stories at full quality

val UploadStoriesAtFullQuality = patch(
    name = "Upload stories at full quality",
    description = "Sends the video stories you post at full 1080p resolution and 9 Mbps bitrate instead of 720p.",
) {
    runCatching {
        ::highQualityPrefMethod.hookMethod(object : XC_MethodReplacement() {
            override fun replaceHookedMethod(param: MethodHookParam): Any = true
        })
    }.onFailure { PatcherLog.info(it) { "NexFusion Instagram: highQualityPrefMethod hook skipped" } }

    runCatching {
        val spClass = classLoader.loadClass("android.app.SharedPreferencesImpl")
        for (methodName in listOf("getInt", "getBoolean")) {
            spClass.declaredMethods.filter { it.name == methodName }.forEach { method ->
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val key = param.args.getOrNull(0) as? String ?: return
                        if (key == "high_quality_media_upload") {
                            if (param.method.name == "getInt") param.result = 1
                            else if (param.method.name == "getBoolean") param.result = true
                        }
                    }
                })
            }
        }
    }
    PatcherLog.info { "NexFusion Instagram: Upload stories at full quality installed" }
}

// 4. Tap a feed video to pause it

val TapFeedVideoToPause = patch(
    name = "Tap a feed video to pause it",
    description = "Tapping a video in the home feed pauses or plays it in place instead of opening Reels.",
) {
    runCatching {
        ::feedMediaTapMethod.hookMethod(object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val handler = param.thisObject ?: return
                val media = param.args.getOrNull(1) ?: return
                val isVideo = runCatching {
                    media.javaClass.getMethod("Eby").invoke(media) as? Boolean
                }.getOrNull() ?: false
                if (!isVideo) return

                val holder = param.args.getOrNull(3)
                val state = param.args.getOrNull(4)

                var controller: Any? = null
                for (field in handler.javaClass.declaredFields) {
                    field.isAccessible = true
                    val obj = runCatching { field.get(handler) }.getOrNull() ?: continue
                    val valueMethod = obj.javaClass.methods.firstOrNull { it.name == "getValue" && it.parameterTypes.isEmpty() }
                    val possible = runCatching { valueMethod?.invoke(obj) }.getOrNull() ?: obj
                    if (possible.javaClass.declaredMethods.any {
                        it.parameterTypes.size == 3 && it.returnType == Void.TYPE && it.parameterTypes[0].name.contains("Media")
                    }) {
                        controller = possible
                        break
                    }
                }

                if (controller != null && holder != null && state != null) {
                    val toggleMethod = controller.javaClass.declaredMethods.firstOrNull {
                        it.parameterTypes.size == 3 && it.returnType == Void.TYPE && it.parameterTypes[0].name.contains("Media")
                    }?.apply { isAccessible = true }
                    toggleMethod?.invoke(controller, media, holder, state)
                    param.result = null
                    PatcherLog.info { "NexFusion Instagram: Feed video tap toggled playback in-place (reels opening suppressed)" }
                }
            }
        })
        PatcherLog.info { "NexFusion Instagram: Tap a feed video to pause it installed" }
    }.onFailure { PatcherLog.info(it) { "NexFusion Instagram: feedMediaTapMethod hook failed" } }
}

// 5. Show if a profile follows you

private fun findFirstTextView(view: View): TextView? {
    if (view is TextView) return view
    if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
            val tv = findFirstTextView(view.getChildAt(i))
            if (tv != null) return tv
        }
    }
    return null
}

val ShowIfProfileFollowsYou = patch(
    name = "Show if a profile follows you",
    description = "Adds 'Follows you' or 'Doesn't follow you' indicator beside the user's name on their profile.",
) {
    runCatching {
        ::profileHeaderFullNameMethod.hookMethod(object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val headerView = param.args.firstOrNull { it is View } as? View
                    ?: (param.args.firstOrNull()?.let { arg ->
                        arg.javaClass.declaredFields.firstOrNull { View::class.java.isAssignableFrom(it.type) }
                            ?.apply { isAccessible = true }?.get(arg) as? View
                    }) ?: return

                val context = headerView.context ?: return
                val nameId = context.resources.getIdentifier("profile_header_full_name_above_vanity", "id", context.packageName)
                val nameView = if (nameId != 0) headerView.findViewById<TextView>(nameId) else null
                val targetTextView = nameView
                    ?: (context.resources.getIdentifier("profile_header_full_name", "id", context.packageName).takeIf { it != 0 }?.let { headerView.findViewById<TextView>(it) })
                    ?: findFirstTextView(headerView) ?: return

                var user: Any? = param.args.firstOrNull { it != null && it.javaClass.name.contains("User") }
                if (user == null) {
                    for (arg in param.args) {
                        if (arg == null) continue
                        for (field in arg.javaClass.declaredFields) {
                            field.isAccessible = true
                            val obj = runCatching { field.get(arg) }.getOrNull() ?: continue
                            if (obj.javaClass.name.contains("UserDetailViewModel")) {
                                val userField = obj.javaClass.declaredFields.firstOrNull { it.type.name.contains("User") }
                                user = userField?.apply { isAccessible = true }?.get(obj)
                                if (user != null) break
                            }
                        }
                        if (user != null) break
                    }
                }

                if (user != null) {
                    val isFollowedBy = runCatching {
                        user.javaClass.methods.firstOrNull { it.name == "A2N" && it.parameterTypes.isEmpty() && it.returnType == java.lang.Boolean::class.java }?.invoke(user) as? Boolean
                    }.getOrNull() ?: runCatching {
                        val m = user.javaClass.methods.firstOrNull { it.name == "Cuj" && it.parameterTypes.size == 1 && it.parameterTypes[0] == Integer.TYPE }
                        m?.invoke(user, 1601672934) as? Boolean
                    }.getOrNull() ?: runCatching {
                        val f = user.javaClass.declaredFields.firstOrNull { it.type.name.contains("02A5") }?.apply { isAccessible = true }?.get(user)
                        f?.javaClass?.declaredFields?.firstOrNull { it.name == "A2F" }?.apply { isAccessible = true }?.get(f) as? Boolean
                    }.getOrNull()

                    val label = when (isFollowedBy) {
                        true -> " · Follows you"
                        false -> " · Doesn't follow you"
                        else -> null
                    }

                    if (label != null) {
                        val currentText = targetTextView.text?.toString() ?: ""
                        if (!currentText.contains("Follows you") && !currentText.contains("Doesn't follow you")) {
                            targetTextView.text = currentText + label
                        }
                    }
                }
            }
        })
        PatcherLog.info { "NexFusion Instagram: Show if a profile follows you installed" }
    }.onFailure { PatcherLog.info(it) { "NexFusion Instagram: profileHeaderFullNameMethod hook skipped" } }
}

// 6. See who a story mentions

val SeeWhoStoryMentions = patch(
    name = "See who a story mentions",
    description = "Displays a badge in story viewer showing how many accounts are mentioned, and lists them on tap.",
) {
    runCatching {
        ::reelViewerBindMediaMethod.hookMethod(object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val media = param.args.firstOrNull { it != null && it.javaClass.name.contains("Media") } ?: return
                val pageView = param.args.firstOrNull { it is View } as? View ?: return
                val context = pageView.context ?: return

                val mentions = runCatching {
                    val m = media.javaClass.methods.firstOrNull { it.name == "A9X" && it.parameterTypes.isEmpty() && java.util.List::class.java.isAssignableFrom(it.returnType) }
                        ?: media.javaClass.methods.firstOrNull { it.parameterTypes.isEmpty() && java.util.List::class.java.isAssignableFrom(it.returnType) && (it.name == "reel_mentions" || it.name == "getReelMentions") }
                    m?.invoke(media) as? List<*>
                }.getOrNull() ?: return

                if (mentions.isEmpty()) return

                val textContainerId = context.resources.getIdentifier("reel_viewer_text_container", "id", context.packageName)
                val textContainer = if (textContainerId != 0) pageView.findViewById<ViewGroup>(textContainerId) else null
                if (textContainer != null) {
                    val pillTag = "nexalloy_story_mentions_pill"
                    if (textContainer.findViewWithTag<View>(pillTag) == null) {
                        val pill = TextView(context).apply {
                            tag = pillTag
                            text = " @${mentions.size} "
                            textSize = 12f
                            setTextColor(Color.WHITE)
                            setBackgroundColor(Color.parseColor("#80000000"))
                            setPadding(12, 4, 12, 4)
                            setOnClickListener {
                                val usernames = mentions.mapNotNull { item ->
                                    if (item == null) return@mapNotNull null
                                    val user = runCatching {
                                        item.javaClass.declaredFields.firstOrNull { it.type.name.contains("User") }?.apply { isAccessible = true }?.get(item)
                                            ?: item.javaClass.methods.firstOrNull { it.returnType.name.contains("User") && it.parameterTypes.isEmpty() }?.invoke(item)
                                    }.getOrNull() ?: item
                                    val username = runCatching {
                                        user.javaClass.methods.firstOrNull { it.name == "A8D" || it.name == "getUsername" || it.name == "getId" }?.invoke(user)?.toString()
                                    }.getOrNull() ?: user.toString()
                                    username
                                }.toTypedArray()

                                AlertDialog.Builder(context)
                                    .setTitle("Mentioned Accounts (${mentions.size})")
                                    .setItems(usernames) { _, index ->
                                        val u = usernames.getOrNull(index)?.substringAfter("@")?.trim() ?: return@setItems
                                        InstagramLinks.openExternally(context, "https://www.instagram.com/$u/")
                                    }
                                    .setPositiveButton("Close", null)
                                    .show()
                            }
                        }
                        textContainer.addView(pill)
                    }
                }
            }
        })
        PatcherLog.info { "NexFusion Instagram: See who a story mentions installed" }
    }.onFailure { PatcherLog.info(it) { "NexFusion Instagram: reelViewerBindMediaMethod hook skipped" } }
}

// 7. Show a post's exact time

val ShowPostExactTime = patch(
    name = "Show a post's exact time",
    description = "Shows the exact date and time a post or comment was uploaded instead of relative time like '3h'.",
) {
    runCatching {
        val formatterMethod = ::timeFormatterMethod.memberOrNull as? java.lang.reflect.Method ?: return@runCatching
        val formatterClass = formatterMethod.declaringClass

        formatterClass.declaredMethods.filter { it.returnType == java.lang.String::class.java }.forEach { method ->
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    var seconds: Long? = null
                    for (arg in param.args) {
                        if (arg is Double && arg > 1_000_000_000.0 && arg < 3_000_000_000.0) {
                            seconds = arg.toLong()
                            break
                        } else if (arg is Long && arg > 1_000_000_000L && arg < 3_000_000_000L) {
                            seconds = arg
                            break
                        } else if (arg is Long && arg > 1_000_000_000_000L && arg < 3_000_000_000_000L) {
                            seconds = arg / 1000L
                            break
                        }
                    }
                    if (seconds != null) {
                        val flags = DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH
                        val formatted = DateUtils.formatDateTime(null, seconds * 1000L, flags)
                        if (!formatted.isNullOrEmpty()) {
                            param.result = formatted
                        }
                    }
                }
            })
        }
        PatcherLog.info { "NexFusion Instagram: Show post exact time installed" }
    }.onFailure { PatcherLog.info(it) { "NexFusion Instagram: timeFormatterMethod hook skipped" } }
}

// 8. Save profile picture

val SaveProfilePicture = patch(
    name = "Save profile picture",
    description = "Adds options to view and save full-resolution profile pictures directly from the profile screen.",
) {
    runCatching {
        ::profileOverflowMethod.hookMethod(object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val helper = param.thisObject ?: return
                val activity = helper.javaClass.declaredFields.firstOrNull { Activity::class.java.isAssignableFrom(it.type) }
                    ?.apply { isAccessible = true }?.get(helper) as? Activity ?: return

                var user: Any? = null
                for (field in helper.javaClass.declaredFields) {
                    field.isAccessible = true
                    val obj = runCatching { field.get(helper) }.getOrNull() ?: continue
                    if (obj.javaClass.name.contains("UserDetailViewModel")) {
                        user = obj.javaClass.declaredFields.firstOrNull { it.type.name.contains("User") }
                            ?.apply { isAccessible = true }?.get(obj)
                        if (user != null) break
                    }
                }

                if (user == null) return

                val avatarUrl = runCatching {
                    val getPicMethod = user.javaClass.methods.firstOrNull { it.returnType.name.contains("ImageUrl") && it.parameterTypes.isEmpty() }
                    val imgUrlObj = getPicMethod?.invoke(user)
                    imgUrlObj?.javaClass?.methods?.firstOrNull { it.name == "getUrl" && it.parameterTypes.isEmpty() }?.invoke(imgUrlObj)?.toString()
                }.getOrNull()

                val username = runCatching {
                    user.javaClass.methods.firstOrNull { it.name == "A8D" || it.name == "getUsername" }?.invoke(user)?.toString()
                }.getOrNull() ?: "instagram_user"

                if (avatarUrl != null) {
                    val items = arrayOf("View Profile Picture", "Download Profile Picture", "Copy Username (@$username)")
                    AlertDialog.Builder(activity)
                        .setTitle("@$username Profile")
                        .setItems(items) { _, which ->
                            when (which) {
                                0 -> InstagramLinks.openExternally(activity, avatarUrl)
                                1 -> {
                                    val dm = activity.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                                    val fileName = "${username}_profile_pic_${System.currentTimeMillis()}.jpg"
                                    val request = DownloadManager.Request(Uri.parse(avatarUrl))
                                        .setTitle(fileName)
                                        .setDescription("Downloading Instagram profile picture")
                                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "NexAlloyNext/Instagram/$fileName")
                                    dm?.enqueue(request)
                                    Toast.makeText(activity, "Downloading profile picture...", Toast.LENGTH_SHORT).show()
                                }
                                2 -> {
                                    val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    clipboard?.setPrimaryClip(ClipData.newPlainText("username", username))
                                    Toast.makeText(activity, "Copied @$username", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                    PatcherLog.info { "NexFusion Instagram: Save profile picture options dialog displayed for @$username" }
                }
            }
        })
        PatcherLog.info { "NexFusion Instagram: Save profile picture installed" }
    }.onFailure { PatcherLog.info(it) { "NexFusion Instagram: profileOverflowMethod hook skipped" } }
}

// 9. Open developer options

val OpenDeveloperOptions = patch(
    name = "Open developer options",
    description = "Long-pressing the Home tab opens Instagram's hidden internal developer options and MobileConfig settings.",
) {
    runCatching {
        val opener = ::optionsOpenerMethod.memberOrNull as? java.lang.reflect.Method ?: return@runCatching
        val openerClass = opener.declaringClass
        PatcherLog.info { "NexFusion Instagram: developer options opener found: ${openerClass.name}.${opener.name}" }

        val instanceField = openerClass.declaredFields.firstOrNull { Modifier.isStatic(it.modifiers) && it.type == openerClass }
            ?.apply { isAccessible = true }
        PatcherLog.info { "NexFusion Instagram: developer options instance field: ${instanceField?.name}" }

        ::homeLongPressMethod.hookMethod(object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val handlerObj = param.thisObject ?: return

                val activity = handlerObj.javaClass.declaredFields
                    .firstOrNull { Activity::class.java.isAssignableFrom(it.type) }
                    ?.apply { isAccessible = true }
                    ?.get(handlerObj) as? Activity

                val session = handlerObj.javaClass.declaredFields
                    .firstOrNull { it.type.name.contains("UserSession") }
                    ?.apply { isAccessible = true }
                    ?.get(handlerObj)

                val openerInstance = instanceField?.get(null)
                    ?: runCatching { openerClass.getDeclaredConstructor().newInstance() }.getOrNull()

                PatcherLog.info {
                    "NexFusion Instagram: Home long press - activity=$activity, session=$session, openerInstance=$openerInstance"
                }

                if (activity != null && session != null && openerInstance != null) {
                    try {
                        opener.invoke(openerInstance, activity, activity, session)
                        param.result = true
                        PatcherLog.info { "NexFusion Instagram: Developer options opened from Home long press" }
                    } catch (e: Exception) {
                        PatcherLog.info(e) { "NexFusion Instagram: opener.invoke failed" }
                    }
                } else {
                    val missing = mutableListOf<String>()
                    if (activity == null) missing.add("activity")
                    if (session == null) missing.add("session")
                    if (openerInstance == null) missing.add("openerInstance")
                    PatcherLog.info { "NexFusion Instagram: Home long press - missing: ${missing.joinToString(", ")}" }
                }
            }
        })
        PatcherLog.info { "NexFusion Instagram: Open developer options installed" }
    }.onFailure { PatcherLog.info(it) { "NexFusion Instagram: developer options opener hook skipped" } }
}
