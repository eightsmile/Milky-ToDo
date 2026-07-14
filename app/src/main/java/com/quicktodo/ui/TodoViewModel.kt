package com.quicktodo.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.quicktodo.data.AppDatabase
import com.quicktodo.data.TodoEntity
import com.quicktodo.data.TodoRepository
import com.quicktodo.widget.TodoWidgetProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class TodoViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: TodoRepository
    private val appContext = application

    val allActiveTodos: Flow<List<TodoEntity>>
    val activeTodos: Flow<List<TodoEntity>>
    val archivedTodos: Flow<List<TodoEntity>>
    val activeTodoCount: Flow<Int>

    init {
        val dao = AppDatabase.getInstance(appContext).todoDao()
        repository = TodoRepository(dao)
        allActiveTodos = repository.allActiveTodos
        activeTodos = repository.activeTodos
        archivedTodos = repository.archivedTodos
        activeTodoCount = repository.activeTodos.map { it.size }
    }

    private fun refreshWidget() {
        TodoWidgetProvider.refreshAll(appContext)
    }

    fun addTodo(title: String, dueDate: Long? = null, repeatInterval: String = "NONE") {
        if (title.isBlank()) return
        viewModelScope.launch {
            repository.add(title, dueDate, repeatInterval)
            refreshWidget()
        }
    }

    fun toggleTodo(id: Long, currentDone: Boolean) {
        viewModelScope.launch {
            repository.toggle(id, currentDone)
            refreshWidget()
        }
    }

    fun archiveCompleted() {
        viewModelScope.launch {
            repository.archiveAllCompleted()
            refreshWidget()
        }
    }

    fun deleteAllArchived() {
        viewModelScope.launch {
            repository.deleteAllArchived()
            refreshWidget()
        }
    }

    fun restoreTodo(todo: TodoEntity) {
        viewModelScope.launch {
            repository.restoreFromArchive(todo)
            refreshWidget()
        }
    }

    fun updateTodo(id: Long, title: String, dueDate: Long?, repeat: String) {
        viewModelScope.launch {
            val todo = com.quicktodo.data.AppDatabase.getInstance(appContext).todoDao().getTodoById(id)
            if (todo != null) {
                com.quicktodo.data.AppDatabase.getInstance(appContext).todoDao().update(
                    todo.copy(title = title.trim(), dueDate = dueDate, repeatInterval = repeat)
                )
            }
            refreshWidget()
        }
    }

    fun deleteTodo(todo: TodoEntity) {
        viewModelScope.launch {
            repository.delete(todo)
            refreshWidget()
        }
    }
}
