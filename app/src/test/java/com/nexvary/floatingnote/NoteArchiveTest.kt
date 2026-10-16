package com.fgmachines.fgnote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class NoteArchiveTest {
    @Test fun roundTripPreservesArabicAndMetadata() {
        val source = listOf(
            Note(10, "عنوان\nمحتوى عربي", 123456L, 3, true, 999999999L),
            Note(11, "Second note", 555L, 0, false, null)
        )
        val out = ByteArrayOutputStream()
        NoteArchive.export(source, out)
        val result = NoteArchive.read(ByteArrayInputStream(out.toByteArray()))
        assertEquals(2, result.size)
        assertEquals(source.map { it.body }, result.map { it.body })
        assertEquals(3, result[0].color)
        assertTrue(result[0].pinned)
        assertEquals(999999999L, result[0].reminderAt)
        assertFalse(result[1].pinned)
    }

    @Test fun independentTitleSurvivesBackupAndOldFilesRemainReadable() {
        val original = Note(71, "Only body", 777L, 1, false, null, "عنوان مختلف")
        val bytes = ByteArrayOutputStream()
        NoteArchive.export(listOf(original), bytes)
        val decoded = NoteArchive.read(ByteArrayInputStream(bytes.toByteArray()))
        assertEquals("عنوان مختلف", decoded.single().customTitle)
        assertEquals("Only body", decoded.single().body)

        val legacy = """{"format":"nexvary-floating-note","version":1,"notes":[{"body":"Legacy note","updatedAt":12,"color":0,"pinned":false,"reminderAt":null}]}"""
        val restored = NoteArchive.read(ByteArrayInputStream(legacy.toByteArray()))
        assertEquals("", restored.single().customTitle)
        assertEquals("Legacy note", restored.single().title)
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesOversizeArchive() {
        NoteArchive.read(ByteArrayInputStream(ByteArray(NoteArchive.MAX_BYTES + 1)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesWrongBackupFormat() {
        val raw = """{"format":"other","version":1,"notes":[]}"""
        NoteArchive.read(ByteArrayInputStream(raw.toByteArray()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOutOfRangeColors() {
        val raw = """{"format":"nexvary-floating-note","version":1,"notes":[{"body":"test","updatedAt":1,"color":20,"pinned":false,"reminderAt":null}]}"""
        NoteArchive.read(ByteArrayInputStream(raw.toByteArray()))
    }
}
