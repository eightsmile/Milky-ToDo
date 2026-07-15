package com.quicktodo.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.quicktodo.QuickTodoApp
import com.quicktodo.ui.theme.TextSecondary
import com.quicktodo.voice.ApiService
import com.quicktodo.voice.LlmTodoItem
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

data class EditableTodoItem(
    val title: String,
    var dueDate: Long?,
    var repeatInterval: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceInputScreen(
    onBack: () -> Unit,
    onConfirm: (List<EditableTodoItem>) -> Unit,
    settingsProvider: () -> com.quicktodo.data.SettingsDataStore
) {
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var showReview by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var audioFile by remember { mutableStateOf<File?>(null) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var todoItems by remember { mutableStateOf<List<EditableTodoItem>>(emptyList()) }
    var originalText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) errorMessage = "Microphone permission required"
    }

    val pulse = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulse.animateFloat(1f, 1.15f, animationSpec = infiniteRepeatable(
        tween(800, easing = EaseInOutCubic), RepeatMode.Reverse
    ), label = "pulseScale")

    DisposableEffect(Unit) {
        onDispose { mediaRecorder?.release(); audioFile?.delete() }
    }

    fun applyTimeToDate(dueDate: Long?, timeStr: String): Long? {
        if (dueDate == null || timeStr.isBlank() || timeStr.equals("none", true)) return dueDate
        val parts = timeStr.split(":")
        if (parts.size != 2) return dueDate
        return try {
            val cal = Calendar.getInstance().apply { timeInMillis = dueDate }
            cal.set(Calendar.HOUR_OF_DAY, parts[0].toInt())
            cal.set(Calendar.MINUTE, parts[1].toInt())
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
        } catch (_: Exception) { dueDate }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Voice Input", fontWeight = FontWeight.Bold) },
                navigationIcon = { TextButton(onClick = {
                    mediaRecorder?.release(); onBack()
                }) { Text("Cancel") } }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            contentAlignment = if (showReview) Alignment.TopCenter else Alignment.BottomCenter
        ) {
            when {
                showReview -> {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Review & Edit Tasks",
                            fontSize = 18.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp))

                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(todoItems) { index, _ ->
                                TodoItemEditor(
                                    item = todoItems[index],
                                    onUpdate = { updated ->
                                        todoItems = todoItems.toMutableList().also {
                                            it[index] = updated
                                        }
                                    },
                                    onRemove = {
                                        todoItems = todoItems.toMutableList().also {
                                            it.removeAt(index)
                                        }
                                        if (todoItems.isEmpty()) {
                                            showReview = false
                                        }
                                    }
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = originalText,
                            onValueChange = { originalText = it },
                            label = { Text("Original") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            minLines = 2, maxLines = 3,
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = TextSecondary)
                        )

                        Spacer(Modifier.height(12.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(onClick = {
                                showReview = false; errorMessage = ""
                                todoItems = emptyList(); originalText = ""
                            }, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                                Text("Redo")
                            }
                            Button(onClick = {
                                onConfirm(todoItems)
                                showReview = false; errorMessage = ""
                                todoItems = emptyList(); originalText = ""
                            }, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                                Text("Add All (${todoItems.size})")
                            }
                        }
                    }
                }
                isProcessing -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 80.dp)
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("Transcribing with Doubao...", color = TextSecondary)
                        Spacer(Modifier.height(24.dp))
                        TextButton(onClick = { isProcessing = false; errorMessage = "Cancelled" }) {
                            Text("Cancel")
                        }
                    }
                }
                else -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 80.dp)
                    ) {
                        Text(
                            if (isRecording) "Recording..." else "Hold to record",
                            fontSize = 18.sp,
                            fontWeight = if (isRecording) FontWeight.Bold else FontWeight.Normal,
                            color = TextSecondary,
                            modifier = Modifier.padding(bottom = 40.dp)
                        )

                        Box(
                            modifier = Modifier.size(180.dp)
                                .then(if (isRecording) Modifier.scale(pulseScale) else Modifier)
                                .clip(CircleShape)
                                .background(if (isRecording) Color(0xFFFF3B30) else MaterialTheme.colorScheme.primary)
                                .pointerInput(Unit) {
                                    detectTapGestures(onPress = {
                                        val perm = Manifest.permission.RECORD_AUDIO
                                        if (ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED) {
                                            permissionLauncher.launch(perm)
                                            tryAwaitRelease()
                                            return@detectTapGestures
                                        }
                                        errorMessage = ""

                                        val file = File.createTempFile("voice", ".m4a", context.cacheDir)
                                        audioFile = file
                                        val rec = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
                                        rec.setAudioSource(MediaRecorder.AudioSource.MIC)
                                        rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                                        rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                                        rec.setAudioSamplingRate(44100)
                                        rec.setAudioEncodingBitRate(128000)
                                        rec.setAudioChannels(1)
                                        rec.setOutputFile(file.absolutePath)
                                        rec.prepare()
                                        rec.start()
                                        mediaRecorder = rec
                                        isRecording = true

                                        val startTime = System.currentTimeMillis()
                                        tryAwaitRelease()
                                        val duration = System.currentTimeMillis() - startTime

                                        if (duration < 1000) {
                                            try { rec.stop() } catch (_: Exception) { }
                                            try { rec.release() } catch (_: Exception) { }
                                            isRecording = false
                                            mediaRecorder = null
                                            errorMessage = "No Voice Recording"
                                            return@detectTapGestures
                                        }

                                        try { rec.stop() } catch (_: Exception) { }
                                        rec.release()
                                        mediaRecorder = null
                                        isRecording = false
                                        isProcessing = true

                                        scope.launch {
                                            val api = ApiService(QuickTodoApp.instance.settingsDataStore)
                                            val stt = api.transcribeAudio(file)
                                            if (!stt.success) {
                                                errorMessage = stt.error
                                                isProcessing = false
                                                return@launch
                                            }
                                            originalText = stt.text
                                            val llm = api.refineText(stt.text)
                                            if (llm.items != null && llm.items.size > 1) {
                                                todoItems = llm.items.map {
                                                    EditableTodoItem(
                                                        title = it.title,
                                                        dueDate = applyTimeToDate(it.dueDate, ""),
                                                        repeatInterval = it.repeatInterval
                                                    )
                                                }
                                            } else {
                                                todoItems = listOf(
                                                    EditableTodoItem(
                                                        title = if (llm.success) llm.text else stt.text,
                                                        dueDate = llm.dueDate,
                                                        repeatInterval = llm.repeatInterval
                                                    )
                                                )
                                            }
                                            isProcessing = false
                                            showReview = true
                                        }
                                    })
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(72.dp)
                            )
                        }

                        Spacer(Modifier.height(20.dp))
                        Text(
                            if (isRecording) "Release to finish" else "Press & hold",
                            color = TextSecondary, fontSize = 14.sp
                        )
                        if (errorMessage.isNotEmpty() && !isRecording) {
                            Spacer(Modifier.height(12.dp))
                            Text(errorMessage, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoItemEditor(
    item: EditableTodoItem,
    onUpdate: (EditableTodoItem) -> Unit,
    onRemove: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var editTitle by remember { mutableStateOf(item.title) }

    // Sync back title changes
    LaunchedEffect(editTitle) {
        onUpdate(item.copy(title = editTitle))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            OutlinedTextField(
                value = editTitle,
                onValueChange = { editTitle = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 15.sp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )

            Spacer(Modifier.height(8.dp))

            // Chips row: Repeat | Date | Time | Remove
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Repeat chip (left)
                AssistChip(
                    onClick = {
                        val newRepeat = when (item.repeatInterval) {
                            "NONE" -> "DAILY"
                            "DAILY" -> "WEEKLY"
                            "WEEKLY" -> "MONTHLY"
                            else -> "NONE"
                        }
                        onUpdate(item.copy(repeatInterval = newRepeat))
                    },
                    label = {
                        Text(
                            when (item.repeatInterval) {
                                "DAILY" -> "Daily"
                                "WEEKLY" -> "Weekly"
                                "MONTHLY" -> "Monthly"
                                else -> "Repeat"
                            }, fontSize = 11.sp
                        )
                    },
                    shape = RoundedCornerShape(6.dp)
                )

                // Date chip (center)
                AssistChip(
                    onClick = { showDatePicker = true },
                    label = {
                        Text(
                            item.dueDate?.let { dateFormat.format(Date(it)) } ?: "Date",
                            fontSize = 11.sp
                        )
                    },
                    shape = RoundedCornerShape(6.dp)
                )

                // Time chip (right of date)
                val hasCustomTime = item.dueDate?.let {
                    val cal = Calendar.getInstance().apply { timeInMillis = it }
                    cal.get(Calendar.HOUR_OF_DAY) != 23 || cal.get(Calendar.MINUTE) != 59
                } ?: false

                AssistChip(
                    onClick = { showTimePicker = true },
                    label = {
                        Text(
                            if (hasCustomTime && item.dueDate != null)
                                timeFormat.format(Date(item.dueDate!!))
                            else "Time",
                            fontSize = 11.sp,
                            color = if (hasCustomTime) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    shape = RoundedCornerShape(6.dp)
                )

                Spacer(Modifier.weight(1f))

                TextButton(onClick = onRemove, contentPadding = PaddingValues(4.dp)) {
                    Text("✕", fontSize = 13.sp, color = Color(0xFFFF3B30))
                }
            }

            // Date picker
            if (showDatePicker) {
                val datePickerState = rememberDatePickerState(
                    initialSelectedDateMillis = item.dueDate ?: System.currentTimeMillis()
                )
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            datePickerState.selectedDateMillis?.let { ms ->
                                // Preserve time if exists
                                val oldCal = item.dueDate?.let { Calendar.getInstance().apply { timeInMillis = it } }
                                val newCal = Calendar.getInstance().apply { timeInMillis = ms }
                                if (oldCal != null) {
                                    newCal.set(Calendar.HOUR_OF_DAY, oldCal.get(Calendar.HOUR_OF_DAY))
                                    newCal.set(Calendar.MINUTE, oldCal.get(Calendar.MINUTE))
                                } else {
                                    newCal.set(Calendar.HOUR_OF_DAY, 23)
                                    newCal.set(Calendar.MINUTE, 59)
                                }
                                newCal.set(Calendar.SECOND, 59)
                                newCal.set(Calendar.MILLISECOND, 0)
                                onUpdate(item.copy(dueDate = newCal.timeInMillis))
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

            // Time picker
            if (showTimePicker) {
                val timePickerState = rememberTimePickerState(
                    initialHour = item.dueDate?.let {
                        Calendar.getInstance().apply { timeInMillis = it }.get(Calendar.HOUR_OF_DAY)
                    } ?: 12,
                    initialMinute = item.dueDate?.let {
                        Calendar.getInstance().apply { timeInMillis = it }.get(Calendar.MINUTE)
                    } ?: 0,
                    is24Hour = true
                )
                AlertDialog(
                    onDismissRequest = { showTimePicker = false },
                    title = { Text("Select Time", fontWeight = FontWeight.Bold) },
                    text = { TimePicker(state = timePickerState) },
                    confirmButton = {
                        TextButton(onClick = {
                            // Set date to today if none, then apply time
                            val cal = Calendar.getInstance()
                            item.dueDate?.let { cal.timeInMillis = it }
                            cal.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                            cal.set(Calendar.MINUTE, timePickerState.minute)
                            cal.set(Calendar.SECOND, 0)
                            cal.set(Calendar.MILLISECOND, 0)
                            onUpdate(item.copy(dueDate = cal.timeInMillis))
                            showTimePicker = false
                        }) { Text("OK") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
                    }
                )
            }
        }
    }
}

private fun EditableTodoItem.copy(
    title: String? = null,
    dueDate: Long? = null,
    repeatInterval: String? = null
) = EditableTodoItem(
    title = title ?: this.title,
    dueDate = dueDate ?: this.dueDate,
    repeatInterval = repeatInterval ?: this.repeatInterval
)
