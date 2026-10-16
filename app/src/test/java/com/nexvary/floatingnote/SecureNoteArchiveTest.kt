package com.fgmachines.fgnote

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class SecureNoteArchiveTest {
    private val pass = "مفتاح طويل Strong-Passphrase-2026".toCharArray()

    @Test fun roundTripIsAuthenticatedAndNondeterministic() {
        val data = "أهلاً NEXVARY 📝".toByteArray(Charsets.UTF_8)
        val a = SecureNoteArchive.encrypt(data, pass)
        val b = SecureNoteArchive.encrypt(data, pass)
        assertFalse(a.contentEquals(b))
        assertArrayEquals(data, SecureNoteArchive.decrypt(a, pass))
        assertArrayEquals(data, SecureNoteArchive.decrypt(b, pass))
        assertFalse(a.toString(Charsets.UTF_8).contains("أهلاً"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun wrongPasswordIsRejected() {
        val a = SecureNoteArchive.encrypt("secret".toByteArray(), pass)
        SecureNoteArchive.decrypt(a, "wrong-password-123".toCharArray())
    }

    @Test(expected = IllegalArgumentException::class)
    fun modifiedCiphertextIsRejected() {
        val a = SecureNoteArchive.encrypt("private".toByteArray(), pass)
        a[a.lastIndex] = (a.last().toInt() xor 1).toByte()
        SecureNoteArchive.decrypt(a, pass)
    }

    @Test(expected = IllegalArgumentException::class)
    fun corruptedHeaderIsRejected() {
        val a = SecureNoteArchive.encrypt("private".toByteArray(), pass)
        a[0] = 0
        SecureNoteArchive.decrypt(a, pass)
    }

    @Test(expected = IllegalArgumentException::class)
    fun shortPasswordIsRejectedForExport() {
        SecureNoteArchive.encrypt("data".toByteArray(), "short".toCharArray())
    }

    @Test fun exportsAndImportsNotes() {
        val source = listOf(Note(2, "عربي\nPrivate", 99, 4, true, null))
        val out = ByteArrayOutputStream()
        SecureNoteArchive.export(source, pass, out)
        val restored = SecureNoteArchive.read(ByteArrayInputStream(out.toByteArray()), pass)
        assertEquals(source.map { it.body }, restored.map { it.body })
        assertTrue(restored.first().pinned)
        assertEquals(4, restored.first().color)
    }
}
