package com.nexvary.floatingnote

import android.app.AppOpsManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.util.Log
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * All windows are created only after the user grants SYSTEM_ALERT_WINDOW.
 * The service is started from the visible Activity, then promoted to a foreground service.
 * No accessibility service, screen reading, or network access is used.
 */
class FloatingNoteService : Service() {
    companion object {
        const val ACTION_START = "com.nexvary.floatingnote.START"
        const val ACTION_OPEN = "com.nexvary.floatingnote.OPEN"
        const val ACTION_NEW = "com.nexvary.floatingnote.NEW"
        const val ACTION_CLOSE_EDITOR = "com.nexvary.floatingnote.CLOSE_EDITOR"
        const val EXTRA_NOTE_ID = "note_id"
        private const val CHANNEL = "floating_notes"
        private const val NOTIFICATION_ID = 1001
    }

    private lateinit var wm: WindowManager
    private lateinit var notes: NoteStore
    private val handler = Handler(Looper.getMainLooper())
    private var bubble: ImageView? = null
    private var menu: LinearLayout? = null
    private data class EditorSession(
        val view: LinearLayout,
        val input: EditText,
        val titleInput: EditText,
        val params: WindowManager.LayoutParams
    )
    // Every open note owns its window and save callback; switching notes never discards edits.
    private val editors = linkedMapOf<Long, EditorSession>()
    private val pendingSaves = mutableMapOf<Long, Runnable>()
    private var activeNoteId = -1L
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var foreground = false
    // Immediately stop the foreground service when the user revokes overlay access.
    private val overlayWatcher = AppOpsManager.OnOpChangedListener { operation, changedPackage ->
        if (operation == AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW &&
            changedPackage == packageName && !Settings.canDrawOverlays(this)
        ) {
            handler.post { stopSelf() }
        }
    }

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        notes = NoteStore(this)
        (getSystemService(APP_OPS_SERVICE) as AppOpsManager).startWatchingMode(
            AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, packageName, overlayWatcher
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show()
            stopSelf()
            return START_NOT_STICKY
        }
        if (!foreground) {
            notificationChannel()
            val notification = notification()
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            foreground = true
        }
        if (intent?.action == ACTION_CLOSE_EDITOR) {
            val id = intent.getLongExtra(EXTRA_NOTE_ID, -1L)
            if (id >= 0) closeEditor(id)
            if (bubble == null) stopSelf()
            return START_NOT_STICKY
        }
        if (bubble == null && !createBubble()) return START_NOT_STICKY
        getSharedPreferences("overlay_state", MODE_PRIVATE)
            .edit().putBoolean("active", true).apply()

        when (intent?.action) {
            ACTION_NEW -> openEditor(notes.create())
            ACTION_OPEN -> {
                val id = intent.getLongExtra(EXTRA_NOTE_ID, -1L)
                openEditor(if (id >= 0 && notes.get(id) != null) id else preferredNote())
            }
            else -> Unit
        }
        return START_STICKY
    }

    private fun notificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    getString(R.string.app_name),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    private fun notification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_note)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(pending)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setOngoing(true)
            .build()
    }

    private fun floatingParams(width: Int, height: Int, focusable: Boolean): WindowManager.LayoutParams {
        val flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            (if (focusable) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        return WindowManager.LayoutParams(
            width, height, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags, PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            if (focusable) softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
    }

    private fun screenWidth(): Int = resources.displayMetrics.widthPixels
    private fun screenHeight(): Int = resources.displayMetrics.heightPixels

    private fun createBubble(): Boolean {
        val icon = icon(R.drawable.ic_note, getString(R.string.open), Palette.PANEL, 62) {
            toggleMenu()
        }
        icon.background = roundShape(Palette.PANEL, 31, Palette.MINT)
        icon.elevation = dp(12).toFloat()
        val prefs = getSharedPreferences("overlay_position", MODE_PRIVATE)
        val p = floatingParams(dp(62), dp(62), false).apply {
            x = prefs.getInt("x", dp(10)).coerceIn(0, max(0, screenWidth() - dp(62)))
            y = prefs.getInt("y", dp(220)).coerceIn(dp(28), max(dp(28), screenHeight() - dp(100)))
        }
        val threshold = dp(8)
        var firstX = 0f
        var firstY = 0f
        var initialX = 0
        var initialY = 0
        var moving = false
        icon.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    firstX = event.rawX
                    firstY = event.rawY
                    initialX = p.x
                    initialY = p.y
                    moving = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - firstX
                    val dy = event.rawY - firstY
                    if (abs(dx) > threshold || abs(dy) > threshold) moving = true
                    if (moving) {
                        p.x = (initialX + dx.toInt()).coerceIn(0, max(0, screenWidth() - dp(62)))
                        p.y = (initialY + dy.toInt()).coerceIn(dp(28), max(dp(28), screenHeight() - dp(100)))
                        updateWindow(icon, p)
                        removeMenu()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (moving) {
                        val dockRight = p.x > (screenWidth() - dp(62)) / 2
                        p.x = if (dockRight) max(0, screenWidth() - dp(66)) else dp(4)
                        updateWindow(icon, p)
                        getSharedPreferences("overlay_position", MODE_PRIVATE).edit()
                            .putInt("x", p.x).putInt("y", p.y).apply()
                    } else {
                        v.performClick()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
        if (!addWindow(icon, p)) return false
        bubble = icon
        bubbleParams = p
        return true
    }

    private fun toggleMenu() {
        if (menu != null) {
            removeMenu()
            return
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(7), dp(7), dp(7), dp(7))
            background = roundShape(Palette.PANEL, 25, Palette.MINT)
            elevation = dp(12).toFloat()
        }

        fun add(iconId: Int, desc: Int, action: () -> Unit) {
            val button = icon(iconId, getString(desc), Palette.PANEL_LIGHT, 43) {
                removeMenu()
                action()
            }
            bar.addView(button)
            if (iconId != R.drawable.ic_close) {
                bar.addView(View(this), LinearLayout.LayoutParams(dp(4), 1))
            }
        }

        add(R.drawable.ic_open, R.string.open) { openEditor(preferredNote()) }
        add(R.drawable.ic_add, R.string.new_note) { openEditor(notes.create()) }
        add(R.drawable.ic_copy, R.string.copy) { copyNote() }
        add(R.drawable.ic_paste, R.string.paste) { pasteNote() }
        add(R.drawable.ic_close, R.string.close) { stopSelf() }

        val p = floatingParams(-2, -2, false)
        val b = bubbleParams ?: return
        p.x = (b.x - dp(195)).coerceIn(0, max(0, screenWidth() - dp(254)))
        p.y = (b.y + dp(67)).coerceAtMost(max(0, screenHeight() - dp(80)))
        if (addWindow(bar, p)) menu = bar
    }

    private fun removeMenu() {
        removeWindow(menu)
        menu = null
    }

    private fun preferredNote(): Long {
        val savedId = getSharedPreferences("overlay_state", MODE_PRIVATE)
            .getLong("last_note", -1L)
        if (savedId != -1L && notes.get(savedId) != null) return savedId
        return notes.all().firstOrNull()?.id ?: notes.create()
    }

    private fun openEditor(noteId: Long) {
        removeMenu()
        editors[noteId]?.let {
            activeNoteId = noteId
            it.input.requestFocus()
            return
        }
        if (editors.size >= 4) {
            toast(R.string.too_many_windows)
            return
        }
        val note = notes.get(noteId) ?: return
        var currentColor = NoteColors.valid(note.color)
        activeNoteId = noteId
        getSharedPreferences("overlay_state", MODE_PRIVATE).edit()
            .putLong("last_note", noteId).apply()

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = roundShape(NoteColors.backgrounds[currentColor], 22,
                NoteColors.accents[currentColor])
            elevation = dp(12).toFloat()
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val heading = label(getString(R.string.untitled), 15f,
            NoteColors.accents[currentColor], true)
        header.addView(heading, LinearLayout.LayoutParams(0, dp(45), 1f))
        header.addView(icon(R.drawable.ic_palette, getString(R.string.note_color), Palette.PANEL, 40) {
            currentColor = (currentColor + 1) % NoteColors.backgrounds.size
            notes.setColor(noteId, currentColor)
            card.background = roundShape(NoteColors.backgrounds[currentColor], 22,
                NoteColors.accents[currentColor])
            heading.setTextColor(NoteColors.accents[currentColor])
        })
        header.addView(icon(R.drawable.ic_copy, getString(R.string.copy), Palette.PANEL, 40) {
            copyNote(noteId)
        })
        header.addView(icon(R.drawable.ic_paste, getString(R.string.paste), Palette.PANEL, 40) {
            pasteNote(noteId)
        })
        header.addView(icon(R.drawable.ic_collapse, getString(R.string.collapse), Palette.PANEL, 40) {
            closeEditor(noteId)
        })
        card.addView(header)
        card.addSpace(this, 8)

        val titleInput = EditText(this).apply {
            setSingleLine(true)
            setText(note.customTitle)
            setTextColor(Palette.WHITE)
            setHintTextColor(Palette.MUTED)
            setHint(R.string.note_title_hint)
            textSize = 18f
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = roundShape(Palette.PANEL_LIGHT, 12)
            textDirection = View.TEXT_DIRECTION_FIRST_STRONG
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        card.addView(titleInput, LinearLayout.LayoutParams(-1, dp(50)))
        card.addSpace(this, 9)

        val input = EditText(this).apply {
            setText(note.body)
            setTextColor(Palette.WHITE)
            setHintTextColor(Palette.MUTED)
            hint = getString(R.string.placeholder)
            textSize = 16f
            gravity = Gravity.TOP or Gravity.START
            textDirection = View.TEXT_DIRECTION_FIRST_STRONG
            setPadding(dp(10), dp(12), dp(10), dp(12))
            background = roundShape(Palette.PANEL_LIGHT, 12)
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            isSingleLine = false
            minLines = 8
        }
        card.addView(input, LinearLayout.LayoutParams(-1, 0, 1f))
        card.addSpace(this, 7)
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
        val resize = icon(R.drawable.ic_resize, getString(R.string.resize_note),
            Palette.PANEL_LIGHT, 36) {}
        footer.addView(icon(R.drawable.ic_checklist,
            getString(R.string.toggle_checklist), Palette.PANEL_LIGHT, 36) {
            val selection = input.selectionStart.coerceAtLeast(0)
            val editable = input.text
            var start = selection
            while (start > 0 && editable[start - 1] != '\n') start--
            var end = selection
            while (end < editable.length && editable[end] != '\n') end++
            val updated = NoteFormatting.toggleTask(
                editable.subSequence(start, end).toString())
            editable.replace(start, end, updated)
            input.setSelection((start + updated.length).coerceIn(0, editable.length))
        })
        footer.addView(icon(R.drawable.ic_bold,
            getString(R.string.mark_bold), Palette.PANEL_LIGHT, 36) {
            val editable = input.text
            val from = input.selectionStart.coerceAtLeast(0)
            val to = input.selectionEnd.coerceAtLeast(from)
            val selected = editable.subSequence(from, to).toString()
            val updated = NoteFormatting.bold(selected)
            editable.replace(from, to, updated)
            input.setSelection((from + updated.length / 2).coerceIn(0, editable.length))
        })
        footer.addView(resize)
        card.addView(footer)
        fun updateHeading() {
            heading.text = titleInput.text.toString().trim()
                .ifBlank { NoteText.title(input.text.toString()) }
                .ifBlank { getString(R.string.untitled) }
        }

        fun scheduleAutosave() {
            updateHeading()
            pendingSaves.remove(noteId)?.let(handler::removeCallbacks)
            val save = Runnable {
                notes.updateNote(noteId, titleInput.text.toString(), input.text.toString())
                pendingSaves.remove(noteId)
            }
            pendingSaves[noteId] = save
            handler.postDelayed(save, 350)
        }
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                scheduleAutosave()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        }
        updateHeading()
        input.addTextChangedListener(watcher)
        titleInput.addTextChangedListener(watcher)

        val sizePrefs = getSharedPreferences("editor_size", MODE_PRIVATE)
        val maxWidth = max(dp(220), screenWidth() - dp(16))
        val maxHeight = max(dp(220), screenHeight() - dp(130))
        val width = sizePrefs.getInt("width", dp(330)).coerceIn(
            min(dp(230), maxWidth), maxWidth)
        val height = sizePrefs.getInt("height", dp(360)).coerceIn(
            min(dp(240), maxHeight), maxHeight)
        // Leave the docked bubble accessible at the edge even on compact displays.
        val bubbleOnLeft = (bubbleParams?.x ?: 0) <= screenWidth() / 2
        val startingWidth = min(width, max(dp(230), screenWidth() - dp(80)))
        val p = floatingParams(startingWidth, height, true).apply {
            val preferredX = if (bubbleOnLeft) dp(72) else dp(8)
            x = preferredX.coerceAtMost(max(0, screenWidth() - startingWidth))
            y = (dp(95) + editors.size * dp(40))
                .coerceAtMost(max(dp(28), screenHeight() - height - dp(60)))
        }
        val minWidth = min(dp(230), maxWidth)
        val minHeight = min(dp(220), maxHeight)
        var resizeStartX = 0f
        var resizeStartY = 0f
        var initialWidth = startingWidth
        var initialHeight = height
        resize.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    resizeStartX = event.rawX
                    resizeStartY = event.rawY
                    initialWidth = p.width
                    initialHeight = p.height
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    p.width = (initialWidth + (event.rawX - resizeStartX).toInt())
                        .coerceIn(minWidth, maxWidth)
                    p.height = (initialHeight + (event.rawY - resizeStartY).toInt())
                        .coerceIn(minHeight, maxHeight)
                    p.x = p.x.coerceIn(0, max(0, screenWidth() - p.width))
                    p.y = p.y.coerceIn(dp(28), max(dp(28), screenHeight() - p.height))
                    updateWindow(card, p)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    sizePrefs.edit().putInt("width", p.width)
                        .putInt("height", p.height).apply()
                    true
                }
                else -> false
            }
        }
        val dragThreshold = dp(7)
        var fromX = 0f
        var fromY = 0f
        var startX = 0
        var startY = 0
        header.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    fromX = event.rawX
                    fromY = event.rawY
                    startX = p.x
                    startY = p.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (abs(event.rawX - fromX) > dragThreshold ||
                        abs(event.rawY - fromY) > dragThreshold
                    ) {
                        p.x = (startX + (event.rawX - fromX).toInt())
                            .coerceIn(0, max(0, screenWidth() - p.width))
                        p.y = (startY + (event.rawY - fromY).toInt())
                            .coerceIn(dp(28), max(dp(28), screenHeight() - p.height))
                        updateWindow(card, p)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> true
                else -> false
            }
        }
        if (!addWindow(card, p)) return
        editors[noteId] = EditorSession(card, input, titleInput, p)
        keepBubbleAboveEditor()
        input.setOnFocusChangeListener { _, focused ->
            if (focused) activeNoteId = noteId
        }
    }

    private fun closeEditor(noteId: Long) {
        pendingSaves.remove(noteId)?.let(handler::removeCallbacks)
        val session = editors.remove(noteId) ?: return
        notes.updateNote(noteId, session.titleInput.text.toString(), session.input.text.toString())
        removeWindow(session.view)
        if (activeNoteId == noteId) activeNoteId = editors.keys.lastOrNull() ?: -1L
    }

    private fun copyNote(noteId: Long = activeNoteId) {
        val id = if (noteId >= 0) noteId else preferredNote()
        val body = editors[id]?.input?.text?.toString()
            ?: notes.get(id)?.body.orEmpty()
        if (body.isEmpty()) {
            toast(R.string.nothing_to_copy)
            return
        }
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), body))
        toast(R.string.copied)
    }

    private fun pasteNote(noteId: Long = activeNoteId) {
        val id = if (noteId >= 0) noteId else preferredNote()
        if (editors[id] == null) openEditor(id)
        val edit = editors[id]?.input ?: return
        activeNoteId = id
        edit.requestFocus()
        val value = try {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.primaryClip?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)?.coerceToText(this)?.toString()
        } catch (_: SecurityException) {
            null
        }
        if (value.isNullOrEmpty()) {
            toast(R.string.clipboard_unavailable)
            return
        }
        val start = max(0, edit.selectionStart)
        val end = max(0, edit.selectionEnd)
        edit.text.replace(min(start, end), max(start, end), value)
    }

    private fun toast(res: Int) {
        Toast.makeText(this, res, Toast.LENGTH_SHORT).show()
    }

    /**
     * Android overlays are Z-ordered by addView. Keep the quick-action bubble on top
     * of the editing window, otherwise an open note can cover its own controls.
     */
    private fun keepBubbleAboveEditor() {
        val icon = bubble ?: return
        val params = bubbleParams ?: return
        try {
            wm.removeViewImmediate(icon)
            if (!addWindow(icon, params)) stopSelf()
        } catch (error: RuntimeException) {
            Log.w("NEXVARYFloating", "Could not keep quick actions accessible", error)
            stopSelf()
        }
    }

    /** Window-manager calls can fail when overlay access is revoked while a note is open. */
    private fun addWindow(view: View, params: WindowManager.LayoutParams): Boolean {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return false
        }
        return try {
            wm.addView(view, params)
            true
        } catch (error: RuntimeException) {
            Log.w("NEXVARYFloating", "Overlay attachment failed", error)
            stopSelf()
            false
        }
    }

    private fun updateWindow(view: View, params: WindowManager.LayoutParams) {
        try {
            wm.updateViewLayout(view, params)
        } catch (error: RuntimeException) {
            Log.w("NEXVARYFloating", "Overlay update failed", error)
            stopSelf()
        }
    }

    private fun removeWindow(view: View?) {
        if (view == null) return
        try {
            wm.removeView(view)
        } catch (error: RuntimeException) {
            Log.w("NEXVARYFloating", "Overlay was already detached", error)
        }
    }

    override fun onDestroy() {
        (getSystemService(APP_OPS_SERVICE) as AppOpsManager)
            .stopWatchingMode(overlayWatcher)
        editors.keys.toList().forEach { closeEditor(it) }
        pendingSaves.values.forEach(handler::removeCallbacks)
        pendingSaves.clear()
        removeMenu()
        removeWindow(bubble)
        bubble = null
        bubbleParams = null
        getSharedPreferences("overlay_state", MODE_PRIVATE)
            .edit().putBoolean("active", false).apply()
        notes.close()
        super.onDestroy()
    }
}
