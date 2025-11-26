package com.clipnotes.app.service

import android.app.*
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.clipnotes.app.NoteApplication
import com.clipnotes.app.R
import kotlinx.coroutines.*

class NotesFloatingWindowService : Service() {
    private var windowManager: WindowManager? = null
    private var floatingView: FrameLayout? = null
    private var clipboardManager: ClipboardManager? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var notesContainer: LinearLayout? = null
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var initialX = 0
    private var initialY = 0
    private var isDragging = false
    private var isResizing = false

    companion object {
        private const val NOTIFICATION_ID = 3
        private const val CHANNEL_ID = "notes_floating_window_channel"

        fun start(context: Context) {
            val intent = Intent(context, NotesFloatingWindowService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, NotesFloatingWindowService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        showFloatingWindow()
        loadNotes()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "笔记浮窗",
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("笔记浮窗")
            .setContentText("笔记窗口运行中...")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .build()
    }

    private fun showFloatingWindow() {
        floatingView = FrameLayout(this).apply {
            setBackgroundColor(0xFFFFFFFF.toInt())
            elevation = 100f
        }

        val params = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE
            format = PixelFormat.RGBA_8888
            flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            width = 400
            height = 600
            x = 50
            y = 100
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0xFFFFFFFF.toInt())
        }

        val titleBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xFF6200EE.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                80
            )
        }

        val titleText = TextView(this).apply {
            text = "笔记列表"
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f
            )
            gravity = android.view.Gravity.CENTER
        }
        titleBar.addView(titleText)

        notesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            setBackgroundColor(0xFFFFFFFF.toInt())
        }

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            addView(notesContainer)
        }

        container.addView(titleBar)
        container.addView(scrollView)
        floatingView?.addView(container)

        floatingView?.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    initialX = params.x
                    initialY = params.y
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(deltaX) > 5 || Math.abs(deltaY) > 5) {
                        isDragging = true
                        params.x = initialX + deltaX
                        params.y = initialY + deltaY
                        windowManager?.updateViewLayout(floatingView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    true
                }
                else -> false
            }
        }

        windowManager?.addView(floatingView, params)
    }

    private fun loadNotes() {
        scope.launch {
            val app = applicationContext as NoteApplication
            app.repository.getAllNotes().collect { notes ->
                runOnUiThread {
                    notesContainer?.removeAllViews()
                    notes.forEach { note ->
                        val noteView = createNoteItemView(note)
                        notesContainer?.addView(noteView)
                    }
                }
            }
        }
    }

    private fun createNoteItemView(note: com.clipnotes.app.data.NoteEntity): View {
        val itemContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(12, 12, 12, 12)
            setBackgroundColor(if (note.isRead) 0xFFE0E0E0.toInt() else 0xFFFFFFFF.toInt())
            isClickable = true
            isFocusable = true
        }

        val noteText = TextView(this).apply {
            text = note.content.take(50)
            textSize = 14f
            setTextColor(note.textColor)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        itemContainer.addView(noteText)

        itemContainer.setOnClickListener {
            scope.launch {
                val app = applicationContext as NoteApplication
                app.repository.markNoteAsRead(note.id)
                copyToClipboard(note.content)
            }
        }

        return itemContainer
    }

    private fun copyToClipboard(text: String) {
        val clip = ClipData.newPlainText("note", text)
        clipboardManager?.setPrimaryClip(clip)
    }

    private fun runOnUiThread(block: () -> Unit) {
        android.os.Handler(android.os.Looper.getMainLooper()).post(block)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        floatingView?.let { windowManager?.removeView(it) }
        scope.cancel()
    }
}
