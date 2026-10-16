package com.fgmachines.fgnote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import java.io.OutputStream

/** Visible offline rendering of rich note content as PDF or a single PNG. */
object NoteExporter {
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val PAD = 42

    private fun layout(context: Context, note: Note, width: Int, textSize: Float): StaticLayout {
        val styled = RichNotes.deserialize(context, note.richHtml, note.body)
        val content = SpannableStringBuilder(styled)
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(37, 49, 51)
            this.textSize = textSize
        }
        return StaticLayout.Builder.obtain(content, 0, content.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(true)
            .setLineSpacing(2f, 1.1f)
            .build()
    }

    private fun drawHeader(canvas: Canvas, title: String, width: Int, pad: Int) {
        canvas.drawColor(Color.rgb(255, 253, 245))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 105, 103)
            textSize = 23f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val shown = if (title.length > 100) title.take(99) + "…" else title
        canvas.drawText(shown, pad.toFloat(), 79f, paint)
        paint.color = Color.rgb(239, 191, 99)
        canvas.drawRoundRect(pad.toFloat(), 96f, (width-pad).toFloat(),
            100f, 2f, 2f, paint)
    }

    fun pdf(context: Context, note: Note, destination: OutputStream) {
        val document = PdfDocument()
        try {
            val text = layout(context, note, PAGE_W - 2 * PAD, 13f)
            val available = PAGE_H - 150
            val pages = maxOf(1, (text.height + available - 1) / available)
            require(pages <= 75) { "Note too long for PDF export" }
            for (pageIndex in 0 until pages) {
                val info = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageIndex + 1).create()
                val page = document.startPage(info)
                val canvas = page.canvas
                drawHeader(canvas, note.title, PAGE_W, PAD)
                canvas.save()
                canvas.clipRect(PAD, 125, PAGE_W - PAD, PAGE_H - 30)
                canvas.translate(PAD.toFloat(), 125f - pageIndex * available)
                text.draw(canvas)
                canvas.restore()
                document.finishPage(page)
            }
            document.writeTo(destination)
        } finally {
            document.close()
        }
    }

    fun png(context: Context, note: Note, destination: OutputStream) {
        val width = 1080
        val layout = layout(context, note, width - 100, 30f)
        val height = (layout.height + 230).coerceAtLeast(750)
        require(height <= 8000) { "Long note: use PDF export instead" }
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bmp)
            canvas.save()
            canvas.scale(1f, 1f)
            drawHeader(canvas, note.title, width, 50)
            canvas.translate(50f, 150f)
            layout.draw(canvas)
            canvas.restore()
            require(bmp.compress(Bitmap.CompressFormat.PNG, 100, destination)) {
                "PNG conversion failed"
            }
            destination.flush()
        } finally {
            bmp.recycle()
        }
    }
}
