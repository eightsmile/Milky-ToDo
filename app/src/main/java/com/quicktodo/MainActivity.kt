package com.quicktodo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.quicktodo.ui.ArchiveScreen
import com.quicktodo.ui.theme.QuickTodoTheme
import com.quicktodo.ui.SettingsScreen
import com.quicktodo.ui.TodoScreen
import com.quicktodo.ui.TodoViewModel
import com.quicktodo.ui.VoiceInputScreen
import androidx.lifecycle.viewmodel.compose.viewModel

enum class Screen {
    TODO_LIST, VOICE_INPUT, SETTINGS, ARCHIVE
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val openVoiceFromWidget = intent.getBooleanExtra("open_voice", false)

        setContent {
            QuickTodoTheme {
                val viewModel: TodoViewModel = viewModel()
                val allActiveTodos by viewModel.allActiveTodos.collectAsState(initial = emptyList())
                val activeCount by viewModel.activeTodoCount.collectAsState(initial = 0)
                val archivedTodos by viewModel.archivedTodos.collectAsState(initial = emptyList())
                val settings = QuickTodoApp.instance.settingsDataStore
                var currentScreen by remember {
                    mutableStateOf(
                        if (openVoiceFromWidget) Screen.VOICE_INPUT else Screen.TODO_LIST
                    )
                }

                when (currentScreen) {
                    Screen.TODO_LIST -> TodoScreen(
                        todos = allActiveTodos,
                        activeCount = activeCount,
                        onAddTodo = { title, dueDate, repeat ->
                            viewModel.addTodo(title, dueDate, repeat)
                        },
                        onToggle = { id -> viewModel.toggleTodo(id) },
                        onDelete = { viewModel.deleteTodo(it) },
                        onUpdateTodo = { id, title, dueDate, repeat -> viewModel.updateTodo(id, title, dueDate, repeat) },
                        onArchiveCompleted = { viewModel.archiveCompleted() },
                        onOpenVoice = { currentScreen = Screen.VOICE_INPUT },
                        onOpenSettings = { currentScreen = Screen.SETTINGS },
                        onOpenArchive = { currentScreen = Screen.ARCHIVE },
                    )

                    Screen.VOICE_INPUT -> VoiceInputScreen(
                        onBack = { currentScreen = Screen.TODO_LIST },
                        onConfirm = { items ->
                            for (item in items) {
                                viewModel.addTodo(item.title, item.dueDate, item.repeatInterval)
                            }
                            currentScreen = Screen.TODO_LIST
                        },
                        settingsProvider = { settings }
                    )

                    Screen.SETTINGS -> SettingsScreen(
                        settings = settings,
                        onBack = { currentScreen = Screen.TODO_LIST }
                    )

                    Screen.ARCHIVE -> ArchiveScreen(
                        archivedTodos = archivedTodos,
                        onClearAll = { viewModel.deleteAllArchived() },
                        onBack = { currentScreen = Screen.TODO_LIST },
                        onRestore = { viewModel.restoreTodo(it) }
                    )
                }
            }
        }
    }
}
