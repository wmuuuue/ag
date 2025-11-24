package com.clipnotes.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.clipnotes.app.data.ContentType
import com.clipnotes.app.data.NoteEntity
import com.clipnotes.app.databinding.ItemNoteBinding
import java.text.SimpleDateFormat
import java.util.*

class NotesAdapter(
    private val onNoteClick: (NoteEntity) -> Unit,
    private val onNoteLongClick: (NoteEntity) -> Unit,
    private val onAudioClick: (String) -> Unit
) : ListAdapter<NoteEntity, NotesAdapter.NoteViewHolder>(NoteDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val binding = ItemNoteBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return NoteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class NoteViewHolder(
        private val binding: ItemNoteBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(note: NoteEntity) {
            binding.apply {
                textContent.text = note.content
                textContent.setTextColor(note.textColor)
                
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                textTimestamp.text = dateFormat.format(Date(note.timestamp))

                when (note.contentType) {
                    ContentType.CLIPBOARD_TEXT -> {
                        textType.text = "📋 剪贴板"
                    }
                    ContentType.USER_INPUT_TEXT -> {
                        textType.text = "✏️ 手动输入"
                    }
                    ContentType.AUDIO_RECORDING -> {
                        textType.text = "🎤 录音"
                        root.setOnClickListener {
                            note.audioFilePath?.let { path -> onAudioClick(path) }
                        }
                    }
                }

                if (note.contentType != ContentType.AUDIO_RECORDING) {
                    root.setOnClickListener {
                        onNoteClick(note)
                    }
                }

                root.setOnLongClickListener {
                    onNoteLongClick(note)
                    true
                }
            }
        }
    }

    private class NoteDiffCallback : DiffUtil.ItemCallback<NoteEntity>() {
        override fun areItemsTheSame(oldItem: NoteEntity, newItem: NoteEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: NoteEntity, newItem: NoteEntity): Boolean {
            return oldItem == newItem
        }
    }
}
