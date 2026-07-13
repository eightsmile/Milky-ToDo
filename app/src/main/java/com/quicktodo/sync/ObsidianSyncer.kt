package com.quicktodo.sync

import android.content.Context
import android.content.SharedPreferences
import com.quicktodo.data.TodoEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Syncs completed todos to an Obsidian vault as .md files.
 * Future: can trigger remotely-save by writing files to the vault directory.
 */
class ObsidianSyncer(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("obsidian_sync", Context.MODE_PRIVATE)

    /** Get configured vault path from preferences */
    val vaultPath: String?
        get() = prefs.getString("vault_path", null)?.takeIf { it.isNotBlank() }

    /** Save vault path */
    fun setVaultPath(path: String) {
        prefs.edit().putString("vault_path", path).apply()
    }

    /** Check if vault path exists */
    fun isVaultValid(): Boolean {
        val path = vaultPath ?: return false
        return File(path).exists()
    }

    /**
     * Sync a completed todo as a .md file in the Obsidian vault.
     * Creates a dated file like: QuickTodo/2026-07-13-买牛奶.md
     */
    fun syncTodo(todo: TodoEntity): String? {
        val vault = vaultPath ?: return null
        val vaultDir = File(vault)
        if (!vaultDir.exists()) return "Vault directory not found: $vault"

        // Create QuickTodo subfolder
        val todoDir = File(vaultDir, "QuickTodo")
        if (!todoDir.exists()) todoDir.mkdirs()

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateStr = todo.completedAt?.let { dateFormat.format(Date(it)) }
            ?: dateFormat.format(Date())

        // Sanitize title for filename
        val safeTitle = todo.title.replace(Regex("[/\\\\:*?\"<>|]"), "").take(50)

        val fileName = "$dateStr-$safeTitle.md"
        val file = File(todoDir, fileName)

        val content = buildString {
            appendLine("---")
            appendLine("title: \"${todo.title}\"")
            appendLine("created: ${todo.createdAt?.let { dateFormat.format(Date(it)) } ?: "unknown"}")
            appendLine("completed: $dateStr")
            appendLine("source: QuickTodo")
            if (todo.dueDate != null) {
                appendLine("due: ${dateFormat.format(Date(todo.dueDate!!))}")
            }
            if (todo.repeatInterval != "NONE") {
                appendLine("repeat: ${todo.repeatInterval.lowercase()}")
            }
            appendLine("---")
            appendLine()
            appendLine("# ${todo.title}")
            appendLine()
            appendLine("Completed on $dateStr via QuickTodo.")
        }

        file.writeText(content)
        return null // null means success
    }

    /**
     * Sync all archived todos. Returns list of errors.
     */
    fun syncAllArchived(todos: List<TodoEntity>): List<String> {
        val errors = mutableListOf<String>()
        for (todo in todos) {
            val error = syncTodo(todo)
            if (error != null) errors.add(error)
        }
        return errors
    }

    /**
     * Trigger remotely-save sync.
     * Currently a placeholder - remotely-save auto-detects file changes.
     */
    fun triggerRemoteSync() {
        // Remotely-save watches the vault directory for changes.
        // Writing .md files to the vault is sufficient to trigger it.
        // Future: could call remotely-save's API if available.
    }
}
