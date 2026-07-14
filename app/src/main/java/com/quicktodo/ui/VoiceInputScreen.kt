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
import java.io.File
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceInputScreen(
    onBack: () -> Unit,
    onConfirm: (String, Long?, String) -> Unit,
    settingsProvider: () -> com.quicktodo.data.SettingsDataStore
) {
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var showReview by remember { mutableStateOf(false) }
    var recognizedText by remember { mutableStateOf("") }
    var refinedText by remember { mutableStateOf("") }
    var voiceDueDate by remember { mutableStateOf<Long?>(null) }
    var voiceRepeat by remember { mutableStateOf("NONE") }
    var audioFile by remember { mutableStateOf<File?>(null) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var errorMessage by remember { mutableStateOf("") }
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
            contentAlignment = if (showReview) Alignment.Center else Alignment.BottomCenter
        ) {
            when {
                showReview -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)
                    ) {
                        Text("Refined Todo", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(value = refinedText, onValueChange = { refinedText = it },
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                            minLines = 2, maxLines = 4
                        )

                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = recognizedText,
                            onValueChange = { recognizedText = it },
                            label = { Text("Original") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            minLines = 2, maxLines = 4,
                            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, color = TextSecondary)
                        )

                        // Date & Repeat chips
                        Spacer(Modifier.height(12.dp))

                        var showDatePicker by remember { mutableStateOf(false) }
                        val datePickerState = rememberDatePickerState(
                            initialSelectedDateMillis = voiceDueDate ?: System.currentTimeMillis()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val dateFormat = remember {
                                java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault())
                            }
                            AssistChip(
                                onClick = { showDatePicker = true },
                                label = {
                                    Text(
                                        voiceDueDate?.let { dateFormat.format(java.util.Date(it)) } ?: "Set date",
                                        fontSize = 12.sp
                                    )
                                },
                                shape = RoundedCornerShape(8.dp)
                            )

                            // Repeat chip (cycling through options)
                            AssistChip(
                                onClick = {
                                    voiceRepeat = when (voiceRepeat) {
                                        "NONE" -> "DAILY"
                                        "DAILY" -> "WEEKLY"
                                        "WEEKLY" -> "MONTHLY"
                                        else -> "NONE"
                                    }
                                },
                                label = {
                                    Text(
                                        when (voiceRepeat) {
                                            "DAILY" -> "Daily"
                                            "WEEKLY" -> "Weekly"
                                            "MONTHLY" -> "Monthly"
                                            else -> "Repeat"
                                        },
                                        fontSize = 12.sp
                                    )
                                },
                                shape = RoundedCornerShape(8.dp)
                            )

                            if (voiceDueDate != null || voiceRepeat != "NONE") {
                                TextButton(
                                    onClick = { voiceDueDate = null; voiceRepeat = "NONE" },
                                    contentPadding = PaddingValues(0.dp)
                                ) { Text("Clear", fontSize = 11.sp) }
                            }
                        }

                        // Date picker dialog
                        if (showDatePicker) {
                            DatePickerDialog(
                                onDismissRequest = { showDatePicker = false },
                                confirmButton = {
                                    TextButton(onClick = {
                                        voiceDueDate = datePickerState.selectedDateMillis
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

                        Spacer(Modifier.height(20.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = {
                                showReview = false; recognizedText = ""; refinedText = ""
                                voiceDueDate = null; voiceRepeat = "NONE"
                            }, shape = RoundedCornerShape(12.dp)) { Text("Redo") }
                            Button(onClick = {
                                onConfirm(refinedText.ifBlank { recognizedText },
                                    voiceDueDate, voiceRepeat)
                                showReview = false; recognizedText = ""; refinedText = ""
                                voiceDueDate = null; voiceRepeat = "NONE"
                            }, shape = RoundedCornerShape(12.dp)) { Text("Add Todo") }
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

                                        // Start recording
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
                                            recognizedText = stt.text
                                            val llm = api.refineText(stt.text)
                                            refinedText = if (llm.success) llm.text else stt.text
                                            voiceDueDate = llm.dueDate
                                            voiceRepeat = llm.repeatInterval
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
