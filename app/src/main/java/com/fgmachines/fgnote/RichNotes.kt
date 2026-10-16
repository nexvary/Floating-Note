package com.fgmachines.fgnote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.text.Editable
import android.text.Html
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.graphics.Typeface
import java.io.ByteArrayOutputStream
import java.util.ArrayDeque
import java.util.Base64
import java.util.Locale
import java.text.BreakIterator

/**
 * Offline, portable rich text. Colours, heading sizes, and inline images are
 * serialized as Android-supported HTML. Images are embedded as compact JPEG
 * data URIs, not temporary document-provider links.
 */
object RichNotes {
    private const val IMAGE_PREFIX = "data:image/jpeg;base64,"
    private const val MAX_IMAGE_BYTES = 1_500_000

    fun deserialize(context: Context, html: String, plain: String): SpannableStringBuilder {
        if (html.isBlank()) return SpannableStringBuilder(plain)
        val parsed = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT,
            Html.ImageGetter { src -> decodeDrawable(context, src) }, null)
        return SpannableStringBuilder(parsed)
    }

    fun serialize(body: CharSequence): String =
        Html.toHtml(SpannableStringBuilder(body), Html.TO_HTML_PARAGRAPH_LINES_INDIVIDUAL)

    /** A body can contain embedded image replacement chars. */
    fun plainText(body: CharSequence): String =
        body.toString().replace('\uFFFC', '▧')

    fun imageFromUri(context: Context, uri: Uri): Pair<String, Drawable> {
        val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
            // Do not trust provider sizes or allocate unbounded buffers.
            val data = ByteArrayOutputStream()
            val block = ByteArray(8192)
            while (true) {
                val count = stream.read(block)
                if (count < 0) break
                require(data.size() + count <= 10_000_000) { "Image is too large" }
                data.write(block, 0, count)
            }
            data.toByteArray()
        } ?: error("Cannot read image")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Invalid picture" }
        var sample = 1
        while (bounds.outWidth / sample > 720 || bounds.outHeight / sample > 720) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: error("Invalid image")
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 75, output)
        val encoded = output.toByteArray()
        require(encoded.size < MAX_IMAGE_BYTES) { "Picture exceeds note limit" }
        return (IMAGE_PREFIX + Base64.getEncoder().encodeToString(encoded)) to
            BitmapDrawable(context.resources, bitmap).apply {
                val scale = (context.resources.displayMetrics.density * 245 /
                    bitmap.width).coerceAtMost(1f)
                setBounds(0, 0, (bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1))
            }
    }

    fun insertImage(editable: Editable, offset: Int, source: String, drawable: Drawable) {
        val where = offset.coerceIn(0, editable.length)
        editable.insert(where, "\uFFFC")
        editable.setSpan(ImageSpan(drawable, source, ImageSpan.ALIGN_BOTTOM),
            where, where + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun decodeDrawable(context: Context, src: String?): Drawable? {
        if (src == null || !src.startsWith(IMAGE_PREFIX) || src.length > 2_200_000) return null
        return try {
            val bytes = Base64.getDecoder().decode(src.removePrefix(IMAGE_PREFIX))
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            BitmapDrawable(context.resources, bitmap).apply {
                val scale = (context.resources.displayMetrics.density * 245 /
                    bitmap.width).coerceAtMost(1f)
                setBounds(0, 0, (bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1))
            }
        } catch (_: Exception) { null }
    }

    /** An empty selection styles the word at the caret; otherwise the exact selection. */
    fun selectionOrWord(text: CharSequence, a: Int, b: Int): Pair<Int, Int> {
        var start = minOf(a, b).coerceIn(0, text.length)
        var end = maxOf(a, b).coerceIn(0, text.length)
        if (end > start) return start to end
        while (start > 0 && isWord(text[start - 1])) start--
        while (end < text.length && isWord(text[end])) end++
        return start to end
    }

    private fun isWord(ch: Char) = ch.isLetterOrDigit() || ch == '_' ||
        ch in '\u064B'..'\u065F'

    fun colour(editable: Editable, start: Int, end: Int, value: Int) {
        if (end > start) editable.setSpan(ForegroundColorSpan(value), start, end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    fun heading(editable: Editable, start: Int, end: Int, colour: Int) {
        if (end <= start) return
        editable.setSpan(RelativeSizeSpan(1.55f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        editable.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        editable.setSpan(ForegroundColorSpan(colour), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    fun deletePreviousWord(editable: Editable, position: Int): Int {
        val at = position.coerceIn(0, editable.length)
        var from = at
        while (from > 0 && editable[from - 1].isWhitespace()) from--
        while (from > 0 && !editable[from - 1].isWhitespace()) from--
        if (from == at && from > 0) from--
        editable.delete(from, at)
        return from
    }

    /** Uses grapheme-aware boundaries to avoid splitting an emoji or joined script. */
    fun deletePreviousCharacter(editable: Editable, position: Int): Int {
        val at = position.coerceIn(0, editable.length)
        if (at == 0) return 0
        val iterator = BreakIterator.getCharacterInstance(Locale.getDefault())
        iterator.setText(editable.toString())
        val from = iterator.preceding(at).takeIf { it >= 0 } ?: (at - 1)
        editable.delete(from, at)
        return from
    }
}

/** Local editor history, including spans, title and cursor. No telemetry. */
class RichHistory {
    data class State(val title: String, val body: SpannableStringBuilder)
    private val undo = ArrayDeque<State>()
    private val redo = ArrayDeque<State>()
    private val maxStates = 48

    fun record(title: String, body: CharSequence) {
        val next = State(title, SpannableStringBuilder(body))
        val last = undo.peekLast()
        if (last != null && last.title == next.title && RichNotes.serialize(last.body) ==
            RichNotes.serialize(next.body)) return
        undo.addLast(next)
        if (undo.size > maxStates) undo.removeFirst()
        redo.clear()
    }

    fun undo(title: String, body: CharSequence): State? {
        if (undo.isEmpty()) return null
        redo.addLast(State(title, SpannableStringBuilder(body)))
        return undo.removeLast()
    }

    fun redo(title: String, body: CharSequence): State? {
        if (redo.isEmpty()) return null
        undo.addLast(State(title, SpannableStringBuilder(body)))
        return redo.removeLast()
    }
}
