package com.example.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.Note
import com.example.data.NoteRepository
import com.example.data.CategoryPrefs
import com.example.sync.SyncManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class SortOrder {
    LAST_UPDATED,
    FIRST_UPDATED
}

class NoteViewModel(val repository: NoteRepository, private val context: Context) : ViewModel() {

    val searchQuery = MutableStateFlow("")
    val selectedCategory = MutableStateFlow<String?>(null)
    val isGridLayout = MutableStateFlow(true)
    val sortOrder = MutableStateFlow(SortOrder.LAST_UPDATED)
    val selectedTagFilter = MutableStateFlow<String?>(null)

    val notesState: StateFlow<List<Note>> = combine(
        repository.allNotes,
        searchQuery,
        selectedCategory,
        sortOrder,
        selectedTagFilter
    ) { allNotes, query, category, order, tagFilter ->
        val filtered = allNotes.filter { note ->
            val matchesQuery = note.title.contains(query, ignoreCase = true) ||
                    note.content.contains(query, ignoreCase = true)
            val matchesCategory = category == null || note.category.equals(category, ignoreCase = true)
            val matchesTag = tagFilter == null || note.tagList.any { it.equals(tagFilter, ignoreCase = true) } || note.autoTagList.any { it.equals(tagFilter, ignoreCase = true) }
            matchesQuery && matchesCategory && matchesTag
        }
        if (order == SortOrder.LAST_UPDATED) {
            filtered.sortedWith(compareByDescending<Note> { it.isPinned }.thenBy { it.position }.thenByDescending { it.timestamp })
        } else {
            filtered.sortedWith(compareByDescending<Note> { it.isPinned }.thenBy { it.position }.thenBy { it.timestamp })
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _categories = MutableStateFlow<List<String>>(emptyList())
    val categories: StateFlow<List<String>> = _categories.asStateFlow()

    private val _tags = MutableStateFlow<List<String>>(emptyList())
    val tagsState: StateFlow<List<String>> = _tags.asStateFlow()

    init {
        _categories.value = CategoryPrefs.getCategories(context)
        _tags.value = CategoryPrefs.getTags(context)
    }

    fun addTag(tagName: String) {
        val trimmed = tagName.trim().removePrefix("#").trim()
        if (trimmed.isEmpty()) return
        val currentList = _tags.value.toMutableList()
        if (!currentList.contains(trimmed)) {
            currentList.add(trimmed)
            _tags.value = currentList
            CategoryPrefs.saveTags(context, currentList)
        }
    }

    fun addCategory(categoryName: String) {
        val trimmed = categoryName.trim()
        if (trimmed.isEmpty()) return
        val currentList = _categories.value.toMutableList()
        if (!currentList.contains(trimmed)) {
            currentList.add(trimmed)
            _categories.value = currentList
            CategoryPrefs.saveCategories(context, currentList)
        }
    }

    fun saveNewCategoryOrder(newOrder: List<String>) {
        _categories.value = newOrder
        CategoryPrefs.saveCategories(context, newOrder)
    }

    fun deleteCategory(categoryName: String) {
        val currentList = _categories.value.toMutableList()
        if (currentList.remove(categoryName)) {
            _categories.value = currentList
            CategoryPrefs.saveCategories(context, currentList)
            if (selectedCategory.value == categoryName) {
                selectedCategory.value = null
            }
        }
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setSelectedCategory(category: String?) {
        selectedCategory.value = category
    }

    fun setSelectedTagFilter(tag: String?) {
        selectedTagFilter.value = tag
    }

    fun toggleLayout() {
        isGridLayout.value = !isGridLayout.value
    }

    fun insertNote(
        title: String,
        content: String,
        category: String,
        colorIndex: Int,
        isPinned: Boolean,
        imageUri: String? = null,
        isTaskList: Boolean = false,
        webUrl: String? = null,
        isScheduled: Boolean = false,
        scheduledTime: Long? = null,
        repeatFrequency: String? = null,
        tags: String? = null,
        autoTags: String? = null,
        attachmentUri: String? = null,
        onSuccess: ((Note) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val note = Note(
                title = title,
                content = content,
                category = category.trim().ifEmpty { "General" },
                colorIndex = colorIndex,
                isPinned = isPinned,
                timestamp = System.currentTimeMillis(),
                imageUri = imageUri,
                isTaskList = isTaskList,
                webUrl = webUrl,
                isScheduled = isScheduled,
                scheduledTime = scheduledTime,
                repeatFrequency = repeatFrequency,
                tags = tags,
                autoTags = autoTags,
                attachmentUri = attachmentUri
            )
            val generatedId = repository.insert(note)
            val insertedNote = note.copy(id = generatedId.toInt())
            onSuccess?.invoke(insertedNote)
        }
    }

    fun updateNote(note: Note) {
        viewModelScope.launch {
            repository.update(note.copy(timestamp = System.currentTimeMillis()))
        }
    }

    fun updateNoteRaw(note: Note) {
        viewModelScope.launch {
            repository.update(note)
        }
    }

    fun updateNotePositions(newOrderList: List<Note>) {
        viewModelScope.launch {
            var changed = false
            newOrderList.forEachIndexed { index, note ->
                if (note.position != index) {
                    repository.update(note.copy(position = index))
                    changed = true
                }
            }
        }
    }

    fun togglePin(note: Note) {
        viewModelScope.launch {
            repository.update(note.copy(isPinned = !note.isPinned))
        }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch {
            repository.delete(note)
        }
    }
}

class NoteViewModelFactory(private val repository: NoteRepository, private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NoteViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return NoteViewModel(repository, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
