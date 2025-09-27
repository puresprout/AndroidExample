package com.purestation.androidexample.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.TypedValue

/**
 * Int 값을 dp(dp 단위)로 변환해주는 확장 함수
 * - v: 변환할 값
 * - resources.displayMetrics를 이용해 px 단위로 변환
 */
fun Context.dp(v: Int): Int =
    TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()

/**
 * 이미지가 세로형(Portrait)인지 가로형(Landscape)인지 판별
 * - BitmapFactory.Options의 inJustDecodeBounds = true 옵션 사용 → 실제 디코딩 없이 크기만 가져옴
 * - width < height → 세로(Portrait)로 판단
 */
fun isPortrait(context: Context, uri: Uri): Boolean = try {
    context.contentResolver.openInputStream(uri).use { input ->
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(input, null, opts) // 이미지 크기만 읽기
        val w = opts.outWidth;
        val h = opts.outHeight
        h >= w
    }
} catch (_: Exception) {
    true
} // 실패 시 기본값 true(세로) 반환

/**
 * 동영상의 첫 프레임(썸네일) 추출
 * - MediaMetadataRetriever 사용
 * - frameAtTime: 특정 시점의 프레임을 Bitmap으로 반환 (기본값은 0초 근처)
 */
fun createVideoThumbnail(context: Context, uri: Uri): Bitmap? = try {
    val retriever = android.media.MediaMetadataRetriever()
    retriever.setDataSource(context, uri)
    val bmp = retriever.frameAtTime
    retriever.release()
    bmp
} catch (_: Exception) {
    null
}

/**
 * 유튜브 URL에서 동영상 ID(11자리 문자열) 추출
 * - 일반 URL: https://www.youtube.com/watch?v=xxxx
 * - 짧은 URL: https://youtu.be/xxxx
 * - embed URL: https://www.youtube.com/embed/xxxx
 */
fun extractYoutubeId(url: String): String? {
    val patterns = listOf(
        "v=([\\w-]{11})",             // 일반 watch?v= 패턴
        "youtu\\.be/([\\w-]{11})",    // 짧은 주소 패턴
        "youtube\\.com/embed/([\\w-]{11})" // embed 주소 패턴
    )
    for (p in patterns) {
        val m = Regex(p).find(url)
        if (m != null) return m.groupValues[1]
    }
    return null
}

/**
 * HTML escape 처리
 * - 특수문자를 HTML 엔티티로 변환
 */
fun escapeHtml(s: String): String =
    s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&#39;")

/**
 * HTML unescape 처리
 * - HTML 엔티티를 원래 문자로 복원
 */
fun unescapeHtml(s: String): String =
    s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&#39;", "'").replace("&amp;", "&")
