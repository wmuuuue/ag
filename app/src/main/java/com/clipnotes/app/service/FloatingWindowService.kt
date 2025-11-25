package com.clipnotes.app.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import com.clipnotes.app.R
import com.clipnotes.app.ui.MainActivity

class FloatingWindowService : Service() {
    private var windowManager: WindowManager? = null
    private var floatingView: View? = null

    override fun onCreate() {
        super.onCreate()
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
                val intent = Intent(this@FloatingWindowService, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                }
                startActivity(intent)
            }

            setOnTouchListener(object : View.OnTouchListener {
                private var initialX = 0
                private var initialY = 0
                private var initialTouchX = 0f
                private var initialTouchY = 0f

                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = params.x
                            initialY = params.y
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            params.x = initialX + (event.rawX - initialTouchX).toInt()
                            params.y = initialY + (event.rawY - initialTouchY).toInt()
                            windowManager?.updateViewLayout(floatingView, params)
                            return true
                        }
                    }
                    return false
                }
            })
        }

        windowManager?.addView(floatingView, params)
    }


    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        floatingView?.let { windowManager?.removeView(it) }
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
