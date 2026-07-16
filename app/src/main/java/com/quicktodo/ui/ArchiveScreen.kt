package com.quicktodo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quicktodo.QuickTodoApp
import com.quicktodo.data.TodoEntity
import com.quicktodo.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(
    archivedTodos: List<TodoEntity>,
    onClearAll: () -> Unit,
    onBack: () -> Unit,
    onRestore: (TodoEntity) -> Unit
) {
    val syncer = QuickTodoApp.instance.obsidianSyncer
    var syncStatus by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Archive", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = {
                    if (syncer.isVaultValid() && archivedTodos.isNotEmpty()) {
                        IconButton(onClick = {
                            scope.launch {
                                val errors = withContext(Dispatchers.IO) {
                                    syncer.syncAllArchived(archivedTodos)
                                }
                                syncStatus = if (errors.isEmpty()) "Synced \u2713" else "Errors: ${errors.size}"
                            }
                        }) {
                            Icon(Icons.Default.Sync, "Sync to Obsidian",
                                tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (archivedTodos.isNotEmpty()) {
                        IconButton(onClick = onClearAll) {
                            Icon(Icons.Default.DeleteSweep, "Clear all",
                                tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (archivedTodos.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No archived items", color = TextSecondary, fontSize = 16.sp)
                    if (syncStatus.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(syncStatus, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                    }
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                Text("${archivedTodos.size} archived", fontSize = 13.sp, color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))

                LazyColumn(modifier = Modifier.weight(1f).background(MaterialTheme.colorScheme.background)) {
                    items(archivedTodos, key = { it.id }) { todo ->
                        ArchiveItem(todo = todo, onRestore = { onRestore(todo) })
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }

                // Sync status at bottom
                if (syncStatus.isNotEmpty()) {
                    Text(syncStatus, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}

@Composable
private fun ArchiveItem(todo: TodoEntity, onRestore: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(todo.title, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
            Text("Done ${todo.completedAt?.let { dateFormat.format(Date(it)) } ?: "\u2014"}",
                fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(top = 2.dp))
        }
        TextButton(onClick = onRestore) { Text("Restore", fontSize = 13.sp) }
    }
}
