package com.nexvary.floatingnote

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import android.widget.TextView
import java.text.DateFormat
import java.util.Date

class MainActivity : Activity() {
    private lateinit var database: NoteStore
    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = NoteStore(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Palette.BACK)
        }
        setContentView(root)
        root.setOnApplyWindowInsetsListener { _, insets ->
            val top: Int
            val bottom: Int
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                top = bars.top
                bottom = bars.bottom
            } else {
                @Suppress("DEPRECATION")
                top = insets.systemWindowInsetTop
                @Suppress("DEPRECATION")
                bottom = insets.systemWindowInsetBottom
            }
            root.setPadding(dp(20), top + dp(14), dp(20), bottom + dp(12))
            insets
        }
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::database.isInitialized) render()
    }

    override fun onDestroy() {
        database.close()
        super.onDestroy()
    }

    private fun overlayAllowed(): Boolean = Settings.canDrawOverlays(this)

    private fun isRunning(): Boolean =
        getSharedPreferences("overlay_state", MODE_PRIVATE).getBoolean("active", false)

    private fun start(action: String, id: Long = -1L) {
        if (!overlayAllowed()) {
            requestOverlay()
            return
        }
        val intent = Intent(this, FloatingNoteService::class.java).apply {
            this.action = action
            if (id >= 0) putExtra(FloatingNoteService.EXTRA_NOTE_ID, id)
        }
        try {
            startForegroundService(intent)
            if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 200)
            }
        } catch (error: RuntimeException) {
            Toast.makeText(this, error.localizedMessage ?: "Unable to start overlay", Toast.LENGTH_LONG).show()
        }
    }

    private fun stop() {
        stopService(Intent(this, FloatingNoteService::class.java))
        getSharedPreferences("overlay_state", MODE_PRIVATE).edit().putBoolean("active", false).apply()
        render()
    }

    private fun requestOverlay() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        try {
            startActivity(intent)
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun render() {
        root.removeAllViews()
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
        }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        content.addView(label("NEXVARY", 14f, Palette.MINT, bold = true))
        content.addSpace(this, 8)
        content.addView(label(getString(R.string.app_name), 30f, Palette.WHITE, bold = true))
        content.addSpace(this, 6)
        content.addView(label(getString(R.string.tagline), 15f, Palette.MUTED))
        content.addSpace(this, 28)

        val setup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundShape(Palette.PANEL, 24, Palette.PANEL_LIGHT)
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        setup.addView(label(getString(R.string.overlay_title), 20f, Palette.WHITE, true))
        setup.addSpace(this, 8)
        setup.addView(label(getString(R.string.overlay_description), 14f, Palette.MUTED))
        setup.addSpace(this, 20)

        if (!overlayAllowed()) {
            setup.addView(label(getString(R.string.permission_hint), 13f, Palette.MUTED))
            setup.addSpace(this, 12)
            setup.addView(action(getString(R.string.grant_permission), true) { requestOverlay() })
        } else if (isRunning()) {
            setup.addView(action(getString(R.string.stop_bubble), false) { stop() })
        } else {
            setup.addView(action(getString(R.string.start_bubble), true) {
                start(FloatingNoteService.ACTION_START)
            })
        }
        content.addView(setup)
        content.addSpace(this, 28)

        val section = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        section.addView(
            label(getString(R.string.my_notes), 21f, Palette.WHITE, true),
            LinearLayout.LayoutParams(0, dp(46), 1f)
        )
        section.addView(icon(R.drawable.ic_add, getString(R.string.new_note), Palette.PANEL_LIGHT, 44) {
            val id = database.create()
            start(FloatingNoteService.ACTION_OPEN, id)
            render()
        })
        content.addView(section)
        content.addSpace(this, 12)

        val notes = database.all()
        if (notes.isEmpty()) {
            val empty = label(getString(R.string.empty_notes), 15f, Palette.MUTED)
            empty.setPadding(dp(12), dp(24), dp(12), dp(24))
            content.addView(empty)
        } else {
            notes.forEach { note -> addNoteCard(content, note) }
        }
    }

    private fun addNoteCard(parent: LinearLayout, note: Note) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundShape(Palette.PANEL, 18)
            setPadding(dp(14), dp(15), dp(9), dp(15))
        }
        val words = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        words.addView(label(note.title.ifBlank { getString(R.string.untitled) }, 17f, Palette.WHITE, true).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        words.addSpace(this, 6)
        words.addView(label(NoteText.preview(note.body).ifBlank { getString(R.string.placeholder) },
            13f, Palette.MUTED).apply {
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        words.addSpace(this, 9)
        words.addView(label(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(note.updatedAt)), 11f, Palette.MUTED))
        words.setOnClickListener { start(FloatingNoteService.ACTION_OPEN, note.id) }
        row.addView(words, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(icon(R.drawable.ic_trash, getString(R.string.delete), Palette.PANEL, 40) {
            AlertDialog.Builder(this)
                .setMessage(R.string.delete_question)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete) { _, _ ->
                    database.delete(note.id)
                    render()
                }.show()
        })
        val p = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
        parent.addView(row, p)
        row.setOnClickListener { start(FloatingNoteService.ACTION_OPEN, note.id) }
    }
}
