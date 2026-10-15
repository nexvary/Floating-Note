package com.nexvary.floatingnote

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Note(
    val id: Long,
    val body: String,
    val updatedAt: Long,
    val color: Int = 0,
    val pinned: Boolean = false,
    val reminderAt: Long? = null
) {
    val title: String get() = NoteText.title(body)
}

object NoteText {
    fun title(body: String): String = body.lineSequence().map { it.trim() }
        .firstOrNull { it.isNotEmpty() }?.take(70) ?: ""
    fun preview(body: String): String = body.trim().replace('\n', ' ').take(150)
    fun matches(body: String, query: String): Boolean =
        body.contains(query.trim(), ignoreCase = true)
}

object NoteColors {
    val backgrounds = intArrayOf(
        0xFF162327.toInt(), 0xFF263B34.toInt(), 0xFF3D302C.toInt(),
        0xFF2B324A.toInt(), 0xFF403148.toInt()
    )
    val accents = intArrayOf(
        0xFF65F0B1.toInt(), 0xFF9CE4B2.toInt(), 0xFFFFC69B.toInt(),
        0xFFAABBFF.toInt(), 0xFFE5B3F9.toInt()
    )
    fun valid(index: Int): Int = index.coerceIn(0, backgrounds.lastIndex)
}

/** Versioned migrations preserve all existing user notes, including 0.1.0 databases. */
class NoteStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "floating_notes.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE notes (id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "body TEXT NOT NULL, updated_at INTEGER NOT NULL, " +
                "color INTEGER NOT NULL DEFAULT 0, pinned INTEGER NOT NULL DEFAULT 0, " +
                "reminder_at INTEGER)"
        )
        db.execSQL("CREATE INDEX notes_updated_idx ON notes(updated_at DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE notes ADD COLUMN color INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE notes ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE notes ADD COLUMN reminder_at INTEGER")
        }
    }

    fun create(body: String = "", color: Int = 0): Long {
        val values = ContentValues().apply {
            put("body", body)
            put("updated_at", System.currentTimeMillis())
            put("color", NoteColors.valid(color))
        }
        return writableDatabase.insertOrThrow("notes", null, values)
    }

    fun update(id: Long, body: String): Boolean {
        val values = ContentValues().apply {
            put("body", body)
            put("updated_at", System.currentTimeMillis())
        }
        return writableDatabase.update("notes", values, "id = ?", arrayOf(id.toString())) > 0
    }

    fun setColor(id: Long, color: Int): Boolean =
        change(id, ContentValues().apply { put("color", NoteColors.valid(color)) })

    fun setPinned(id: Long, pinned: Boolean): Boolean =
        change(id, ContentValues().apply { put("pinned", if (pinned) 1 else 0) })

    fun setReminder(id: Long, at: Long?): Boolean =
        change(id, ContentValues().apply {
            if (at == null) putNull("reminder_at") else put("reminder_at", at)
        })

    private fun change(id: Long, values: ContentValues): Boolean =
        writableDatabase.update("notes", values, "id = ?", arrayOf(id.toString())) > 0

    fun delete(id: Long): Boolean =
        writableDatabase.delete("notes", "id = ?", arrayOf(id.toString())) > 0

    private val columns = arrayOf("id", "body", "updated_at", "color", "pinned", "reminder_at")

    private fun row(c: android.database.Cursor) = Note(
        c.getLong(0), c.getString(1), c.getLong(2), NoteColors.valid(c.getInt(3)),
        c.getInt(4) != 0, if (c.isNull(5)) null else c.getLong(5)
    )

    fun get(id: Long): Note? {
        readableDatabase.query(
            "notes", columns, "id = ?", arrayOf(id.toString()), null, null, null, "1"
        ).use { c ->
            return if (c.moveToFirst()) row(c) else null
        }
    }

    fun all(): List<Note> {
        val result = mutableListOf<Note>()
        readableDatabase.query(
            "notes", columns, null, null, null, null,
            "pinned DESC, updated_at DESC, id DESC"
        ).use { c -> while (c.moveToNext()) result.add(row(c)) }
        return result
    }

    fun search(query: String): List<Note> =
        if (query.isBlank()) all() else all().filter { NoteText.matches(it.body, query) }

    /**
     * Validate archive before calling. Use a transaction so failures never leave
     * a partially imported archive. Merge; do NOT wipe existing notes.
     */
    fun importNotes(items: List<Note>): List<Long> {
        val db = writableDatabase
        val ids = mutableListOf<Long>()
        db.beginTransaction()
        try {
            for (n in items) {
                val values = ContentValues().apply {
                    put("body", n.body)
                    put("updated_at", n.updatedAt)
                    put("color", NoteColors.valid(n.color))
                    put("pinned", if (n.pinned) 1 else 0)
                    if (n.reminderAt != null && n.reminderAt > System.currentTimeMillis()) {
                        put("reminder_at", n.reminderAt)
                    }
                }
                ids.add(db.insertOrThrow("notes", null, values))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return ids
    }
}
