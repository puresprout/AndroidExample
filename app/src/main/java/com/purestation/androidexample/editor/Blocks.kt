package com.purestation.androidexample.editor

import android.graphics.Typeface
import android.net.Uri
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan

sealed class Block {
    data class Paragraph(
        var text: SpannableStringBuilder = SpannableStringBuilder(""),
        var isH1: Boolean = false
    ) : Block()

    data class ImageGrid(
        val uris: MutableList<Uri>,
        var columns: Int = 1
    ) : Block()

    data class Video(
        val uri: Uri
    ) : Block()

    data class YouTube(
        val videoId: String
    ) : Block()
}

fun SpannableStringBuilder.deepCopy(): SpannableStringBuilder {
    val copy = SpannableStringBuilder(this.toString())
    val spans = getSpans(0, length, Any::class.java)
    for (s in spans) {
        copy.setSpan(
            s, getSpanStart(s), getSpanEnd(s),
            if (getSpanFlags(s) != 0) getSpanFlags(s) else Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }
    return copy
}

fun sampleParagraph(text: String, bold: Boolean = false): Block.Paragraph {
    val s = SpannableStringBuilder(text)
    if (bold) {
        s.setSpan(StyleSpan(Typeface.BOLD), 0, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    return Block.Paragraph(s, isH1 = false)
}
