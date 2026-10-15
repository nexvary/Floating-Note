package com.nexvary.floatingnote

import org.junit.Assert.assertEquals
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
}
