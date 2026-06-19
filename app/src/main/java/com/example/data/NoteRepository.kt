package com.example.data

import kotlinx.coroutines.flow.Flow

class NoteRepository(private val noteDao: NoteDao) {
    val allNotes: Flow<List<Note>> = noteDao.getAllNotes()

    suspend fun getNoteById(id: Int): Note? {
        return noteDao.getNoteById(id)
    }

    suspend fun getNoteByNoteId(noteId: String): Note? {
        return noteDao.getNoteByNoteId(noteId)
    }

    suspend fun getAllNotesDirect(): List<Note> {
        return noteDao.getAllNotesDirect()
    }

    suspend fun insert(note: Note): Long {
        val updatedNote = note.copy(
            updatedAt = System.currentTimeMillis()
        )
        return noteDao.insertNote(updatedNote)
    }

    suspend fun update(note: Note) {
        val updatedNote = note.copy(
            updatedAt = System.currentTimeMillis()
        )
        noteDao.updateNote(updatedNote)
    }

    suspend fun delete(note: Note) {
        val softDeletedNote = note.copy(
            deletedAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        noteDao.updateNote(softDeletedNote)
    }

    suspend fun deleteById(id: Int) {
        val note = noteDao.getNoteById(id)
        if (note != null) {
            delete(note)
        }
    }

    suspend fun deleteNoteByNoteId(noteId: String) {
        val note = noteDao.getNoteByNoteId(noteId)
        if (note != null) {
            delete(note)
        }
    }

    suspend fun hardDeleteNoteByNoteId(noteId: String) {
        noteDao.deleteNoteByNoteId(noteId)
    }
}
