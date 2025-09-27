package com.purestation.androidexample.editor

import android.net.Uri
import android.text.SpannableStringBuilder

/**
 * 에디터의 각 Row(행)를 표현하는 sealed class
 * - Row는 텍스트, 이미지, 비디오, 유튜브 링크 등 다양한 형태로 구성될 수 있음
 */
sealed class Row {
    /** 텍스트 Row: SpannableStringBuilder로 스타일(굵게, 기울임, 밑줄 등) 포함 가능 */
    data class TextRow(
        var text: SpannableStringBuilder = SpannableStringBuilder("")
    ) : Row()

    /** 이미지 Row: 여러 개의 이미지를 리스트 형태로 보관 */
    data class ImageRow(
        val items: List<ImageItem>
    ) : Row()

    /** 비디오 Row: Uri로 동영상 경로 저장, 썸네일(Bitmap)을 캐싱할 수 있음 */
    data class VideoRow(
        val uri: Uri,
        var thumb: android.graphics.Bitmap? = null
    ) : Row()

    /** 유튜브 Row: URL과 함께 썸네일 이미지 주소를 저장 */
    data class YoutubeRow(
        val url: String,
        var thumbUrl: String? = null
    ) : Row()
}

/**
 * 개별 이미지 아이템 정보
 * - uri: 이미지의 경로
 * - isPortrait: 세로 이미지 여부 (레이아웃 배치 시 사용)
 */
data class ImageItem(
    val uri: Uri,
    val isPortrait: Boolean
)
