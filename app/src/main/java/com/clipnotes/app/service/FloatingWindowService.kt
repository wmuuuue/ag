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

    override fun onCreate() {
        super.onCreate()
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        showFloatingWindow()
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
        val floatingSize = screenWidth / 40

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

        floatingView?.findViewById<ImageView>(R.id.floatingIcon)?.apply {
            setOnClickListener {
                // 点击浮动图标，保存剪切板最新内容到笔记
                saveClipboardToNote()
            }

            setOnTouchListener(object : View.OnTouchListener {
                private var initialX = 0
                private var initialY = 0
                private var initialTouchX = 0f
                private var initialTouchY = 0f
                private var isDragging = false

                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = params.x
                            initialY = params.y
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            isDragging = false
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
                            }
                            return true
                        }
                        MotionEvent.ACTION_UP -> {
                            return isDragging  // 如果是拖动则拦截，否则允许点击
                        }
                    }
                    return false
                }
            })
        }

        windowManager?.addView(floatingView, params)
    }
    
    private fun saveClipboardToNote() {
        scope.launch(Dispatchers.IO) {
            try {
                val clip = clipboardManager?.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val text = clip.getItemAt(0).text?.toString()?.trim()
                    if (!text.isNullOrBlank()) {
                        val app = application as NoteApplication
                        val color = app.preferenceManager.clipboardTextColor
                        val note = NoteEntity(
                            content = text,
                            contentType = ContentType.CLIPBOARD_TEXT,
                            textColor = color
                        )
                        app.repository.insertNote(note)
                        
                        // 显示成功提示
                        mainHandler.post {
                            Toast.makeText(this@FloatingWindowService, "✅ 已保存到笔记", Toast.LENGTH_SHORT).show()
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

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, FloatingWindowService::class.java)
            context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingWindowService::class.java))
        }
    }
}
