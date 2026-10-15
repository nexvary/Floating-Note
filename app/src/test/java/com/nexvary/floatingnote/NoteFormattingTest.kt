package com.nexvary.floatingnote

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteFormattingTest {
    @Test fun createsAndTogglesTask() {
        assertEquals("- [ ] Buy milk", NoteFormatting.toggleTask("Buy milk"))
        assertEquals("- [x] Buy milk", NoteFormatting.toggleTask("- [ ] Buy milk"))
        assertEquals("- [ ] Buy milk", NoteFormatting.toggleTask("- [x] Buy milk"))
    }
    @Test fun supportsArabicTask() {
        assertEquals("- [x] مراجعة", NoteFormatting.toggleTask("- [ ] مراجعة"))
    }
    @Test fun boldMarkupRoundTrip() {
        assertEquals("**Hello**", NoteFormatting.bold("Hello"))
        assertEquals("Hello", NoteFormatting.bold("**Hello**"))
        assertEquals("****", NoteFormatting.bold(""))
    }
}
