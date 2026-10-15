package com.nexvary.floatingnote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteTextTest {
    @Test fun extractsFirstNonEmptyLine() {
        assertEquals("Important", NoteText.title(" \n\n Important  \nother"))
    }
    @Test fun titleSupportsArabicAndRtlContent() {
        assertEquals("ملاحظتي", NoteText.title("\n  ملاحظتي \n مرحبًا"))
    }
    @Test fun emptyTitleIsSafe() {
        assertEquals("", NoteText.title("  \n \n"))
    }
    @Test fun titleIsTruncated() {
        assertEquals(70, NoteText.title("x".repeat(90)).length)
    }
    @Test fun previewFlattensMultilineNote() {
        assertEquals("hello world", NoteText.preview(" hello\nworld "))
    }
    @Test fun arabicSearchIsCaseInsensitiveWhereRelevant() {
        assertTrue(NoteText.matches("تجربة الملاحظة", "الملاحظة"))
        assertTrue(NoteText.matches("A Note", "note"))
        assertFalse(NoteText.matches("Another", "missing"))
    }
    @Test fun invalidThemeIsClamped() {
        assertEquals(0, NoteColors.valid(-1))
        assertEquals(4, NoteColors.valid(80))
    }
}
