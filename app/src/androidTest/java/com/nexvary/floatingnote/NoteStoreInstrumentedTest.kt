package com.nexvary.floatingnote

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Device-level regression tests against the actual SQLiteOpenHelper, not a mock. */
@RunWith(AndroidJUnit4::class)
class NoteStoreInstrumentedTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(context.deleteDatabase(DB_NAME) || !context.getDatabasePath(DB_NAME).exists())
    }

    @After
    fun cleanUp() {
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun upgradeFromOriginalV1PreservesIdsBodiesAndDates() {
        val file = context.getDatabasePath(DB_NAME)
        file.parentFile?.mkdirs()
        val original = SQLiteDatabase.openOrCreateDatabase(file, null)
        original.execSQL(
            "CREATE TABLE notes (id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "body TEXT NOT NULL, updated_at INTEGER NOT NULL)"
        )
        original.execSQL("CREATE INDEX notes_updated_idx ON notes(updated_at DESC)")
        original.execSQL(
            "INSERT INTO notes (id, body, updated_at) VALUES (?, ?, ?)",
            arrayOf<Any>(47L, "عنوان قديم\nتفاصيل أصلية", 123_456L)
        )
        original.version = 1
        original.close()

        withStore { store ->
            val migrated = store.get(47)
            assertNotNull(migrated)
            assertEquals("عنوان قديم\nتفاصيل أصلية", migrated!!.body)
            assertEquals(123_456L, migrated.updatedAt)
            assertEquals(0, migrated.color)
            assertFalse(migrated.pinned)
            assertNull(migrated.reminderAt)
            assertTrue(store.setPinned(47, true))
            assertTrue(store.setColor(47, 3))
            assertTrue(store.update(47, "بعد الترقية"))
        }
        withStore { store ->
            val reopened = store.get(47)!!
            assertEquals("بعد الترقية", reopened.body)
            assertTrue(reopened.pinned)
            assertEquals(3, reopened.color)
        }
    }

    @Test
    fun pinnedOrderingArabicSearchAndPersistence() {
        val reminderAt = System.currentTimeMillis() + 86_400_000L
        val regular: Long
        val pinned: Long
        withStore { store ->
            regular = store.create("English note", 2)
            pinned = store.create("ملاحظة مهمة\nتجربة", 4)
            assertTrue(store.setPinned(pinned, true))
            assertTrue(store.setReminder(pinned, reminderAt))
            assertEquals(listOf(pinned, regular), store.all().map { it.id })
            assertEquals(listOf(pinned), store.search("مهمة").map { it.id })
            assertEquals(listOf(regular), store.search("english").map { it.id })
        }
        withStore { store ->
            val saved = store.get(pinned)!!
            assertTrue(saved.pinned)
            assertEquals(4, saved.color)
            assertEquals(reminderAt, saved.reminderAt)
            assertTrue(store.delete(regular))
            assertNull(store.get(regular))
            assertEquals(1, store.all().size)
        }
    }

    @Test
    fun importingSameBackupTwiceIsIdempotentAndMergePreservesLocalNotes() {
        withStore { store ->
            val existing = store.create("My existing unsynced note")
            val imported = Note(-1, "النص المستورد", 55_555L, 3, true, null)
            val first = store.importNotes(listOf(imported, imported))
            assertEquals(1, first.size)
            assertEquals(0, store.importNotes(listOf(imported)).size)
            assertNotNull(store.get(existing))
            assertEquals(2, store.all().size)
            assertEquals(imported.body, store.get(first.single())?.body)
        }
    }

    @Test
    fun invalidRecordIdCannotOverwriteOrDeleteOtherNotes() {
        withStore { store ->
            val id = store.create("Private original")
            assertFalse(store.update(-1, "Attack"))
            assertFalse(store.delete(-1))
            assertEquals("Private original", store.get(id)?.body)
        }
    }

    @Test
    fun encryptedBackupWorksOnDeviceCryptoProviderAndRejectsWrongPassword() {
        val passphrase = "Device-Test-Key-123456".toCharArray()
        val note = Note(-1, "ملحوظة سرية\nPrivate content", 2222L, 2, true, null)
        val sink = java.io.ByteArrayOutputStream()
        try {
            SecureNoteArchive.export(listOf(note), passphrase, sink)
            val raw = sink.toByteArray()
            assertFalse(String(raw, Charsets.UTF_8).contains("Private content"))
            val restored = SecureNoteArchive.read(
                java.io.ByteArrayInputStream(raw), passphrase
            )
            assertEquals(note.body, restored.single().body)
            assertEquals(note.color, restored.single().color)
            try {
                SecureNoteArchive.read(
                    java.io.ByteArrayInputStream(raw), "Wrong-Password-123456".toCharArray()
                )
                throw AssertionError("Wrong password unexpectedly decrypted backup")
            } catch (_: IllegalArgumentException) {
                // Authenticated encryption rejected the wrong key.
            }
        } finally {
            passphrase.fill('\u0000')
        }
    }

    /** SQLiteOpenHelper is not AutoCloseable on all supported Android releases. */
    private inline fun <T> withStore(block: (NoteStore) -> T): T {
        val store = NoteStore(context)
        return try {
            block(store)
        } finally {
            store.close()
        }
    }

    companion object {
        private const val DB_NAME = "floating_notes.db"
    }
}
