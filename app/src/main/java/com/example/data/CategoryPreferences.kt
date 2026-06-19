package com.example.data

import android.content.Context

object CategoryPrefs {
    private const val PREF_NAME = "category_prefs"
    private const val KEY_CATEGORIES = "categories_list"
    private const val KEY_TAGS = "tags_list"

    fun getCategories(context: Context): List<String> {
        val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_CATEGORIES, null)
        if (saved.isNullOrEmpty()) {
            return listOf("General", "Personal", "Work", "Ideas", "Reminders")
        }
        return saved.split("|||").filter { it.isNotEmpty() }
    }

    fun saveCategories(context: Context, categories: List<String>) {
        val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CATEGORIES, categories.joinToString("|||")).apply()
    }

    fun getTags(context: Context): List<String> {
        val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_TAGS, null)
        if (saved.isNullOrEmpty()) {
            return listOf("Urgent", "Reference", "School", "Finance", "Family")
        }
        return saved.split("|||").filter { it.isNotEmpty() }
    }

    fun saveTags(context: Context, tags: List<String>) {
        val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_TAGS, tags.joinToString("|||")).apply()
    }
}
