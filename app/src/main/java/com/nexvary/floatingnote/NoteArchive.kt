package com.fgmachines.fgnote

import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream

/**
 * User-controlled plaintext export through Android's document picker.
 * JSON is validated completely before any database rows are inserted.
 */
object NoteArchive {
    private const val VERSION = 1
    const val MAX_BYTES = 5 * 1024 * 1024
    const val MAX_NOTES = 2000
    const val MAX_BODY = 100_000

    fun export(notes: List<Note>, output: OutputStream) {
        require(notes.size <= MAX_NOTES) { "Too many notes" }
        val root = JSONObject().put("format", "nexvary-floating-note")
            .put("version", VERSION)
        val list = JSONArray()
        notes.forEach { n ->
            require(n.body.length <= MAX_BODY) { "Note too large" }
            require(n.customTitle.length <= 250) { "Title too long" }
            require(n.richHtml.length <= MAX_BYTES) { "Rich note too large" }
            list.put(JSONObject()
                .put("body", n.body)
                .put("title", n.customTitle)
                .put("richHtml", n.richHtml)
                .put("titleSize", n.titleSizeSp)
                .put("titleInk", n.titleInk)
                .put("updatedAt", n.updatedAt)
                .put("color", n.color)
                .put("pinned", n.pinned)
                .put("reminderAt", n.reminderAt ?: JSONObject.NULL))
        }
        root.put("notes", list)
        val bytes = root.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES) { "Archive too large" }
        output.write(bytes)
        output.flush()
    }

    fun read(input: InputStream): List<Note> {
        val sink = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(sink.size() + count <= MAX_BYTES) { "Archive too large" }
            sink.write(buffer, 0, count)
        }
        val bytes = sink.toByteArray()
        require(bytes.size <= MAX_BYTES) { "Archive too large" }
        val root = JSONObject(String(bytes, Charsets.UTF_8))
        require(root.optString("format") == "nexvary-floating-note") { "Invalid backup format" }
        require(root.optInt("version") == VERSION) { "Unsupported backup version" }
        val entries = root.getJSONArray("notes")
        require(entries.length() <= MAX_NOTES) { "Too many notes" }
        val result = mutableListOf<Note>()
        for (i in 0 until entries.length()) {
            val item = entries.getJSONObject(i)
            val body = item.getString("body")
            val title = item.optString("title", "")
            val html = item.optString("richHtml", "")
            val titleSize = item.optInt("titleSize", 20).coerceIn(14, 36)
            val titleInk = item.optInt("titleInk", 0)
            require(html.length <= MAX_BYTES) { "Rich note too large" }
            require(title.length <= 250) { "Title too long" }
            require(body.length <= MAX_BODY) { "Note too large" }
            val updated = item.getLong("updatedAt")
            require(updated > 0) { "Invalid date" }
            val color = item.getInt("color")
            require(color in NoteColors.backgrounds.indices) { "Invalid color" }
            val reminder = if (item.isNull("reminderAt")) null else item.getLong("reminderAt")
            result.add(Note(-1, body, updated, color, item.getBoolean("pinned"), reminder, title, html, titleSize, titleInk))
        }
        return result
    }
}
