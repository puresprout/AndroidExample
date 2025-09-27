package com.purestation.androidexample.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.TypedValue

/** dp 변환 */
fun Context.dp(v: Int): Int =
    TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()

/** 이미지 방향 판별 */
fun isPortrait(context: Context, uri: Uri): Boolean = try {
    context.contentResolver.openInputStream(uri).use { input ->
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(input, null, opts)
        val w = opts.outWidth; val h = opts.outHeight
        h >= w
    }
} catch (_: Exception) { true }

/** 동영상 썸네일 */
fun createVideoThumbnail(context: Context, uri: Uri): Bitmap? = try {
    val retriever = android.media.MediaMetadataRetriever()
    retriever.setDataSource(context, uri)
    val bmp = retriever.frameAtTime
    retriever.release()
    bmp
} catch (_: Exception) { null }

/** 유튜브 ID 추출 */
fun extractYoutubeId(url: String): String? {
    val patterns = listOf(
        "v=([\\w-]{11})",
        "youtu\\.be/([\\w-]{11})",
        "youtube\\.com/embed/([\\w-]{11})"
    )
    for (p in patterns) {
        val m = Regex(p).find(url)
        if (m != null) return m.groupValues[1]
    }
    return null
}

/** HTML escape/unescape */
fun escapeHtml(s: String): String =
    s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&#39;")

fun unescapeHtml(s: String): String =
    s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&#39;", "'").replace("&amp;", "&")
