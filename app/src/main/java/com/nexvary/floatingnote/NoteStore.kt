package com.fgmachines.fgnote

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
    val reminderAt: Long? = null,
    val customTitle: String = "",
    val richHtml: String = "",
    val titleSizeSp: Int = 20,
    val titleInk: Int = 0
) {
    val title: String get() = customTitle.ifBlank { NoteText.title(body) }
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
        0xFFFFF4CD.toInt(), 0xFFDDF5E9.toInt(), 0xFFFFE5DB.toInt(),
        0xFFE4EDFF.toInt(), 0xFFF5E5F6.toInt()
    )
    val accents = intArrayOf(
        0xFFAC7100.toInt(), 0xFF167E60.toInt(), 0xFFB64D4B.toInt(),
        0xFF3C65BA.toInt(), 0xFF8C50AA.toInt()
    )
    fun valid(index: Int): Int = index.coerceIn(0, backgrounds.lastIndex)
}

/** Versioned migrations preserve all existing user notes, including 0.1.0 databases. */
class NoteStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "floating_notes.db", null, 6) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE notes (id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "body TEXT NOT NULL, updated_at INTEGER NOT NULL, " +
                "color INTEGER NOT NULL DEFAULT 0, pinned INTEGER NOT NULL DEFAULT 0, " +
                "reminder_at INTEGER, title TEXT NOT NULL DEFAULT '', rich_html TEXT NOT NULL DEFAULT '', deleted_at INTEGER, title_size INTEGER NOT NULL DEFAULT 20, title_ink INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL("CREATE INDEX notes_updated_idx ON notes(updated_at DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE notes ADD COLUMN color INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE notes ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE notes ADD COLUMN reminder_at INTEGER")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE notes ADD COLUMN title TEXT NOT NULL DEFAULT ''")
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE notes ADD COLUMN rich_html TEXT NOT NULL DEFAULT ''")
        }
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE notes ADD COLUMN deleted_at INTEGER")
        }
        if (oldVersion < 6) {
            db.execSQL("ALTER TABLE notes ADD COLUMN title_size INTEGER NOT NULL DEFAULT 20")
            db.execSQL("ALTER TABLE notes ADD COLUMN title_ink INTEGER NOT NULL DEFAULT 0")
        }
    }

    fun create(body: String = "", color: Int = 0, title: String = ""): Long {
        val values = ContentValues().apply {
            put("body", body)
            put("title", title)
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

    /** Atomically persist the independent title and body in one autosave operation. */
    fun updateNote(id: Long, title: String, body: String): Boolean {
        val values = ContentValues().apply {
            put("title", title)
            put("body", body)
            put("updated_at", System.currentTimeMillis())
        }
        return writableDatabase.update("notes", values, "id = ?", arrayOf(id.toString())) > 0
    }

    fun updateRich(id: Long, title: String, body: String, richHtml: String): Boolean {
        val values = ContentValues().apply {
            put("title", title)
            put("body", body)
            put("rich_html", richHtml)
            put("updated_at", System.currentTimeMillis())
        }
        return writableDatabase.update("notes", values, "id = ?", arrayOf(id.toString())) > 0
    }

    fun setTitleStyle(id: Long, sizeSp: Int, ink: Int): Boolean =
        change(id, ContentValues().apply {
            put("title_size", sizeSp.coerceIn(14, 36))
            put("title_ink", ink)
        })

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

    /** Recoverable soft deletion, including after process restart. */
    fun delete(id: Long): Boolean =
        writableDatabase.update("notes",
            ContentValues().apply { put("deleted_at", System.currentTimeMillis()) },
            "id = ? AND deleted_at IS NULL", arrayOf(id.toString())) > 0

    fun lastDeleted(): Note? {
        readableDatabase.query("notes", columns, "deleted_at IS NOT NULL", null, null,
            null, "deleted_at DESC, id DESC", "1").use { cursor ->
            return if (cursor.moveToFirst()) row(cursor) else null
        }
    }

    fun undoLastDelete(): Note? {
        val last = lastDeleted() ?: return null
        val count = writableDatabase.update("notes",
            ContentValues().apply { putNull("deleted_at") },
            "id = ? AND deleted_at IS NOT NULL", arrayOf(last.id.toString()))
        return if (count == 1) get(last.id) else null
    }

    private val columns = arrayOf("id", "body", "updated_at", "color", "pinned", "reminder_at", "title", "rich_html", "title_size", "title_ink")

    private fun row(c: android.database.Cursor) = Note(
        c.getLong(0), c.getString(1), c.getLong(2), NoteColors.valid(c.getInt(3)),
        c.getInt(4) != 0, if (c.isNull(5)) null else c.getLong(5), c.getString(6), c.getString(7), c.getInt(8), c.getInt(9)
    )

    fun get(id: Long): Note? {
        readableDatabase.query(
            "notes", columns, "id = ? AND deleted_at IS NULL", arrayOf(id.toString()), null, null, null, "1"
        ).use { c ->
            return if (c.moveToFirst()) row(c) else null
        }
    }

    fun all(): List<Note> {
        val result = mutableListOf<Note>()
        readableDatabase.query(
            "notes", columns, "deleted_at IS NULL", null, null, null,
            "pinned DESC, updated_at DESC, id DESC"
        ).use { c -> while (c.moveToNext()) result.add(row(c)) }
        return result
    }

    fun search(query: String): List<Note> =
        if (query.isBlank()) all() else all().filter { NoteText.matches(it.body, query) || NoteText.matches(it.customTitle, query) }

    /**
     * Validate archive before calling. Use a transaction so failures never leave
     * a partially imported archive. Merge; do NOT wipe existing notes.
     */
    fun importNotes(items: List<Note>): List<Long> {
        val db = writableDatabase
        val ids = mutableListOf<Long>()
        // Import is idempotent for identical notes. Keep metadata and timestamp in identity.
        val now = System.currentTimeMillis()
        fun signature(note: Note): List<Any?> = listOf(note.customTitle, note.body, note.richHtml, note.titleSizeSp, note.titleInk, note.updatedAt,
            NoteColors.valid(note.color), note.pinned,
            note.reminderAt?.takeIf { it > now })
        val seen = all().map(::signature).toMutableSet()
        db.beginTransaction()
        try {
            for (n in items) {
                if (!seen.add(signature(n))) continue
                val values = ContentValues().apply {
                    put("body", n.body)
                    put("title", n.customTitle)
                    put("rich_html", n.richHtml)
                    put("title_size", n.titleSizeSp.coerceIn(14, 36))
                    put("title_ink", n.titleInk)
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
