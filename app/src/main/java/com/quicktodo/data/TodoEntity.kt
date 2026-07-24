package com.quicktodo.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "todos",
    indices = [
        Index(value = ["isArchived", "manualOrder", "createdAt"]),
        Index(value = ["isArchived", "sortOrder", "createdAt"]),
        Index(value = ["isArchived", "dueDate", "createdAt"]),
        Index(value = ["isArchived", "completedAt"]),
        Index(value = ["isDone", "isArchived"])
    ]
)
data class TodoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val isDone: Boolean = false,
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val dueDate: Long? = null,
    val repeatInterval: String = "NONE", // NONE, DAILY, WEEKLY, MONTHLY
    val sortOrder: Long = createdAt,
    val manualOrder: Long? = null
)
