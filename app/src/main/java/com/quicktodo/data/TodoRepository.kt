package com.quicktodo.data

import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class TodoRepository(private val todoDao: TodoDao) {

    val allActiveTodos: Flow<List<TodoEntity>> = todoDao.getAllActive()
    val archivedTodos: Flow<List<TodoEntity>> = todoDao.getArchivedTodos()

    suspend fun add(title: String, dueDate: Long? = null, repeatInterval: String = "NONE"): Long {
        // Auto-set dueDate for recurring todos if not explicitly set
        val effectiveDueDate = if (dueDate == null && repeatInterval != "NONE") {
            calculateNextDueDate(System.currentTimeMillis(), repeatInterval)
        } else {
            dueDate
        }
        val nextOrder = todoDao.getMaxSortOrder() + 1
        return todoDao.insert(
            TodoEntity(
                title = title.trim(),
                dueDate = effectiveDueDate,
                repeatInterval = repeatInterval,
                sortOrder = nextOrder,
                manualOrder = null
            )
        )
    }

    suspend fun toggle(id: Long) {
        val todo = todoDao.getTodoById(id) ?: return
        if (todo.isDone) {
            todoDao.setDone(id, false, null)
        } else {
            val now = System.currentTimeMillis()
            todoDao.setDone(id, true, now)

            if (todo.repeatInterval != "NONE") {
                val nextDueDate = calculateNextDueDate(todo.dueDate ?: now, todo.repeatInterval)
                todoDao.insert(
                    TodoEntity(
                        title = todo.title,
                        dueDate = nextDueDate,
                        repeatInterval = todo.repeatInterval,
                        sortOrder = todoDao.getMaxSortOrder() + 1,
                        manualOrder = null
                    )
                )
            }
        }
    }

    suspend fun archiveAllCompleted() {
        todoDao.archiveAllCompleted(System.currentTimeMillis())
    }

    suspend fun deleteAllArchived() {
        todoDao.deleteAllArchived()
    }

    suspend fun restoreFromArchive(todo: TodoEntity) {
        todoDao.update(todo.copy(isArchived = false, isDone = false, completedAt = null))
    }

    suspend fun delete(todo: TodoEntity) {
        todoDao.delete(todo)
    }


    suspend fun updateTodo(id: Long, title: String, dueDate: Long?, repeatInterval: String) {
        val todo = todoDao.getTodoById(id) ?: return
        todoDao.update(
            todo.copy(
                title = title.trim(),
                dueDate = dueDate,
                repeatInterval = repeatInterval
            )
        )
    }

    suspend fun updateOrder(ids: List<Long>) {
        ids.forEachIndexed { index, id ->
            todoDao.updateManualOrder(id, index.toLong())
        }
    }

    private fun calculateNextDueDate(currentDueDate: Long, interval: String): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = currentDueDate
            when (interval) {
                "DAILY" -> add(Calendar.DAY_OF_YEAR, 1)
                "WEEKLY" -> add(Calendar.WEEK_OF_YEAR, 1)
                "MONTHLY" -> add(Calendar.MONTH, 1)
            }
        }
        return cal.timeInMillis
    }
}
