package com.quicktodo.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {

    @Query("SELECT * FROM todos WHERE isArchived = 0 ORDER BY CASE WHEN dueDate IS NULL THEN 1 ELSE 0 END, dueDate ASC, createdAt ASC")
    fun getAllActive(): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos WHERE isDone = 0 AND isArchived = 0 ORDER BY createdAt ASC")
    fun getActiveTodos(): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos WHERE isArchived = 1 ORDER BY completedAt DESC")
    fun getArchivedTodos(): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos WHERE id = :id")
    suspend fun getTodoById(id: Long): TodoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(todo: TodoEntity): Long

    @Update
    suspend fun update(todo: TodoEntity)

    @Delete
    suspend fun delete(todo: TodoEntity)

    @Query("UPDATE todos SET isArchived = 1, completedAt = :now WHERE isDone = 1 AND isArchived = 0")
    suspend fun archiveAllCompleted(now: Long)

    @Query("UPDATE todos SET isDone = :isDone, completedAt = :completedAt, isArchived = 0 WHERE id = :id")
    suspend fun setDone(id: Long, isDone: Boolean, completedAt: Long?)

    @Query("DELETE FROM todos WHERE isArchived = 1")
    suspend fun deleteAllArchived()
}
