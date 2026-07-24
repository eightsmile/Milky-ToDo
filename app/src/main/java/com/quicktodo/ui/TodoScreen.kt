package com.quicktodo.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quicktodo.data.TodoEntity
import com.quicktodo.ui.theme.CheckedColor
import com.quicktodo.ui.theme.TextSecondary
import com.quicktodo.util.mergeDateWithExistingTime
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TodoScreen(
    todos: List<TodoEntity>,
    activeCount: Int,
    onAddTodo: (String, Long?, String) -> Unit,
    onToggle: (Long) -> Unit,
    onDelete: (TodoEntity) -> Unit,
    onUpdateTodo: (Long, String, Long?, String) -> Unit,
    onArchiveCompleted: () -> Unit,
    onMoveTodo: (List<Long>) -> Unit,
    onOpenVoice: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenArchive: () -> Unit
) {
    var newTodoText by remember { mutableStateOf("") }
    var editingTodoId by remember { mutableStateOf<Long?>(null) }
    var editingText by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var pendingDueDate by remember { mutableStateOf<Long?>(null) }
    var pendingRepeat by remember { mutableStateOf("NONE") }
    var showOptions by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val context = LocalContext.current
    var displayTodos by remember { mutableStateOf(todos) }

    // Keep the locally reordered display list in sync with every Room update.
    // This must include isDone/completedAt changes; otherwise a successful toggle can
    // keep rendering a stale unchecked TodoEntity until another sort-related field changes.
    LaunchedEffect(todos) {
        displayTodos = todos
    }

    fun moveDisplayedTodo(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in displayTodos.indices || toIndex !in displayTodos.indices || fromIndex == toIndex) return
        val mutable = displayTodos.toMutableList()
        val moved = mutable.removeAt(fromIndex)
        mutable.add(toIndex, moved)
        displayTodos = mutable
        val touchedIds = setOf(displayTodos[toIndex].id, displayTodos[fromIndex].id)
        val manualIds = mutable.filter { it.manualOrder != null || it.id in touchedIds }.map { it.id }
        onMoveTodo(manualIds)
    }

    fun copyTodosAsMarkdown() {
        val markdown = formatTodosAsMarkdown(todos)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Milky ToDo", markdown))
        Toast.makeText(context, "Copied ${todos.size} todos as Markdown", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Milky ToDo",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (activeCount > 0) {
                            Text(
                                text = "$activeCount remaining",
                                fontSize = 14.sp,
                                color = TextSecondary
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { copyTodosAsMarkdown() }) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = "Copy todos"
                        )
                    }
                    IconButton(onClick = onOpenArchive) {
                        Icon(
                            imageVector = Icons.Outlined.Inventory2,
                            contentDescription = "Archive"
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Settings"
                        )
                    }
                }
            )
        },
        floatingActionButton = { }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // ===== Todo list (takes remaining space) =====
            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                items(displayTodos, key = { it.id }) { todo ->
                    SwipeToDismissBox(
                        modifier = Modifier.animateItemPlacement(),
                        state = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart) {
                                    scope.launch { onDelete(todo) }
                                    true
                                } else false
                            }
                        ),
                        backgroundContent = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFFF3B30))
                                    .padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Delete",
                                    tint = Color.White
                                )
                            }
                        },
                        content = {
                            TodoItem(
                                todo = todo,
                                isEditing = editingTodoId == todo.id,
                                editText = if (editingTodoId == todo.id) editingText else todo.title,
                                onToggle = { onToggle(todo.id) },
                                onEdit = {
                                    editingTodoId = todo.id
                                    editingText = todo.title
                                },
                                onEditTextChange = { editingText = it },
                                onSaveEdit = { title, dueDate, repeat ->
                                    if (title.isNotBlank()) {
                                        onUpdateTodo(todo.id, title, dueDate, repeat)
                                    }
                                    editingTodoId = null
                                },
                                onCancelEdit = { editingTodoId = null },
                                onMoveUp = {
                                    val index = displayTodos.indexOfFirst { it.id == todo.id }
                                    moveDisplayedTodo(index, index - 1)
                                },
                                onMoveDown = {
                                    val index = displayTodos.indexOfFirst { it.id == todo.id }
                                    moveDisplayedTodo(index, index + 1)
                                },
                                canMoveUp = displayTodos.indexOfFirst { it.id == todo.id } > 0,
                                canMoveDown = displayTodos.indexOfFirst { it.id == todo.id }.let { it >= 0 && it < displayTodos.lastIndex }
                            )
                        },
                        enableDismissFromStartToEnd = false,
                        enableDismissFromEndToStart = true
                    )
                }
                // ===== Empty state with icon =====
                if (displayTodos.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = TextSecondary.copy(alpha = 0.4f)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = if (activeCount == 0) "All done! 🎉" else "No todos yet",
                                color = TextSecondary,
                                fontSize = 16.sp
                            )
                            if (activeCount == 0) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "Tap + to add a new todo",
                                    color = TextSecondary.copy(alpha = 0.6f),
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
            }

            // ===== Clear Completed + Voice row =====
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val hasCompleted = todos.any { it.isDone }
                if (hasCompleted) {
                    TextButton(onClick = onArchiveCompleted) {
                        Text("Clear Completed", color = MaterialTheme.colorScheme.primary)
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                ExtendedFloatingActionButton(
                    onClick = onOpenVoice,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = "Voice input",
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Voice")
                }
            }

            // ===== Input bar at the very bottom =====
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = newTodoText,
                        onValueChange = { newTodoText = it },
                        placeholder = {
                            Text("New todo...", color = TextSecondary)
                        },
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = true
                    )

                    IconButton(onClick = { showOptions = !showOptions }) {
                        Icon(
                            imageVector = Icons.Filled.DateRange,
                            contentDescription = "Set date/repeat",
                            tint = if (showOptions) MaterialTheme.colorScheme.primary
                                   else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    if (newTodoText.isNotBlank()) {
                        TextButton(
                            onClick = {
                                onAddTodo(newTodoText, pendingDueDate, pendingRepeat)
                                newTodoText = ""
                                pendingDueDate = null
                                pendingRepeat = "NONE"
                                showOptions = false
                            }
                        ) {
                            Text("Add", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // Expandable date/repeat options
                if (showOptions) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Repeat chip (left)
                        AssistChip(
                            onClick = {
                                pendingRepeat = when (pendingRepeat) {
                                    "NONE" -> "DAILY"
                                    "DAILY" -> "WEEKLY"
                                    "WEEKLY" -> "MONTHLY"
                                    else -> "NONE"
                                }
                            },
                            label = {
                                Text(
                                    when (pendingRepeat) {
                                        "DAILY" -> "Daily"
                                        "WEEKLY" -> "Weekly"
                                        "MONTHLY" -> "Monthly"
                                        else -> "Repeat"
                                    },
                                    fontSize = 12.sp
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Filled.Loop, null, modifier = Modifier.size(14.dp))
                            },
                            shape = RoundedCornerShape(8.dp)
                        )

                        // Date chip (center)
                        AssistChip(
                            onClick = { showDatePicker = true },
                            label = {
                                Text(
                                    if (pendingDueDate != null)
                                        dateFormat.format(Date(pendingDueDate!!))
                                    else "Date",
                                    fontSize = 12.sp
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Filled.DateRange, null, modifier = Modifier.size(14.dp))
                            },
                            shape = RoundedCornerShape(8.dp)
                        )

                        // Time chip (right of date)
                        val hasCustomTime = pendingDueDate?.let {
                            val cal = Calendar.getInstance().apply { timeInMillis = it }
                            cal.get(Calendar.HOUR_OF_DAY) != 23 || cal.get(Calendar.MINUTE) != 59
                        } ?: false

                        AssistChip(
                            onClick = { showTimePicker = true },
                            label = {
                                Text(
                                    if (hasCustomTime && pendingDueDate != null)
                                        timeFormat.format(Date(pendingDueDate!!))
                                    else "Time",
                                    fontSize = 12.sp,
                                    color = if (hasCustomTime) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            shape = RoundedCornerShape(8.dp)
                        )

                        if (pendingDueDate != null || pendingRepeat != "NONE") {
                            TextButton(
                                onClick = {
                                    pendingDueDate = null
                                    pendingRepeat = "NONE"
                                },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Clear", fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // Date picker dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = pendingDueDate ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { ms ->
                        pendingDueDate = mergeDateWithExistingTime(ms, pendingDueDate)
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Time picker dialog
    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = pendingDueDate?.let {
                java.util.Calendar.getInstance().apply { timeInMillis = it }.get(java.util.Calendar.HOUR_OF_DAY)
            } ?: 12,
            initialMinute = pendingDueDate?.let {
                java.util.Calendar.getInstance().apply { timeInMillis = it }.get(java.util.Calendar.MINUTE)
            } ?: 0,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Select Time", fontWeight = FontWeight.Bold) },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    val cal = java.util.Calendar.getInstance()
                    pendingDueDate?.let { cal.timeInMillis = it }
                    cal.set(java.util.Calendar.HOUR_OF_DAY, timePickerState.hour)
                    cal.set(java.util.Calendar.MINUTE, timePickerState.minute)
                    cal.set(java.util.Calendar.SECOND, 0)
                    cal.set(java.util.Calendar.MILLISECOND, 0)
                    pendingDueDate = cal.timeInMillis
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoItem(
    todo: TodoEntity,
    isEditing: Boolean = false,
    editText: String = todo.title,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onEditTextChange: (String) -> Unit = {},
    onSaveEdit: (title: String, dueDate: Long?, repeat: String) -> Unit = { _, _, _ -> },
    onCancelEdit: () -> Unit = {},
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val isOverdue = todo.dueDate != null && todo.dueDate!! < System.currentTimeMillis() && !todo.isDone

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .then(
                if (!isEditing) Modifier.clickable(onClick = onEdit)
                else Modifier
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val circleColor by animateColorAsState(
            targetValue = if (todo.isDone) CheckedColor else MaterialTheme.colorScheme.outline,
            label = "circleColor"
        )

        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(circleColor)
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center
        ) {
            if (todo.isDone) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Done",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        if (isEditing) {
            val dateFormat = remember { SimpleDateFormat("MMM dd", Locale.getDefault()) }
            var editDueDate by remember { mutableStateOf(todo.dueDate) }
            var editRepeat by remember { mutableStateOf(todo.repeatInterval) }
            var showDatePicker by remember { mutableStateOf(false) }
            var showTimePicker by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.Center
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    // Title edit field
                    OutlinedTextField(
                        value = editText,
                        onValueChange = onEditTextChange,
                        singleLine = false,
                        minLines = 1, maxLines = 8,
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        ),
                        textStyle = LocalTextStyle.current.copy(fontSize = 16.sp),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = androidx.compose.ui.text.input.ImeAction.Done
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onDone = { }
                        )
                    )

                    // Date, repeat, time chips + Done button
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        // Compact repeat button: icon for NONE, D/W/M for active repeat intervals.
                        TextButton(
                            onClick = {
                                editRepeat = when (editRepeat) {
                                    "NONE" -> "DAILY"
                                    "DAILY" -> "WEEKLY"
                                    "WEEKLY" -> "MONTHLY"
                                    else -> "NONE"
                                }
                            },
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Icon(
                                Icons.Filled.Loop,
                                contentDescription = "Repeat",
                                tint = if (editRepeat != "NONE") MaterialTheme.colorScheme.primary else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            if (editRepeat != "NONE") {
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = when (editRepeat) {
                                        "DAILY" -> "D"
                                        "WEEKLY" -> "W"
                                        "MONTHLY" -> "M"
                                        else -> ""
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        // Date chip (center)
                        AssistChip(
                            onClick = { showDatePicker = true },
                            label = { Text(editDueDate?.let { dateFormat.format(Date(it)) } ?: "Date", fontSize = 10.sp) },
                            shape = RoundedCornerShape(6.dp)
                        )
                        // Time chip (right)
                        val hasCustomTime = editDueDate?.let {
                            val cal = java.util.Calendar.getInstance().apply { timeInMillis = it }
                            cal.get(java.util.Calendar.HOUR_OF_DAY) != 23 || cal.get(java.util.Calendar.MINUTE) != 59
                        } ?: false
                        AssistChip(
                            onClick = { showTimePicker = true },
                            label = {
                                Text(
                                    if (hasCustomTime && editDueDate != null)
                                        timeFormat.format(Date(editDueDate!!))
                                    else "Time",
                                    fontSize = 10.sp,
                                    color = if (hasCustomTime) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            shape = RoundedCornerShape(6.dp)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            onSaveEdit(editText, editDueDate, editRepeat)
                        }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)) {
                            Text("Done", fontSize = 11.sp)
                        }
                    }
                }
            }

            // Date picker dialog
            if (showDatePicker) {
                val datePickerState = rememberDatePickerState(
                    initialSelectedDateMillis = editDueDate ?: System.currentTimeMillis()
                )
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            datePickerState.selectedDateMillis?.let { ms ->
                                editDueDate = mergeDateWithExistingTime(ms, editDueDate)
                            }
                            showDatePicker = false
                        }) { Text("OK") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
                    }
                ) {
                    DatePicker(state = datePickerState)
                }
            }

            // Time picker dialog
            if (showTimePicker) {
                val timePickerState = rememberTimePickerState(
                    initialHour = editDueDate?.let {
                        java.util.Calendar.getInstance().apply { timeInMillis = it }.get(java.util.Calendar.HOUR_OF_DAY)
                    } ?: 12,
                    initialMinute = editDueDate?.let {
                        java.util.Calendar.getInstance().apply { timeInMillis = it }.get(java.util.Calendar.MINUTE)
                    } ?: 0,
                    is24Hour = true
                )
                AlertDialog(
                    onDismissRequest = { showTimePicker = false },
                    title = { Text("Select Time", fontWeight = FontWeight.Bold) },
                    text = { TimePicker(state = timePickerState) },
                    confirmButton = {
                        TextButton(onClick = {
                            val cal = java.util.Calendar.getInstance()
                            editDueDate?.let { cal.timeInMillis = it }
                            cal.set(java.util.Calendar.HOUR_OF_DAY, timePickerState.hour)
                            cal.set(java.util.Calendar.MINUTE, timePickerState.minute)
                            cal.set(java.util.Calendar.SECOND, 0)
                            cal.set(java.util.Calendar.MILLISECOND, 0)
                            editDueDate = cal.timeInMillis
                            showTimePicker = false
                        }) { Text("OK") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
                    }
                )
            }
        } else {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = todo.title,
                    fontSize = 16.sp,
                    textDecoration = if (todo.isDone) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (todo.isDone) TextSecondary else MaterialTheme.colorScheme.onSurface
                )
                if (todo.dueDate != null || todo.repeatInterval != "NONE") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        if (todo.repeatInterval != "NONE") {
                            Text(
                                text = "🔁",
                                fontSize = 10.sp
                            )
                        }
                        if (todo.dueDate != null) {
                            val hasCustomTime = with(java.util.Calendar.getInstance()) {
                                timeInMillis = todo.dueDate!!
                                get(java.util.Calendar.HOUR_OF_DAY) != 23 || get(java.util.Calendar.MINUTE) != 59
                            }
                            Text(
                                text = if (hasCustomTime)
                                    "${dateFormat.format(Date(todo.dueDate!!))} ${timeFormat.format(Date(todo.dueDate!!))}"
                                else dateFormat.format(Date(todo.dueDate!!)),
                                fontSize = 10.sp,
                                color = if (isOverdue) Color(0xFFFF3B30) else TextSecondary
                            )
                        }
                        if (todo.repeatInterval != "NONE") {
                            Text(
                                text = when (todo.repeatInterval) {
                                    "DAILY" -> "daily"
                                    "WEEKLY" -> "weekly"
                                    "MONTHLY" -> "monthly"
                                    else -> ""
                                },
                                fontSize = 10.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(6.dp))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.width(28.dp)
        ) {
            IconButton(
                onClick = onMoveUp,
                enabled = canMoveUp,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowUp,
                    contentDescription = "Move up",
                    tint = if (canMoveUp) TextSecondary else TextSecondary.copy(alpha = 0.25f),
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(
                onClick = onMoveDown,
                enabled = canMoveDown,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Move down",
                    tint = if (canMoveDown) TextSecondary else TextSecondary.copy(alpha = 0.25f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun formatTodosAsMarkdown(todos: List<TodoEntity>): String {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    return buildString {
        appendLine("# Milky ToDo")
        appendLine()
        if (todos.isEmpty()) {
            appendLine("_No todo items._")
            return@buildString
        }
        todos.forEach { todo ->
            append(if (todo.isDone) "- [x] " else "- [ ] ")
            append(todo.title)
            if (todo.dueDate != null) {
                val cal = Calendar.getInstance().apply { timeInMillis = todo.dueDate }
                val hasCustomTime = cal.get(Calendar.HOUR_OF_DAY) != 23 || cal.get(Calendar.MINUTE) != 59
                append(" 📅 ")
                append(dateFormat.format(Date(todo.dueDate)))
                if (hasCustomTime) {
                    append(" ")
                    append(timeFormat.format(Date(todo.dueDate)))
                }
            }
            if (todo.repeatInterval != "NONE") {
                append(" 🔁 ")
                append(todo.repeatInterval.lowercase(Locale.getDefault()))
            }
            appendLine()
        }
    }
}
