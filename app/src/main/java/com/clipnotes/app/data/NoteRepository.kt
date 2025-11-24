package com.clipnotes.app.data

import kotlinx.coroutines.flow.Flow

class NoteRepository(private val noteDao: NoteDao, private val deviceDao: PairedDeviceDao) {
    
    fun getAllNotes(): Flow<List<NoteEntity>> = noteDao.getAllNotes()
    
    suspend fun getNoteById(id: Long): NoteEntity? = noteDao.getNoteById(id)
    
    suspend fun insertNote(note: NoteEntity): Long = noteDao.insertNote(note)
    
    suspend fun updateNote(note: NoteEntity) = noteDao.updateNote(note)
    
    suspend fun deleteNote(note: NoteEntity) = noteDao.deleteNote(note)
    
    suspend fun deleteAllNotes() = noteDao.deleteAllNotes()
    
    suspend fun getNotesCount(): Int = noteDao.getNotesCount()
    
    fun getAllPairedDevices(): Flow<List<PairedDeviceEntity>> = deviceDao.getAllPairedDevices()
    
    suspend fun getDeviceById(deviceId: String): PairedDeviceEntity? = 
        deviceDao.getDeviceById(deviceId)
    
    suspend fun insertOrUpdateDevice(device: PairedDeviceEntity) = 
        deviceDao.insertOrUpdateDevice(device)
    
    suspend fun deleteDevice(device: PairedDeviceEntity) = deviceDao.deleteDevice(device)
    
    suspend fun updateLastConnected(deviceId: String, timestamp: Long) = 
        deviceDao.updateLastConnected(deviceId, timestamp)
}
