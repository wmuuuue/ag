package com.clipnotes.app.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.clipnotes.app.NoteApplication
import com.clipnotes.app.data.ContentType
import com.clipnotes.app.data.NoteEntity
import kotlinx.coroutines.*

class ClipboardAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var clipboardManager: ClipboardManager? = null
    private var lastClipboardText: String? = null
    private val recentlySavedContents = LinkedHashSet<String>()
    private val MAX_SAVED_CACHE = 50

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 无障碍事件处理 - 我们主要使用定时轮询方式
        // 这里保留以满足接口要求
    }

    override fun onInterrupt() {
        // 服务中断处理
    }

    override fun onCreate() {
        super.onCreate()
        try {
            clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        } catch (e: Exception) {
        }
        
        // 启动剪贴板监听
        startMonitoring()
    }

    private fun startMonitoring() {
        scope.launch {
            while (isActive) {
                try {
                    checkClipboardContent()
                    delay(500) // 每500ms检查一次，比标准服务更频繁
                } catch (e: Exception) {
                }
            }
        }
    }

    private fun checkClipboardContent() {
        try {
            val clip = clipboardManager?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val clipText = clip.getItemAt(0).coerceToText(this@ClipboardAccessibilityService).toString()
                
                // 检查是否为新内容且未被保存过
                if (clipText.isNotEmpty() && clipText != lastClipboardText && 
                    !recentlySavedContents.contains(clipText)) {
                    
                    lastClipboardText = clipText
                    recentlySavedContents.add(clipText)
                    
                    // 控制缓存大小
                    if (recentlySavedContents.size > MAX_SAVED_CACHE) {
                        recentlySavedContents.remove(recentlySavedContents.first())
                    }
                    
                    saveToDatabase(clipText)
                }
            }
        } catch (e: Exception) {
        }
    }

    private fun saveToDatabase(text: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val app = NoteApplication.instance
                val color = app.preferenceManager.clipboardTextColor
                val note = NoteEntity(
                    content = text,
                    contentType = ContentType.CLIPBOARD_TEXT,
                    textColor = color
                )
                app.repository.insertNote(note)
            } catch (e: Exception) {
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    companion object {
        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            return try {
                val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) 
                    as android.view.accessibility.AccessibilityManager
                accessibilityManager.isEnabled && isClipboardAccessibilityServiceEnabled(context)
            } catch (e: Exception) {
                false
            }
        }

        private fun isClipboardAccessibilityServiceEnabled(context: Context): Boolean {
            return try {
                val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) 
                    as android.view.accessibility.AccessibilityManager
                val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(-1)
                enabledServices.any { it.id.contains("com.clipnotes.app") && it.id.contains("ClipboardAccessibilityService") }
            } catch (e: Exception) {
                false
            }
        }
    }
}
