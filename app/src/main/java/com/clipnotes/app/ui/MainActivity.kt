package com.clipnotes.app.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.clipnotes.app.NoteApplication
import com.clipnotes.app.R
import com.clipnotes.app.data.ContentType
import com.clipnotes.app.data.NoteEntity
import com.clipnotes.app.data.NoteRepository
import com.clipnotes.app.databinding.ActivityMainBinding
import com.clipnotes.app.service.ClipboardMonitorService
import com.clipnotes.app.service.FloatingWindowService
import com.clipnotes.app.service.NetworkDiscoveryService
import com.clipnotes.app.utils.AudioRecorderManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONArray

private const val TAG = "MainActivity"

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var notesAdapter: NotesAdapter
    private lateinit var audioRecorder: AudioRecorderManager
    private lateinit var networkService: NetworkDiscoveryService
    private var isRecording = false
    
    private val viewModel: MainViewModel by viewModels {
        val app = application as NoteApplication
        MainViewModelFactory(app.repository, app.preferenceManager)
    }

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            toggleRecording()
        } else {
            Toast.makeText(this, "需要录音权限", Toast.LENGTH_SHORT).show()
        }
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            startServices()
        } else {
            Toast.makeText(this, "需要悬浮窗权限", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            Log.d(TAG, "onCreate: Starting MainActivity initialization")
            
            Log.d(TAG, "onCreate: Inflating binding")
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)
            Log.d(TAG, "onCreate: Content view set")

            setSupportActionBar(binding.toolbar)
            Log.d(TAG, "onCreate: Action bar set")

            audioRecorder = AudioRecorderManager(this)
            networkService = NetworkDiscoveryService(this)
            Log.d(TAG, "onCreate: Services initialized")

            setupRecyclerView()
            Log.d(TAG, "onCreate: RecyclerView setup complete")
            
            setupClickListeners()
            Log.d(TAG, "onCreate: Click listeners setup complete")
            
            requestPermissions()
            Log.d(TAG, "onCreate: Permissions requested")
            
            observeNotes()
            Log.d(TAG, "onCreate: Notes observer set up")
            
            ClipboardMonitorService.pause(this)
            Log.d(TAG, "onCreate: ClipboardMonitorService paused")

            networkService.startServer(8888) { notesJson, callback ->
                showReceiveDialog(notesJson, callback)
            }
            Log.d(TAG, "onCreate: Network server started")
            Log.d(TAG, "onCreate: MainActivity initialization complete")
            
        } catch (e: Exception) {
            Log.e(TAG, "onCreate: Critical error during initialization", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    private fun setupRecyclerView() {
        notesAdapter = NotesAdapter(
            onNoteClick = { note ->
                copyToClipboard(note.content)
            },
            onNoteLongClick = { note ->
                showNoteOptionsDialog(note)
            },
            onAudioClick = { audioPath ->
                audioRecorder.playAudio(audioPath) {
                    notesAdapter.notifyDataSetChanged()
                }
            }
        )
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = notesAdapter
        }
    }

    private fun setupClickListeners() {
        binding.fabAddNote.setOnClickListener {
            showAddNoteDialog()
        }

        binding.fabRecord.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                toggleRecording()
            }
        }
    }

    private fun toggleRecording() {
        if (isRecording) {
            val (file, duration) = audioRecorder.stopRecording()
            file?.let {
                saveAudioNote(it.absolutePath, duration)
            }
            binding.fabRecord.setImageResource(R.drawable.ic_mic)
            isRecording = false
        } else {
            audioRecorder.startRecording()
            binding.fabRecord.setImageResource(R.drawable.ic_stop)
            isRecording = true
        }
    }

    private fun saveAudioNote(path: String, duration: Long) {
        viewModel.insertNote(
            "录音 (${duration / 1000}秒)",
            ContentType.AUDIO_RECORDING,
            path,
            duration
        )
    }

    private fun showAddNoteDialog() {
        val builder = AlertDialog.Builder(this)
        val input = android.widget.EditText(this)
        input.hint = "输入笔记内容"
        
        builder.setTitle("添加笔记")
            .setView(input)
            .setPositiveButton("保存") { _, _ ->
                val text = input.text.toString()
                if (text.isNotBlank()) {
                    saveUserNote(text)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun saveUserNote(text: String) {
        viewModel.insertNote(text, ContentType.USER_INPUT_TEXT)
    }

    private fun showNoteOptionsDialog(note: NoteEntity) {
        val options = arrayOf("编辑", "删除")
        AlertDialog.Builder(this)
            .setTitle("笔记操作")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> editNote(note)
                    1 -> deleteNote(note)
                }
            }
            .show()
    }

    private fun editNote(note: NoteEntity) {
        if (note.contentType == ContentType.AUDIO_RECORDING) {
            Toast.makeText(this, "音频笔记无法编辑", Toast.LENGTH_SHORT).show()
            return
        }
        
        val builder = AlertDialog.Builder(this)
        val input = android.widget.EditText(this)
        input.setText(note.content)
        
        builder.setTitle("编辑笔记")
            .setView(input)
            .setPositiveButton("保存") { _, _ ->
                val text = input.text.toString()
                if (text.isNotBlank()) {
                    viewModel.updateNote(note.copy(content = text))
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteNote(note: NoteEntity) {
        AlertDialog.Builder(this)
            .setTitle("删除笔记")
            .setMessage("确定要删除这条笔记吗？")
            .setPositiveButton("删除") { _, _ ->
                viewModel.deleteNote(note)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("note", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }

    private fun observeNotes() {
        lifecycleScope.launch {
            viewModel.notes.collectLatest { notes ->
                notesAdapter.submitList(notes)
            }
        }
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                overlayPermissionLauncher.launch(intent)
            } else {
                startServices()
            }
        } else {
            startServices()
        }
    }

    private fun startServices() {
        ClipboardMonitorService.start(this)
        FloatingWindowService.start(this)
    }

    private fun clearAllNotes() {
        AlertDialog.Builder(this)
            .setTitle("清空笔记")
            .setMessage("确定要清空所有笔记吗？此操作无法撤销。")
            .setPositiveButton("清空") { _, _ ->
                viewModel.deleteAllNotes()
                Toast.makeText(this@MainActivity, "已清空所有笔记", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun sendNotes() {
        lifecycleScope.launch {
            networkService.discoverServices()
            
            launch {
                networkService.discoveredDevices.collect { devices ->
                    if (devices.isNotEmpty()) {
                        showDeviceSelectionDialog(devices)
                    }
                }
            }
        }
    }

    private fun showDeviceSelectionDialog(devices: List<NetworkDiscoveryService.DeviceInfo>) {
        val deviceNames = devices.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("选择设备")
            .setItems(deviceNames) { _, which ->
                val device = devices[which]
                performSendNotes(device)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun performSendNotes(device: NetworkDiscoveryService.DeviceInfo) {
        lifecycleScope.launch {
            val notesList = viewModel.getAllNotesSnapshot()
            
            val response = networkService.sendNotes(device, notesList)
            
            when (response) {
                "ACCEPTED" -> {
                    val count = notesList.size
                    Toast.makeText(
                        this@MainActivity,
                        "发送成功！共 $count 条笔记",
                        Toast.LENGTH_LONG
                    ).show()
                }
                "REJECTED" -> {
                    Toast.makeText(this@MainActivity, "对方拒绝接收", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    Toast.makeText(this@MainActivity, "发送失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showReceiveDialog(notesJson: String, callback: (Boolean) -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("接收笔记")
            .setMessage("是否接收来自其他设备的笔记？")
            .setPositiveButton("接收") { _, _ ->
                receiveNotes(notesJson)
                callback(true)
            }
            .setNegativeButton("拒绝") { _, _ ->
                callback(false)
            }
            .show()
    }

    private fun receiveNotes(notesJson: String) {
        viewModel.receiveNotes(
            notesJson,
            onSuccess = {
                Toast.makeText(this@MainActivity, "接收成功！", Toast.LENGTH_SHORT).show()
            },
            onError = {
                Toast.makeText(this@MainActivity, "接收失败", Toast.LENGTH_SHORT).show()
            }
        )
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_send -> {
                sendNotes()
                true
            }
            R.id.action_clear -> {
                clearAllNotes()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        ClipboardMonitorService.pause(this)
    }

    override fun onPause() {
        super.onPause()
        ClipboardMonitorService.resume(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        audioRecorder.release()
        networkService.stopDiscovery()
    }
}
