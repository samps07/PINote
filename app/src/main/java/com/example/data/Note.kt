package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val category: String = "General",
    val colorIndex: Int = 0,
    val isPinned: Boolean = false,
    val imageUri: String? = null,
    val isTaskList: Boolean = false,
    val webUrl: String? = null,
    val isScheduled: Boolean = false,
    val scheduledTime: Long? = null,
    val repeatFrequency: String? = null,
    val position: Int = 0,
    val tags: String? = null,
    val autoTags: String? = null,
    val attachmentUri: String? = null,
    val noteId: String = java.util.UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val attachmentsJson: String? = null
) {
    val imageUris: List<String>
        get() = if (imageUri.isNullOrEmpty()) emptyList() else imageUri.split("|").filter { it.trim().isNotEmpty() }

    val tagList: List<String>
        get() = if (tags.isNullOrEmpty()) emptyList() else tags.split("|").filter { it.trim().isNotEmpty() }

    val autoTagList: List<String>
        get() = if (autoTags.isNullOrEmpty()) emptyList() else autoTags.split("|").filter { it.trim().isNotEmpty() }
}

data class TaskItem(
    val text: String,
    val isChecked: Boolean
)

fun parseTaskItems(content: String): List<TaskItem> {
    if (content.isEmpty()) return emptyList()
    return content.split("\n").mapNotNull { line ->
        if (line.isBlank()) null
        else if (line.startsWith("[x] ")) {
            TaskItem(line.substring(4), true)
        } else if (line.startsWith("[ ] ")) {
            TaskItem(line.substring(4), false)
        } else {
            TaskItem(line, false)
        }
    }
}

fun serializeTaskItems(items: List<TaskItem>): String {
    return items.joinToString("\n") { item ->
        val prefix = if (item.isChecked) "[x] " else "[ ] "
        "$prefix${item.text}"
    }
}
