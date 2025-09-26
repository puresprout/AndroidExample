package com.purestation.androidexample.editor

import android.net.Uri
import android.text.Html
import android.text.SpannableStringBuilder
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

object HtmlIO {

    fun toHtml(state: EditorState): String {
        val sb = StringBuilder()
        state.blocks.forEach { b ->
            when (b) {
                is Block.Paragraph -> {
                    val base = Html.toHtml(b.text, Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE)
                    val body = base.replace(Regex("(?s)^.*?<body>|</body>.*$"), "")
                    if (b.isH1) {
                        sb.append("<h1>").append(body.trim()).append("</h1>")
                    } else {
                        sb.append(body)
                    }
                }
                is Block.ImageGrid -> {
                    val cols = b.columns.coerceIn(1, 3)
                    sb.append("""<image-grid cols="$cols">""")
                    b.uris.forEach { uri ->
                        sb.append("""<img src="${uri}" />""")
                    }
                    sb.append("</image-grid>")
                }
                is Block.Video -> {
                    sb.append("""<video src="${b.uri}" />""")
                }
                is Block.YouTube -> {
                    sb.append("""<youtube id="${b.videoId}" />""")
                }
            }
        }
        return sb.toString()
    }

    fun fromHtml(html: String): EditorState {
        val doc = Jsoup.parseBodyFragment(html)
        val blocks = mutableListOf<Block>()
        val body = doc.body()

        fun addParagraph(el: Element, h1: Boolean) {
            val plain = el.html()
            val sp = Html.fromHtml(plain, Html.FROM_HTML_MODE_COMPACT) as android.text.Spanned
            val ssb = SpannableStringBuilder(sp)
            blocks.add(Block.Paragraph(ssb, isH1 = h1))
        }

        for (node in body.children()) {
            when (node.tagName().lowercase()) {
                "p" -> addParagraph(node, false)
                "h1" -> addParagraph(node, true)
                "image-grid" -> {
                    val cols = node.attr("cols").toIntOrNull() ?: 1
                    val imgs = node.getElementsByTag("img").mapNotNull { it.attr("src") }
                        .map { Uri.parse(it) }.toMutableList()
                    blocks.add(Block.ImageGrid(imgs, cols.coerceIn(1, 3)))
                }
                "video" -> {
                    val src = node.attr("src")
                    if (src.isNotBlank()) blocks.add(Block.Video(Uri.parse(src)))
                }
                "youtube" -> {
                    val id = node.attr("id")
                    if (id.isNotBlank()) blocks.add(Block.YouTube(id))
                }
                else -> {
                    val text = node.text()
                    if (text.isNotBlank()) blocks.add(Block.Paragraph(SpannableStringBuilder(text)))
                }
            }
        }
        if (blocks.isEmpty()) blocks.add(Block.Paragraph())
        return EditorState(blocks)
    }
}
