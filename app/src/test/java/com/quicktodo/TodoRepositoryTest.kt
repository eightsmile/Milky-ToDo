package com.quicktodo

import com.quicktodo.data.TodoDao
import com.quicktodo.data.TodoEntity
import com.quicktodo.data.TodoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class TodoRepositoryTest {
    private class FakeTodoDao : TodoDao {
        private val rows = LinkedHashMap<Long, TodoEntity>()
        private var nextId = 1L
        private val flow = MutableStateFlow<List<TodoEntity>>(emptyList())

        val allRows: List<TodoEntity> get() = rows.values.toList()

        override fun getAllActive(): Flow<List<TodoEntity>> = flow
        override fun getArchivedTodos(): Flow<List<TodoEntity>> = flow
        override suspend fun getTodoById(id: Long): TodoEntity? = rows[id]

        override suspend fun insert(todo: TodoEntity): Long {
            val id = if (todo.id == 0L) nextId++ else todo.id
            rows[id] = todo.copy(id = id)
            flow.value = rows.values.toList()
            return id
        }

        override suspend fun update(todo: TodoEntity) {
            rows[todo.id] = todo
            flow.value = rows.values.toList()
        }

        override suspend fun delete(todo: TodoEntity) {
            rows.remove(todo.id)
            flow.value = rows.values.toList()
        }

        override suspend fun archiveAllCompleted(now: Long) {
            rows.replaceAll { _, todo ->
                if (todo.isDone && !todo.isArchived) todo.copy(isArchived = true, completedAt = now) else todo
            }
            flow.value = rows.values.toList()
        }

        override suspend fun setDone(id: Long, isDone: Boolean, completedAt: Long?) {
            rows[id]?.let { rows[id] = it.copy(isDone = isDone, completedAt = completedAt, isArchived = false) }
            flow.value = rows.values.toList()
        }

        override suspend fun deleteAllArchived() {
            rows.entries.removeIf { it.value.isArchived }
            flow.value = rows.values.toList()
        }

        override suspend fun getMaxSortOrder(): Long {
            return rows.values.maxOfOrNull { it.sortOrder } ?: 0L
        }

        override suspend fun updateSortOrder(id: Long, sortOrder: Long) {
            rows[id]?.let { rows[id] = it.copy(sortOrder = sortOrder) }
            flow.value = rows.values.sortedWith(compareBy<TodoEntity> { it.sortOrder }.thenBy { it.createdAt })
        }

        override suspend fun updateManualOrder(id: Long, manualOrder: Long?) {
            rows[id]?.let { rows[id] = it.copy(manualOrder = manualOrder) }
            flow.value = rows.values.sortedWith(compareBy<TodoEntity> { it.manualOrder == null }.thenBy { it.manualOrder ?: Long.MAX_VALUE }.thenBy { it.dueDate ?: Long.MAX_VALUE }.thenBy { it.createdAt })
        }
    }

    private fun date(year: Int, month: Int, day: Int): Long {
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    @Test
    fun toggleRecurringTodoMarksDoneAndCreatesNextOccurrence() = runTest {
        val dao = FakeTodoDao()
        val repo = TodoRepository(dao)
        val firstDue = date(2026, 7, 15)
        val id = repo.add("weekly report", firstDue, "WEEKLY")

        repo.toggle(id)

        val rows = dao.allRows.sortedBy { it.id }
        assertEquals(2, rows.size)
        assertTrue(rows[0].isDone)
        assertNotNull(rows[0].completedAt)
        assertFalse(rows[1].isDone)
        assertEquals("weekly report", rows[1].title)
        assertEquals("WEEKLY", rows[1].repeatInterval)
        assertEquals(date(2026, 7, 22), rows[1].dueDate)
    }

    @Test
    fun updateOrderPersistsManualOrdering() = runTest {
        val dao = FakeTodoDao()
        val repo = TodoRepository(dao)
        val first = repo.add("first")
        val second = repo.add("second")
        val third = repo.add("third")

        repo.updateOrder(listOf(third, first, second))

        assertEquals(0L, dao.getTodoById(third)!!.manualOrder)
        assertEquals(1L, dao.getTodoById(first)!!.manualOrder)
        assertEquals(2L, dao.getTodoById(second)!!.manualOrder)
    }

    @Test
    fun updateTodoUpdatesTitleDateAndRepeatInOneWrite() = runTest {
        val dao = FakeTodoDao()
        val repo = TodoRepository(dao)
        val id = repo.add("old", null, "NONE")
        val due = date(2026, 8, 1)

        repo.updateTodo(id, " new title ", due, "MONTHLY")

        val updated = dao.getTodoById(id)!!
        assertEquals("new title", updated.title)
        assertEquals(due, updated.dueDate)
        assertEquals("MONTHLY", updated.repeatInterval)
    }
}
