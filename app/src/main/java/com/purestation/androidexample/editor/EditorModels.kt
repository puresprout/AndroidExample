package com.purestation.androidexample.editor

import android.net.Uri
import android.text.SpannableStringBuilder

// 행 타입 정의
sealed class Row {
    data class TextRow(var text: SpannableStringBuilder = SpannableStringBuilder("")) : Row()
    data class ImageRow(val items: List<ImageItem>) : Row()
    data class VideoRow(val uri: Uri, var thumb: android.graphics.Bitmap? = null) : Row()
    data class YoutubeRow(val url: String, var thumbUrl: String? = null) : Row()
}

data class ImageItem(val uri: Uri, val isPortrait: Boolean)
