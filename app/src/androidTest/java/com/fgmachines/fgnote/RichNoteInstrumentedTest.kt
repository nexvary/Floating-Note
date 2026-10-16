package com.fgmachines.fgnote

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.Base64

@RunWith(AndroidJUnit4::class)
class RichNoteInstrumentedTest {
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext


    @Test fun quickSettingsTileIsRegisteredWithSystemOnlyPermission() {
        val name = android.content.ComponentName(ctx, FGNoteTileService::class.java)
        val info = ctx.packageManager.getServiceInfo(name, 0)
        assertEquals("android.permission.BIND_QUICK_SETTINGS_TILE", info.permission)
        assertTrue(info.exported)
        assertEquals("FG Note", ctx.getString(R.string.quick_tile_label))
    }

    @Test fun formattingRoundTripsAndIndependentColoursStaySeparate() {
        val note = SpannableStringBuilder("أحمر أخضر")
        RichNotes.colour(note, 0, 4, Color.RED)
        RichNotes.colour(note, 5, note.length, Color.GREEN)
        RichNotes.heading(note, 5, note.length, Color.BLUE)
        val html = RichNotes.serialize(note)
        val restored = RichNotes.deserialize(ctx, html, "")
        assertTrue(restored.toString().contains("أحمر"))
        val spans = restored.getSpans(0, restored.length, ForegroundColorSpan::class.java)
        assertTrue("Must retain multiple coloured spans", spans.size >= 2)
    }

    @Test fun wordAndGraphemeDeletionAndUndoCanRestore() {
        val ed = SpannableStringBuilder("كلمة ثانية 😀")
        val history = RichHistory()
        history.record("العنوان", ed)
        val pos = RichNotes.deletePreviousCharacter(ed, ed.length)
        assertEquals("كلمة ثانية ", ed.toString())
        assertEquals(ed.length, pos)
        val prior = history.undo("العنوان", ed)
        assertEquals("كلمة ثانية 😀", prior!!.body.toString())
        val text = SpannableStringBuilder("hello world")
        RichNotes.deletePreviousWord(text, text.length)
        assertEquals("hello ", text.toString())
    }

    @Test fun pngAndPdfExportsProduceReadableSignatures() {
        val styled = SpannableStringBuilder("Rich colour test\nثاني سطر")
        RichNotes.colour(styled, 0, 4, Color.RED)
        val note = Note(1L, styled.toString(), 123L, 0, false, null,
            "FG Note export", RichNotes.serialize(styled))
        val png = ByteArrayOutputStream()
        NoteExporter.png(ctx, note, png)
        val p = png.toByteArray()
        assertTrue(p.size > 500)
        assertEquals(0x89, p[0].toInt() and 0xFF)
        assertEquals(0x50, p[1].toInt() and 0xFF)
        val pdf = ByteArrayOutputStream()
        NoteExporter.pdf(ctx, note, pdf)
        assertTrue(String(pdf.toByteArray().copyOfRange(0, 5)).startsWith("%PDF-"))
    }

    @Test fun embeddedJpegImageIsRestoredWithEditableSpan() {
        val bitmap = Bitmap.createBitmap(20, 15, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(19, 140, 115))
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 75, output)
        val source = "data:image/jpeg;base64," +
            Base64.getEncoder().encodeToString(output.toByteArray())
        val editable = SpannableStringBuilder("Above\nBelow")
        val image = BitmapDrawable(ctx.resources, bitmap)
        image.setBounds(0, 0, 100, 75)
        RichNotes.insertImage(editable, 6, source, image)
        val encoded = RichNotes.serialize(editable)
        val restored = RichNotes.deserialize(ctx, encoded, editable.toString())
        assertTrue(restored.getSpans(0, restored.length, ImageSpan::class.java).isNotEmpty())
    }
}
