package com.clipnotes.app.ui

import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.clipnotes.app.NoteApplication
import com.clipnotes.app.databinding.ActivitySettingsBinding
import com.clipnotes.app.service.ClipboardMonitorService
import com.clipnotes.app.service.FloatingWindowService
import com.clipnotes.app.service.NotesFloatingWindowService

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "设置"

        val app = application as NoteApplication
        
        updateColorPreview()
        
        binding.switchClipboardMonitor.isChecked = app.preferenceManager.isClipboardMonitoringEnabled
        
        binding.switchClipboardMonitor.setOnCheckedChangeListener { _, isChecked ->
            app.preferenceManager.isClipboardMonitoringEnabled = isChecked
            if (isChecked) {
                ClipboardMonitorService.start(this)
                FloatingWindowService.start(this)
            } else {
                ClipboardMonitorService.stop(this)
                FloatingWindowService.stop(this)
            }
        }

        binding.btnClipboardColor.setOnClickListener {
            showColorPicker(true)
        }

        binding.btnUserInputColor.setOnClickListener {
            showColorPicker(false)
        }

        binding.btnOpenNotesWindow.setOnClickListener {
            NotesFloatingWindowService.start(this)
            Toast.makeText(this, "笔记浮窗已打开", Toast.LENGTH_SHORT).show()
        }

        binding.btnCloseNotesWindow.setOnClickListener {
            NotesFloatingWindowService.stop(this)
            Toast.makeText(this, "笔记浮窗已关闭", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateColorPreview() {
        val app = application as NoteApplication
        binding.viewClipboardColorPreview.setBackgroundColor(app.preferenceManager.clipboardTextColor)
        binding.viewUserInputColorPreview.setBackgroundColor(app.preferenceManager.userInputTextColor)
    }

    private fun showColorPicker(isClipboardColor: Boolean) {
        val colors = arrayOf(
            "黑色" to Color.BLACK,
            "蓝色" to Color.BLUE,
            "红色" to Color.RED,
            "绿色" to Color.GREEN,
            "黄色" to Color.YELLOW,
            "紫色" to Color.MAGENTA,
            "青色" to Color.CYAN,
            "灰色" to Color.GRAY
        )

        val colorNames = colors.map { it.first }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(if (isClipboardColor) "选择剪贴板文字颜色" else "选择用户输入文字颜色")
            .setItems(colorNames) { _, which ->
                val color = colors[which].second
                val app = application as NoteApplication
                
                if (isClipboardColor) {
                    app.preferenceManager.clipboardTextColor = color
                } else {
                    app.preferenceManager.userInputTextColor = color
                }
                
                updateColorPreview()
                // 已更新
            }
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
