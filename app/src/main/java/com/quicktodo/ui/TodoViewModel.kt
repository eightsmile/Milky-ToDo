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
    val archivedTodos: Flow<List<TodoEntity>>
    val activeTodoCount: Flow<Int>

    init {
        val dao = AppDatabase.getInstance(appContext).todoDao()
        repository = TodoRepository(dao)
        allActiveTodos = repository.allActiveTodos
        archivedTodos = repository.archivedTodos
        activeTodoCount = repository.allActiveTodos.map { todos -> todos.count { !it.isDone } }
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

    fun toggleTodo(id: Long) {
        viewModelScope.launch {
            repository.toggle(id)
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
            repository.updateTodo(id, title, dueDate, repeat)
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
