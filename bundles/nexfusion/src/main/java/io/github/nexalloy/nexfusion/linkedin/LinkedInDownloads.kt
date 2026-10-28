package io.github.nexalloy.nexfusion.linkedin

import android.app.DownloadManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Environment
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.Toast
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.XposedBridge
import io.github.nexalloy.IHook
import io.github.nexalloy.PatcherLog
import io.github.nexalloy.patch

// Ported from heyymichii/michii-patches (GPL-3.0):
// - extensions/extension/src/main/java/app/linkedin/extension/DownloadMediaPatch.java
// - extensions/extension/src/main/java/app/linkedin/extension/Downloads.java
// - extensions/extension/src/main/java/app/linkedin/extension/MediaItem.java

val DownloadMedia = patch(
    name = "Download media",
    description = "Adds a download button to the full-screen photo and video viewer.",
) {
    installMediaViewerPresenterHooks()
}

private const val OVERLAY_TAG = "nexfusion_linkedin_download_overlay"

private val MEDIA_VIEWER_PRESENTERS = listOf(
    "com.linkedin.android.media.pages.mediaviewer.MediaViewerImagePresenter",
    "com.linkedin.android.media.pages.mediaviewer.MultiPhotoImagePresenter",
    "com.linkedin.android.media.pages.mediaviewer.MediaViewerVideoPresenter",
)

private fun IHook.installMediaViewerPresenterHooks() {
    for (presenterClass in MEDIA_VIEWER_PRESENTERS) {
        try {
            val presenter = classLoader.loadClass(presenterClass)
            val onBindMethods = presenter.declaredMethods.filter {
                it.name == "onBind" && it.parameterTypes.size == 2
            }
            if (onBindMethods.isEmpty()) continue
            val hook = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val viewData = param.args.getOrNull(0) ?: return
                    val binding = param.args.getOrNull(1) ?: return
                    attachDownloadOverlay(viewData, binding)
                }
            }
            onBindMethods.forEach { XposedBridge.hookMethod(it, hook) }
        } catch (t: Throwable) {
            PatcherLog.info(t) { "NexFusion: LinkedIn presenter hook failed for $presenterClass" }
        }
    }
}

private fun attachDownloadOverlay(viewData: Any, binding: Any) {
    try {
        val root = binding.javaClass.getMethod("getRoot").invoke(binding) as? ViewGroup ?: return
        var overlay = root.findViewWithTag<View>(OVERLAY_TAG)
        if (overlay == null) {
            overlay = createDownloadOverlay(root.context)
            root.addView(
                overlay,
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
        }
        overlay.bringToFront()

        val button = (overlay as? ViewGroup)?.getChildAt(0) as? ImageButton ?: return
        button.setOnClickListener { v ->
            downloadMedia(v.context, viewData)
        }
    } catch (t: Throwable) {
        PatcherLog.info(t) { "NexFusion: LinkedIn attachDownloadOverlay failed" }
    }
}

private fun createDownloadOverlay(context: Context): View {
    val overlay = FrameLayout(context).apply {
        tag = OVERLAY_TAG
    }
    val button = ImageButton(context).apply {
        setImageResource(android.R.drawable.stat_sys_download)
        contentDescription = "Download media"
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0x80000000.toInt())
        }
        val pad = dp(context, 10)
        setPadding(pad, pad, pad, pad)
    }

    val size = dp(context, 44)
    val params = FrameLayout.LayoutParams(size, size, Gravity.TOP or Gravity.END).apply {
        topMargin = dp(context, 112)
        marginEnd = dp(context, 16)
    }
    overlay.addView(button, params)
    return overlay
}

private fun downloadMedia(context: Context, viewData: Any) {
    try {
        val videoUrl = findVideoUrl(viewData)
        val isVideo = videoUrl != null
        val targetUrl = videoUrl ?: findImageUrl(viewData)

        if (targetUrl == null) {
            Toast.makeText(context, "Media URL not found", Toast.LENGTH_SHORT).show()
            return
        }

        enqueueDownload(context, targetUrl, System.currentTimeMillis().toString(), isVideo)
    } catch (t: Throwable) {
        PatcherLog.info(t) { "NexFusion: LinkedIn downloadMedia failed" }
        Toast.makeText(context, "Download failed: ${t.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun enqueueDownload(context: Context, url: String, assetId: String, isVideo: Boolean) {
    try {
        val extension = if (isVideo) ".mp4" else ".jpg"
        val mimeType = if (isVideo) "video/mp4" else "image/jpeg"
        val fileName = "linkedin_$assetId$extension"
        val subDir = if (isVideo) "Movies/LinkedIn" else "Pictures/LinkedIn"

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(fileName)
            .setMimeType(mimeType)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(
                if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES,
                "LinkedIn/$fileName"
            )

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        manager?.enqueue(request)
        Toast.makeText(context, "Downloading to $subDir", Toast.LENGTH_SHORT).show()
    } catch (t: Throwable) {
        PatcherLog.info(t) { "NexFusion: LinkedIn enqueueDownload failed" }
        Toast.makeText(context, "Failed to start download: ${t.message}", Toast.LENGTH_SHORT).show()
    }
}

/** Finds the highest-resolution MP4 URL from video play metadata. */
private fun findVideoUrl(viewData: Any): String? {
    val meta = readProperty(viewData, "videoPlayMetadata") ?: return null
    val streams = readProperty(meta, "progressiveStreams") as? List<*> ?: return null

    var bestUrl: String? = null
    var bestScore = -1L

    for (stream in streams) {
        if (stream == null) continue
        val width = (readProperty(stream, "width") as? Number)?.toLong() ?: 0L
        val height = (readProperty(stream, "height") as? Number)?.toLong() ?: 0L
        val bitRate = (readProperty(stream, "bitRate") as? Number)?.toLong() ?: 0L
        val score = (width * height * 100_000L) + (bitRate / 1000L)

        val url = firstStreamingLocationUrl(stream)
        if (score > bestScore && url != null) {
            bestScore = score
            bestUrl = url
        }
    }
    return bestUrl
}

private fun firstStreamingLocationUrl(stream: Any): String? {
    val locations = readProperty(stream, "streamingLocations") as? List<*> ?: return null
    for (loc in locations) {
        val url = readProperty(loc, "url") as? String
        if (!url.isNullOrEmpty()) return url
    }
    return null
}

/** Finds the largest rendition image URL from image or imageViewModel attributes. */
private fun findImageUrl(viewData: Any): String? {
    val image = readProperty(viewData, "image") ?: readProperty(viewData, "imageViewModel") ?: return null
    val attributes = readProperty(image, "attributes") as? List<*> ?: return null

    for (attr in attributes) {
        if (attr == null) continue
        val detail = readProperty(attr, "detailData") ?: continue

        val vector = readProperty(detail, "vectorImageValue")
        if (vector != null) {
            val url = largestArtifactUrl(vector)
            if (url != null) return url
        }

        val plain = readProperty(detail, "imageUrlValue")
        val plainUrl = readProperty(plain, "url") as? String
        if (!plainUrl.isNullOrEmpty()) return plainUrl
    }
    return null
}

private fun largestArtifactUrl(vectorImage: Any): String? {
    val artifacts = readProperty(vectorImage, "artifacts") as? List<*> ?: return null
    val rootUrl = readProperty(vectorImage, "rootUrl") as? String ?: ""

    var bestSegment: String? = null
    var bestWidth = -1

    for (artifact in artifacts) {
        if (artifact == null) continue
        val segment = readProperty(artifact, "fileIdentifyingUrlPathSegment") as? String ?: continue
        val width = (readProperty(artifact, "width") as? Number)?.toInt() ?: 0
        if (width > bestWidth) {
            bestWidth = width
            bestSegment = segment
        }
    }

    if (bestSegment == null) return null
    return if (bestSegment.startsWith("http") || rootUrl.isEmpty()) {
        bestSegment
    } else {
        rootUrl + bestSegment
    }
}

private fun readProperty(target: Any?, name: String): Any? {
    if (target == null) return null
    return runCatching {
        var cls: Class<*>? = target.javaClass
        while (cls != null && cls != Any::class.java) {
            for (field in cls.declaredFields) {
                if (field.name == name) {
                    field.isAccessible = true
                    return field.get(target)
                }
            }
            cls = cls.superclass
        }
        val getterName = "get" + name.replaceFirstChar { it.uppercase() }
        val method = target.javaClass.methods.firstOrNull { it.name == getterName && it.parameterTypes.isEmpty() }
        method?.invoke(target)
    }.getOrNull()
}

private fun dp(context: Context, value: Int): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics).toInt()

