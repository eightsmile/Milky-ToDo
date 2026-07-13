package com.quicktodo.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quicktodo.data.SettingsDataStore
import com.quicktodo.ui.theme.TextSecondary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: com.quicktodo.data.SettingsDataStore,
    onBack: () -> Unit
) {
    var sttEndpoint by remember { mutableStateOf("") }
    var sttApiKey by remember { mutableStateOf("") }
    var sttModel by remember { mutableStateOf("") }
    var llmEndpoint by remember { mutableStateOf("") }
    var llmApiKey by remember { mutableStateOf("") }
    var llmModel by remember { mutableStateOf("") }
    var saveMessage by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        sttEndpoint = settings.sttEndpoint.first()
        sttApiKey = settings.sttApiKey.first()
        sttModel = settings.sttModel.first()
        llmEndpoint = settings.llmEndpoint.first()
        llmApiKey = settings.llmApiKey.first()
        llmModel = settings.llmModel.first()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Settings", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // === STT Section ===
            Text(
                text = "Speech-to-Text API",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
            Text(
                text = "Voice recording → text transcription",
                fontSize = 12.sp,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            OutlinedTextField(
                value = sttEndpoint,
                onValueChange = { sttEndpoint = it },
                label = { Text("Endpoint URL") },
                placeholder = { Text("https://api.openai.com/v1/audio/transcriptions") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = sttApiKey,
                onValueChange = { sttApiKey = it },
                label = { Text("API Key") },
                placeholder = { Text("sk-...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = sttModel,
                onValueChange = { sttModel = it },
                label = { Text("Model") },
                placeholder = { Text("whisper-1") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))

            // STT preset quick-fill
            Text("Presets:", fontSize = 12.sp, color = TextSecondary)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
            ) {
                SuggestionChip(
                    onClick = {
                        sttEndpoint = "https://openspeech.bytedance.com/api/v3/auc/bigmodel/recognize/flash"
                        sttModel = "bigmodel"
                    },
                    label = { Text("Doubao ASR", fontSize = 11.sp) },
                    shape = RoundedCornerShape(8.dp)
                )
                SuggestionChip(
                    onClick = {
                        sttEndpoint = "https://api.openai.com/v1/audio/transcriptions"
                        sttModel = "whisper-1"
                    },
                    label = { Text("OpenAI Whisper", fontSize = 11.sp) },
                    shape = RoundedCornerShape(8.dp)
                )
            }
 
             HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // === LLM Section ===
            Text(
                text = "LLM Refinement API",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "Raw text → refined todo item",
                fontSize = 12.sp,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            OutlinedTextField(
                value = llmEndpoint,
                onValueChange = { llmEndpoint = it },
                label = { Text("Endpoint URL") },
                placeholder = { Text("https://api.deepseek.com/v1/chat/completions") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = llmApiKey,
                onValueChange = { llmApiKey = it },
                label = { Text("API Key") },
                placeholder = { Text("sk-...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = llmModel,
                onValueChange = { llmModel = it },
                label = { Text("Model") },
                placeholder = { Text("deepseek-chat") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))

            // LLM preset quick-fill
            Text("Presets:", fontSize = 12.sp, color = TextSecondary)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
            ) {
                SuggestionChip(
                    onClick = {
                        llmEndpoint = "https://api.deepseek.com/v1/chat/completions"
                        llmModel = "deepseek-chat"
                    },
                    label = { Text("DeepSeek", fontSize = 11.sp) },
                    shape = RoundedCornerShape(8.dp)
                )
                SuggestionChip(
                    onClick = {
                        llmEndpoint = "https://ark.cn-beijing.volces.com/api/v3/chat/completions"
                        llmModel = "doubao-seed-2-0-mini-260428"
                    },
                    label = { Text("Doubao", fontSize = 11.sp) },
                    shape = RoundedCornerShape(8.dp)
                )
                SuggestionChip(
                    onClick = {
                        llmEndpoint = "https://api.openai.com/v1/chat/completions"
                        llmModel = "gpt-4o-mini"
                    },
                    label = { Text("OpenAI", fontSize = 11.sp) },
                    shape = RoundedCornerShape(8.dp)
                )
            }

            // Export section
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            Text(
                text = "Data Management",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { /* TODO: export */ },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Export")
                }
                OutlinedButton(
                    onClick = { /* TODO: import */ },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Import")
                }
            }

            // Obsidian (future)
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            Text(
                text = "Obsidian Sync",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "Sync completed todos to your Obsidian vault as markdown files. Remotely-save auto-detects changes.",
                fontSize = 12.sp,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Vault path input
            var vaultPath by remember { mutableStateOf("") }
            val syncer = com.quicktodo.QuickTodoApp.instance.obsidianSyncer
            var vaultStatus by remember { mutableStateOf("") }

            LaunchedEffect(Unit) {
                vaultPath = syncer.vaultPath ?: ""
            }

            OutlinedTextField(
                value = vaultPath,
                onValueChange = { vaultPath = it },
                label = { Text("Vault path") },
                placeholder = { Text("/storage/emulated/0/Obsidian") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        syncer.setVaultPath(vaultPath)
                        vaultStatus = if (syncer.isVaultValid()) "Path valid ✓" else "Path saved (not found)"
                    },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) { Text("Save path", fontSize = 12.sp) }

                if (vaultStatus.isNotEmpty()) {
                    Text(
                        text = vaultStatus,
                        fontSize = 12.sp,
                        color = if (vaultStatus.contains("✓")) MaterialTheme.colorScheme.primary else TextSecondary,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
            }

            // Save button
            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    scope.launch {
                        settings.saveSttSettings(sttEndpoint, sttApiKey, sttModel)
                        settings.saveLlmSettings(llmEndpoint, llmApiKey, llmModel)
                        saveMessage = "Saved!"
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Save Settings", fontWeight = FontWeight.SemiBold)
            }

            if (saveMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = saveMessage,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
