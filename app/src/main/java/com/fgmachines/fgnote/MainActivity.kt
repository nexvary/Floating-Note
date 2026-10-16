package com.fgmachines.fgnote

import android.Manifest
import android.app.Activity
import android.app.StatusBarManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import android.app.ActivityManager
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.text.InputType
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

class MainActivity : Activity() {
    companion object {
        const val EXTRA_OPEN_NOTE = "open_note"
        const val EXTRA_TILE_TOGGLE = "fg_note_quick_tile_toggle"
        const val EXTRA_PICK_IMAGE_NOTE = "pick_image_note"
        private const val REQUEST_EXPORT = 310
        private const val REQUEST_IMPORT = 311
        private const val REQUEST_SECURE_EXPORT = 312
        private const val REQUEST_SECURE_IMPORT = 313
        private const val REQUEST_EXPORT_PDF = 314
        private const val REQUEST_EXPORT_PNG = 315
        private const val REQUEST_PICK_IMAGE = 316
        private const val REQUEST_EXPORT_TXT = 317
        private const val REQUEST_EXPORT_HTML = 318
    }

    private lateinit var database: NoteStore
    private lateinit var root: LinearLayout
    private var search = ""
    private var gridMode = true
    private var backCallback: android.window.OnBackInvokedCallback? = null
    private var toolsExpanded = false
    private var exportId = -1L
    private var pendingImageId = -1L
    private var pendingOverlayAction: Pair<String, Long>? = null
    private var noteContainer: LinearLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = NoteStore(this)
        savedInstanceState?.let { saved ->
            saved.getString("pending_overlay_action")?.let { action ->
                pendingOverlayAction = action to saved.getLong("pending_overlay_id", -1L)
            }
        }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Palette.BACK)
            isFocusableInTouchMode = true
        }
        setContentView(root)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        root.requestFocus()
        if (Build.VERSION.SDK_INT >= 33) {
            val callback = android.window.OnBackInvokedCallback { confirmExit() }
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback
            )
            backCallback = callback
        }
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
        handleOpenIntent(intent)
        handleImageIntent(intent)
        handleQuickTileIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOpenIntent(intent)
        handleImageIntent(intent)
        handleQuickTileIntent(intent)
    }

    private fun handleOpenIntent(intent: Intent?) {
        val id = intent?.getLongExtra(EXTRA_OPEN_NOTE, -1L) ?: -1L
        if (id >= 0 && database.get(id) != null) {
            start(FloatingNoteService.ACTION_OPEN, id)
            intent?.removeExtra(EXTRA_OPEN_NOTE)
        }
    }

    /** Invoked if the system prohibits direct quick-tile background service launch. */
    private fun handleQuickTileIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_TILE_TOGGLE, false) != true) return
        intent.removeExtra(EXTRA_TILE_TOGGLE)
        if (isRunning()) stop() else start(FloatingNoteService.ACTION_START)
    }

    private fun addQuickTile() {
        if (Build.VERSION.SDK_INT >= 33) {
            val manager = getSystemService(StatusBarManager::class.java)
            try {
                manager.requestAddTileService(
                    ComponentName(this, FGNoteTileService::class.java),
                    getString(R.string.quick_tile_label),
                    Icon.createWithResource(this, R.drawable.ic_quick_tile),
                    mainExecutor
                ) { result ->
                    if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED) {
                        Toast.makeText(this, R.string.quick_tile_added, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, R.string.quick_tile_manual, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (_: RuntimeException) {
                Toast.makeText(this, R.string.quick_tile_manual, Toast.LENGTH_LONG).show()
            }
        } else {
            AlertDialog.Builder(this)
                .setTitle(R.string.quick_tile_label)
                .setMessage(R.string.quick_tile_manual)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    private fun handleImageIntent(intent: Intent?) {
        val id = intent?.getLongExtra(EXTRA_PICK_IMAGE_NOTE, -1L) ?: -1L
        if (id < 0 || database.get(id) == null) return
        intent?.removeExtra(EXTRA_PICK_IMAGE_NOTE)
        pendingImageId = id
        startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }, REQUEST_PICK_IMAGE)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        confirmExit()
    }

    private fun confirmExit() {
        AlertDialog.Builder(this)
            .setTitle(R.string.exit_save_title)
            .setMessage(R.string.exit_save_message)
            .setPositiveButton(R.string.save_and_exit) { _, _ ->
                stop()
                finish()
            }
            .setNegativeButton(R.string.keep_editing, null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        if (::database.isInitialized) {
            render()
            val pending = pendingOverlayAction
            if (pending != null && overlayAllowed()) {
                pendingOverlayAction = null
                start(pending.first, pending.second)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        pendingOverlayAction?.let { pending ->
            outState.putString("pending_overlay_action", pending.first)
            outState.putLong("pending_overlay_id", pending.second)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= 33) {
            backCallback?.let { onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it) }
        }
        database.close()
        super.onDestroy()
    }

    private fun overlayAllowed() = Settings.canDrawOverlays(this)
    @Suppress("DEPRECATION")
    private fun isRunning(): Boolean {
        // Package-local running service lookup remains available from Android 8+.
        // Unlike SharedPreferences, it cannot remain stale after Force Stop.
        val manager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        return manager.getRunningServices(100).any { service ->
            service.service.className == FloatingNoteService::class.java.name
        }
    }

    private fun start(action: String, id: Long = -1L) {
        if (!overlayAllowed()) {
            pendingOverlayAction = action to id
            requestOverlay()
            return
        }
        val intent = Intent(this, FloatingNoteService::class.java).apply {
            this.action = action
            if (id >= 0) putExtra(FloatingNoteService.EXTRA_NOTE_ID, id)
        }
        try {
            startForegroundService(intent)
            askNotificationPermission()
            // Service updates overlay_state asynchronously; refresh on the UI thread.
            root.postDelayed({
                if (!isFinishing && !isDestroyed) render()
            }, 550L)
        } catch (error: RuntimeException) {
            Toast.makeText(this, error.localizedMessage ?: "Unable to start overlay", Toast.LENGTH_LONG).show()
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 200)
    }

    private fun stop() {
        stopService(Intent(this, FloatingNoteService::class.java))
        getSharedPreferences("overlay_state", MODE_PRIVATE).edit().putBoolean("active", false).apply()
        render()
        FGNoteTileService.refresh(this)
    }

    private fun requestOverlay() {
        val settings = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName"))
        try {
            startActivity(settings)
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

        content.addView(label("FG MACHINES  /  NOTES", 12f, Palette.MINT, true))
        content.addSpace(this, 9)
        content.addView(label(getString(R.string.app_name), 29f, Palette.WHITE, true))
        content.addSpace(this, 7)
        content.addView(label(getString(R.string.tagline), 14f, Palette.MUTED))
        content.addSpace(this, 22)

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
        setup.addSpace(this, 12)
        setup.addView(action(getString(R.string.quick_tile_add), false) {
            addQuickTile()
        })
        // The floating panel is displayed after the note library.

        val archiveBar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        archiveBar.addView(action(getString(R.string.export_notes), false) { exportNotes() },
            LinearLayout.LayoutParams(0, -2, 1f))
        archiveBar.addView(android.view.View(this), LinearLayout.LayoutParams(dp(10), 1))
        archiveBar.addView(action(getString(R.string.import_notes), false) { importNotes() },
            LinearLayout.LayoutParams(0, -2, 1f))
        // Archive controls are attached only when expanded.
        val secureBar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        secureBar.addView(action(getString(R.string.export_secure), true) {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_TITLE, "fg-note-protected-notes.nfn")
            }, REQUEST_SECURE_EXPORT)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        secureBar.addView(android.view.View(this), LinearLayout.LayoutParams(dp(10), 1))
        secureBar.addView(action(getString(R.string.import_secure), false) {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }, REQUEST_SECURE_IMPORT)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        // Prioritise notes over maintenance controls.

        val section = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        section.addView(label(getString(R.string.my_notes), 21f, Palette.WHITE, true),
            LinearLayout.LayoutParams(0, dp(46), 1f))
        if (database.lastDeleted() != null) {
            section.addView(icon(R.drawable.ic_undo, getString(R.string.undo_delete),
                Palette.PANEL_LIGHT, 46) {
                val restored = database.undoLastDelete()
                if (restored != null) {
                    ReminderScheduler.schedule(this, restored)
                    Toast.makeText(this, R.string.note_restored, Toast.LENGTH_SHORT).show()
                }
                render()
            })
        }
        section.addView(icon(R.drawable.ic_grid, getString(R.string.grid_mode),
            Palette.PANEL_LIGHT, 46) {
            gridMode = !gridMode
            displayNotes()
        })
        section.addView(icon(R.drawable.ic_add, getString(R.string.new_note),
            Palette.PANEL_LIGHT, 46) {
            val newId = database.create()
            start(FloatingNoteService.ACTION_OPEN, newId)
            render()
        })
        content.addView(section)
        content.addSpace(this, 12)

        val filter = EditText(this).apply {
            setSingleLine(true)
            setText(search)
            setTextColor(Palette.WHITE)
            setHintTextColor(Palette.MUTED)
            setHint(R.string.search_notes)
            textSize = 15f
            setPadding(dp(17), dp(10), dp(17), dp(10))
            background = roundShape(Palette.PANEL_LIGHT, 14)
        }
        content.addView(filter, LinearLayout.LayoutParams(-1, dp(52)))
        content.addSpace(this, 16)

        noteContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(noteContainer)
        displayNotes()
        content.addSpace(this, 14)
        content.addView(setup)
        content.addSpace(this, 14)
        content.addView(action(getString(R.string.backup_tools), false) {
            toolsExpanded = !toolsExpanded
            render()
        })
        if (toolsExpanded) {
            content.addSpace(this, 10)
            content.addView(archiveBar)
            content.addSpace(this, 10)
            content.addView(secureBar)
        }
        filter.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                search = s?.toString().orEmpty()
                displayNotes()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        root.requestFocus()
    }

    private fun displayNotes() {
        val parent = noteContainer ?: return
        parent.removeAllViews()
        val list = database.search(search)
        if (list.isEmpty()) {
            val empty = label(getString(if (search.isNotEmpty()) R.string.no_results
                else R.string.empty_notes), 15f, Palette.MUTED)
            empty.setPadding(dp(12), dp(24), dp(12), dp(24))
            parent.addView(empty)
        } else if (gridMode && resources.configuration.screenWidthDp >= 350) {
            list.chunked(2).forEach { pair ->
                val line = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.TOP
                }
                pair.forEach { addNoteCard(line, it, grid = true) }
                if (pair.size == 1) line.addView(android.view.View(this),
                    LinearLayout.LayoutParams(0, 1, 1f))
                parent.addView(line, LinearLayout.LayoutParams(-1, -2))
            }
        } else list.forEach { addNoteCard(parent, it, grid = false) }
    }

    private fun addNoteCard(parent: LinearLayout, note: Note, grid: Boolean) {
        val accent = NoteColors.accents[note.color]
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundShape(NoteColors.backgrounds[note.color], 18, accent)
            setPadding(dp(15), dp(14), dp(12), dp(12))
        }
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        heading.addView(label(note.title.ifBlank { getString(R.string.untitled) }, 17f,
            Palette.WHITE, true).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(0, -2, 1f))
        if (note.pinned) heading.addView(label(" ● ", 17f, accent, true))
        row.addView(heading)
        row.addSpace(this, 6)
        val preview = label(
            NoteText.preview(note.body).ifBlank { getString(R.string.placeholder) },
            13f, Palette.MUTED
        ).apply {
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        row.addView(preview)
        row.addSpace(this, 9)
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val dateText = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(note.updatedAt))
        footer.addView(label(dateText, 11f, Palette.MUTED))
        buttons.addView(icon(R.drawable.ic_palette, getString(R.string.note_color), Palette.PANEL,
            38) { selectColor(note) })
        buttons.addView(icon(R.drawable.ic_pin, getString(R.string.pin_note), Palette.PANEL,
            38) {
            database.setPinned(note.id, !note.pinned)
            displayNotes()
        })
        buttons.addView(icon(R.drawable.ic_bell, getString(R.string.reminders), Palette.PANEL,
            38) { selectReminder(note) })
        buttons.addView(icon(R.drawable.ic_trash, getString(R.string.delete), Palette.PANEL,
            38) {
            AlertDialog.Builder(this)
                .setMessage(R.string.delete_question)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete) { _, _ ->
                    ReminderScheduler.cancel(this, note.id)
                    if (isRunning()) start(FloatingNoteService.ACTION_CLOSE_EDITOR, note.id)
                    database.delete(note.id)
                    render()
                }.show()
        })
        val toolsScroll = android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(buttons)
        }
        footer.addView(toolsScroll, LinearLayout.LayoutParams(-1, dp(40)))
        row.addView(footer)
        row.addSpace(this, 9)
        row.addView(action(getString(R.string.share_note_via_email), false) {
            shareNote(note.id)
        })
        row.addSpace(this, 5)
        row.addView(action(getString(R.string.export_note), false) {
            offerExport(note.id)
        })
        if (note.reminderAt != null) {
            row.addView(label(
                getString(R.string.reminder_set,
                    DateFormat.getDateTimeInstance().format(Date(note.reminderAt))),
                12f, accent
            ))
        }
        row.setOnClickListener { start(FloatingNoteService.ACTION_OPEN, note.id) }
        val layout = if (grid) LinearLayout.LayoutParams(0, -2, 1f)
                     else LinearLayout.LayoutParams(-1, -2)
        layout.bottomMargin = dp(12)
        if (grid) layout.marginEnd = dp(7)
        parent.addView(row, layout)
    }

    /**
     * User-controlled Android share sheet. Supports installed Gmail and Proton Mail
     * clients without accessing mailbox credentials or claiming cloud synchronisation.
     */
    private fun shareNote(noteId: Long) {
        val note = database.get(noteId) ?: return
        val subject = note.title.ifBlank { getString(R.string.untitled) }
        val message = buildString {
            append(subject)
            if (note.body.isNotBlank()) {
                append("\n\n")
                append(note.body)
            }
        }
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, message)
        }
        startActivity(Intent.createChooser(share, getString(R.string.share_note_via_email)))
    }

    private fun offerExport(id: Long) {
        val options = arrayOf(getString(R.string.export_pdf), getString(R.string.export_png),
            getString(R.string.export_txt), getString(R.string.export_html))
        AlertDialog.Builder(this).setTitle(R.string.export_note)
            .setItems(options) { _, which ->
                exportId = id
                startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = when (which) {
                        0 -> "application/pdf"
                        1 -> "image/png"
                        2 -> "text/plain"
                        else -> "text/html"
                    }
                    val title = database.get(id)?.title.orEmpty()
                        .replace(Regex("[^\\p{L}\\p{N}_ -]"), "_")
                        .take(55).ifBlank { "FG-Note" }
                    val suffix = arrayOf(".pdf", ".png", ".txt", ".html")[which]
                    putExtra(Intent.EXTRA_TITLE, title + suffix)
                }, REQUEST_EXPORT_PDF + which)
            }.show()
    }

    private fun selectColor(note: Note) {
        AlertDialog.Builder(this)
            .setTitle(R.string.note_color)
            .setSingleChoiceItems(
                arrayOf(
                    getString(R.string.color_mint), getString(R.string.color_sage),
                    getString(R.string.color_peach), getString(R.string.color_blue),
                    getString(R.string.color_violet)
                ), note.color
            ) { dialog, selected ->
                database.setColor(note.id, selected)
                dialog.dismiss()
                displayNotes()
            }.show()
    }

    private fun selectReminder(note: Note) {
        if (note.reminderAt == null) {
            showReminderPicker(note)
        } else {
            AlertDialog.Builder(this)
                .setTitle(R.string.reminders)
                .setMessage(R.string.change_or_cancel_reminder)
                .setPositiveButton(R.string.change_reminder) { _, _ ->
                    showReminderPicker(note)
                }
                .setNegativeButton(R.string.cancel_reminder) { _, _ ->
                    database.setReminder(note.id, null)
                    ReminderScheduler.cancel(this, note.id)
                    displayNotes()
                }
                .setNeutralButton(R.string.cancel, null)
                .show()
        }
    }

    private fun showReminderPicker(note: Note) {
        val calendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 1) }
        DatePickerDialog(this, { _, year, month, day ->
            TimePickerDialog(this, { _, hour, minute ->
                calendar.set(year, month, day, hour, minute, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                if (calendar.timeInMillis > System.currentTimeMillis()) {
                    database.setReminder(note.id, calendar.timeInMillis)
                    database.get(note.id)?.let { ReminderScheduler.schedule(this, it) }
                    askNotificationPermission()
                    displayNotes()
                } else Toast.makeText(this, R.string.choose_future, Toast.LENGTH_SHORT).show()
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun exportNotes() {
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "nexvary-notes-backup.json")
        }, REQUEST_EXPORT)
    }

    private fun importNotes() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }, REQUEST_IMPORT)
    }

    @Deprecated("Uses platform activity-result mechanism without external dependencies.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        if (requestCode == REQUEST_PICK_IMAGE) {
            val id = pendingImageId
            pendingImageId = -1L
            if (id >= 0) {
                val service = Intent(this, FloatingNoteService::class.java).apply {
                    action = FloatingNoteService.ACTION_INSERT_IMAGE
                    putExtra(FloatingNoteService.EXTRA_NOTE_ID, id)
                    putExtra("image_uri", uri.toString())
                }
                startForegroundService(service)
            }
            return
        }
        if (requestCode in REQUEST_EXPORT_PDF..REQUEST_EXPORT_HTML) {
            val note = database.get(exportId) ?: return
            Thread {
                try {
                    contentResolver.openOutputStream(uri)?.use { output ->
                        when (requestCode) {
                            REQUEST_EXPORT_PDF -> NoteExporter.pdf(applicationContext, note, output)
                            REQUEST_EXPORT_PNG -> NoteExporter.png(applicationContext, note, output)
                            REQUEST_EXPORT_TXT -> NoteExporter.txt(note, output)
                            REQUEST_EXPORT_HTML -> NoteExporter.html(applicationContext, note, output)
                        }
                    } ?: error("Unable to write export")
                    runOnUiThread {
                        if (!isFinishing) Toast.makeText(this, R.string.export_success,
                            Toast.LENGTH_SHORT).show()
                    }
                } catch (error: Exception) {
                    runOnUiThread {
                        if (!isFinishing) AlertDialog.Builder(this)
                            .setMessage(R.string.export_failed)
                            .setPositiveButton(android.R.string.ok, null).show()
                    }
                }
            }.start()
            return
        }
        if (requestCode == REQUEST_SECURE_EXPORT || requestCode == REQUEST_SECURE_IMPORT) {
            askArchivePassword(requestCode == REQUEST_SECURE_EXPORT) { password ->
                runSecureArchive(requestCode, uri, password)
            }
            return
        }
        try {
            if (requestCode == REQUEST_EXPORT) {
                contentResolver.openOutputStream(uri)?.use { NoteArchive.export(database.all(), it) }
                    ?: error("Unable to open destination")
                Toast.makeText(this, R.string.export_success, Toast.LENGTH_LONG).show()
            } else if (requestCode == REQUEST_IMPORT) {
                val incoming = contentResolver.openInputStream(uri)?.use { NoteArchive.read(it) }
                    ?: error("Unable to open backup")
                val ids = database.importNotes(incoming)
                ids.forEach { id -> database.get(id)?.let { ReminderScheduler.schedule(this, it) } }
                displayNotes()
                Toast.makeText(this, getString(R.string.import_success, ids.size),
                    Toast.LENGTH_LONG).show()
            }
        } catch (error: Exception) {
            AlertDialog.Builder(this)
                .setTitle(R.string.archive_error)
                .setMessage(error.localizedMessage ?: getString(R.string.archive_error))
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    private fun askArchivePassword(export: Boolean, submit: (CharArray) -> Unit) {
        fun field(hintRes: Int): EditText = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = getString(hintRes)
            isSingleLine = true
        }
        val password = field(R.string.backup_password)
        val confirm = if (export) field(R.string.confirm_backup_password) else null
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(10), dp(22), dp(6))
            addView(password)
            if (confirm != null) addView(confirm)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (export) R.string.export_secure else R.string.import_secure)
            .setMessage(if (export) R.string.backup_password_hint else R.string.restore_password_hint)
            .setView(container)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val typed = password.text
                val pass = CharArray(typed.length) { index -> typed[index] }
                if (pass.isEmpty() || (export && pass.size < 10)) {
                    pass.fill('\u0000')
                    Toast.makeText(this, R.string.password_too_short, Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                if (confirm != null) {
                    val repeated = confirm.text
                    val check = CharArray(repeated.length) { index -> repeated[index] }
                    val matches = pass.contentEquals(check)
                    check.fill('\u0000')
                    if (!matches) {
                        pass.fill('\u0000')
                        Toast.makeText(this, R.string.password_mismatch, Toast.LENGTH_LONG).show()
                        return@setOnClickListener
                    }
                }
                dialog.dismiss()
                submit(pass)
            }
        }
        dialog.show()
    }

    private fun runSecureArchive(requestCode: Int, uri: Uri, password: CharArray) {
        Thread {
            try {
                val store = NoteStore(applicationContext)
                val affected = try {
                    if (requestCode == REQUEST_SECURE_EXPORT) {
                        val snapshot = store.all()
                        contentResolver.openOutputStream(uri)?.use {
                            SecureNoteArchive.export(snapshot, password, it)
                        } ?: error("Could not open backup file")
                        -1
                    } else {
                        val restored = contentResolver.openInputStream(uri)?.use {
                            SecureNoteArchive.read(it, password)
                        } ?: error("Could not read backup file")
                        val ids = store.importNotes(restored)
                        ids.forEach { id ->
                            store.get(id)?.let { ReminderScheduler.schedule(applicationContext, it) }
                        }
                        ids.size
                    }
                } finally {
                    store.close()
                }
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        displayNotes()
                        Toast.makeText(this,
                            if (affected == -1) getString(R.string.export_success)
                            else getString(R.string.import_success, affected),
                            Toast.LENGTH_LONG).show()
                    }
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        AlertDialog.Builder(this)
                            .setTitle(R.string.archive_error)
                            .setMessage(getString(R.string.secure_restore_failed))
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                    }
                }
            } finally {
                password.fill('\u0000')
            }
        }.start()
    }

}
