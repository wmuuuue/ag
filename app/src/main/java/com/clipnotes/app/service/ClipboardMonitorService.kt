package com.clipnotes.app.service

import android.app.*
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.clipnotes.app.NoteApplication
import com.clipnotes.app.R
import com.clipnotes.app.data.ContentType
import com.clipnotes.app.data.NoteEntity
import com.clipnotes.app.ui.MainActivity
import com.clipnotes.app.utils.LoggerUtil
import kotlinx.coroutines.*

class ClipboardMonitorService : Service() {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var clipboardManager: ClipboardManager? = null
    private var lastClipboardText: String? = null
    private var isMonitoringPaused = false
    private var pollingJob: Job? = null
    private val recentlySavedContents = LinkedHashSet<String>()
    private val MAX_SAVED_CACHE = 50

    override fun onCreate() {
        super.onCreate()
        LoggerUtil.log("ClipboardMonitorService 已启动")
        try {
            clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            LoggerUtil.log("clipboardManager 获取成功: ${clipboardManager != null}")
        } catch (e: Exception) {
            LoggerUtil.logError("初始化 clipboardManager 失败", e)
        }

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        startPolling()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE_MONITORING -> {
                LoggerUtil.log("暂停剪贴板监控")
                pauseMonitoring()
            }
            ACTION_RESUME_MONITORING -> {
                LoggerUtil.log("恢复剪贴板监控")
                resumeMonitoring()
            }
            ACTION_STOP_SERVICE -> {
                LoggerUtil.log("停止 ClipboardMonitorService")
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startPolling() {
        LoggerUtil.log("启动剪贴板轮询...")
        pollingJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                try {
                    if (!isMonitoringPaused) {
                        checkClipboard()
                    }
                    delay(200) // 每 200ms 检查一次
                } catch (e: Exception) {
                    LoggerUtil.logError("轮询异常", e)
                    delay(1000)
                }
            }
        }
    }

    private fun checkClipboard() {
        try {
            val clip = clipboardManager?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString()?.trim()
                if (!text.isNullOrBlank()) {
                    // 三层去重机制：
                    // 1. 与上一次剪贴板内容比较
                    val normalizedNew = text.trim()
                    val normalizedOld = lastClipboardText?.trim()
                    
                    if (normalizedNew != normalizedOld) {
                        // 2. 检查是否已在最近保存的列表中
                        if (!recentlySavedContents.contains(normalizedNew)) {
                            lastClipboardText = text
                            LoggerUtil.log("✓ 剪贴板捕获: $text")
                            saveToDatabase(text)
                        } else {
                            LoggerUtil.log("⚠ 重复内容，跳过: $text")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            LoggerUtil.logError("读取剪贴板异常", e)
        }
    }

    private fun saveToDatabase(text: String) {
        scope.launch(Dispatchers.IO) {
            try {
                LoggerUtil.log("开始保存笔记...")
                val app = application as NoteApplication
                val color = app.preferenceManager.clipboardTextColor
                val note = NoteEntity(
                    content = text,
                    contentType = ContentType.CLIPBOARD_TEXT,
                    textColor = color
                )
                app.repository.insertNote(note)
                
                // 添加到最近保存的内容缓存
                val normalizedText = text.trim()
                recentlySavedContents.add(normalizedText)
                
                // 如果缓存超过最大值，移除最早的
                if (recentlySavedContents.size > MAX_SAVED_CACHE) {
                    val iterator = recentlySavedContents.iterator()
                    if (iterator.hasNext()) {
                        iterator.next()
                        iterator.remove()
                    }
                }
                
                LoggerUtil.log("✓ 笔记已保存: $text (缓存: ${recentlySavedContents.size}/$MAX_SAVED_CACHE)")
            } catch (e: Exception) {
                LoggerUtil.logError("保存笔记异常", e)
            }
        }
    }

    fun pauseMonitoring() {
        isMonitoringPaused = true
        LoggerUtil.log("监控已暂停")
    }

    fun resumeMonitoring() {
        isMonitoringPaused = false
        LoggerUtil.log("监控已恢复")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "剪贴板监听服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "监听剪贴板变化"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("剪贴板笔记")
            .setContentText("剪切板监控中")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        pollingJob?.cancel()
        scope.cancel()
    }

    companion object {
        private const val CHANNEL_ID = "clipboard_monitor_channel"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_PAUSE_MONITORING = "com.clipnotes.app.PAUSE_MONITORING"
        const val ACTION_RESUME_MONITORING = "com.clipnotes.app.RESUME_MONITORING"
        const val ACTION_STOP_SERVICE = "com.clipnotes.app.STOP_SERVICE"

        fun start(context: Context) {
            val intent = Intent(context, ClipboardMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun pause(context: Context) {
            val intent = Intent(context, ClipboardMonitorService::class.java).apply {
                action = ACTION_PAUSE_MONITORING
            }
            context.startService(intent)
        }

        fun resume(context: Context) {
            val intent = Intent(context, ClipboardMonitorService::class.java).apply {
                action = ACTION_RESUME_MONITORING
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ClipboardMonitorService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }
    }
}
