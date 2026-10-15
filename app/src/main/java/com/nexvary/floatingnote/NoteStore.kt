package com.nexvary.floatingnote

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Note(
    val id: Long,
    val body: String,
    val updatedAt: Long
) {
    val title: String get() = NoteText.title(body)
}

object NoteText {
    fun title(body: String): String = body.lineSequence().map { it.trim() }
        .firstOrNull { it.isNotEmpty() }?.take(70) ?: ""
    fun preview(body: String): String = body.trim().replace('\n', ' ').take(150)
}

/** Database never leaves the device. No internet permission is requested. */
class NoteStore(context: Context) : SQLiteOpenHelper(context.applicationContext, "floating_notes.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE notes (id INTEGER PRIMARY KEY AUTOINCREMENT, body TEXT NOT NULL, updated_at INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX notes_updated_idx ON notes(updated_at DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Explicit migrations will be added when the schema version changes. Never drop user notes.
    }

    fun create(body: String = ""): Long {
        val values = ContentValues().apply {
            put("body", body)
            put("updated_at", System.currentTimeMillis())
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

    fun delete(id: Long): Boolean =
        writableDatabase.delete("notes", "id = ?", arrayOf(id.toString())) > 0

    fun get(id: Long): Note? {
        readableDatabase.query(
            "notes", arrayOf("id", "body", "updated_at"), "id = ?",
            arrayOf(id.toString()), null, null, null, "1"
        ).use { c ->
            return if (c.moveToFirst()) Note(c.getLong(0), c.getString(1), c.getLong(2)) else null
        }
    }

    fun all(): List<Note> {
        val notes = mutableListOf<Note>()
        readableDatabase.query(
            "notes", arrayOf("id", "body", "updated_at"), null, null, null, null,
            "updated_at DESC, id DESC"
        ).use { c ->
            while (c.moveToNext()) notes.add(Note(c.getLong(0), c.getString(1), c.getLong(2)))
        }
        return notes
    }
}
