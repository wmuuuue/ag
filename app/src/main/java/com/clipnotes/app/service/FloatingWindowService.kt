package com.clipnotes.app.service

import android.app.*
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.*
import android.widget.ImageView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.clipnotes.app.NoteApplication
import com.clipnotes.app.R
import com.clipnotes.app.data.ContentType
import com.clipnotes.app.data.NoteEntity
import com.clipnotes.app.ui.MainActivity
import kotlinx.coroutines.*

class FloatingWindowService : Service() {
    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var clipboardManager: ClipboardManager? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val NOTIFICATION_ID = 2
        private const val CHANNEL_ID = "floating_window_channel"

        fun start(context: Context) {
            val intent = Intent(context, FloatingWindowService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingWindowService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        showFloatingWindow()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "浮动窗口服务",
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("剪切板笔记")
            .setContentText("浮动窗口运行中...")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .build()
    }

    private fun showFloatingWindow() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val screenWidth = resources.displayMetrics.widthPixels
        val floatingSize = screenWidth / 8

        val params = WindowManager.LayoutParams(
            floatingSize,
            floatingSize,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 100
        }

        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        floatingView = inflater.inflate(R.layout.floating_window, null)
        val iconView = floatingView?.findViewById<ImageView>(R.id.floatingIcon)

        iconView?.apply {
            setOnTouchListener(object : View.OnTouchListener {
                private var initialX = 0
                private var initialY = 0
                private var initialTouchX = 0f
                private var initialTouchY = 0f
                private var isDragging = false
                private var downTime = 0L

                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = params.x
                            initialY = params.y
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            isDragging = false
                            downTime = System.currentTimeMillis()
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val deltaX = (event.rawX - initialTouchX).toInt()
                            val deltaY = (event.rawY - initialTouchY).toInt()
                            if (Math.abs(deltaX) > 5 || Math.abs(deltaY) > 5) {
                                isDragging = true
                                params.x = initialX + deltaX
                                params.y = initialY + deltaY
                                windowManager?.updateViewLayout(floatingView, params)
                                return true
                            }
                            return true
                        }
                        MotionEvent.ACTION_UP -> {
                            val deltaX = (event.rawX - initialTouchX).toInt()
                            val deltaY = (event.rawY - initialTouchY).toInt()
                            if (!isDragging && Math.abs(deltaX) <= 10 && Math.abs(deltaY) <= 10) {
                                mainHandler.post {
                                    Toast.makeText(this@FloatingWindowService, "📌 点击已触发", Toast.LENGTH_SHORT).show()
                                }
                                saveClipboardToNote()
                                showMainActivityBriefly()
                            }
                            return true
                        }
                    }
                    return false
                }
            })
        }

        windowManager?.addView(floatingView, params)
    }

    private fun showMainActivityBriefly() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("SHOW_BRIEFLY", true)
        }
        startActivity(intent)
    }

    private fun saveClipboardToNote() {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                android.util.Log.d("FloatingWindowService", "开始保存剪切板内容到笔记")
                
                val clip = clipboardManager?.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val text = clip.getItemAt(0).text?.toString()?.trim()
                    if (!text.isNullOrBlank()) {
                        try {
                            val context = this@FloatingWindowService
                            val app = context.applicationContext as NoteApplication
                            
                            val color = app.preferenceManager.clipboardTextColor
                            val note = NoteEntity(
                                content = text,
                                contentType = ContentType.CLIPBOARD_TEXT,
                                textColor = color
                            )
                            
                            val noteId = app.repository.insertNote(note)

                            mainHandler.post {
                                Toast.makeText(this@FloatingWindowService, "✅ 已保存: ${text.take(20)}", Toast.LENGTH_SHORT).show()
                            }
                        } catch (dbError: Exception) {
                            mainHandler.post {
                                Toast.makeText(this@FloatingWindowService, "❌ 错误: ${dbError.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        mainHandler.post {
                            Toast.makeText(this@FloatingWindowService, "❌ 剪切板为空", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    mainHandler.post {
                        Toast.makeText(this@FloatingWindowService, "❌ 剪切板为空", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    Toast.makeText(this@FloatingWindowService, "❌ 保存失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        floatingView?.let { windowManager?.removeView(it) }
        scope.cancel()
    }
}
