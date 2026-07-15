package com.quicktodo.voice

import android.util.Base64
import com.quicktodo.data.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import java.util.UUID
import java.util.concurrent.TimeUnit

data class SttResult(val success: Boolean, val text: String = "", val error: String = "")

data class LlmTodoItem(
    val title: String,
    val dueDate: Long? = null,
    val repeatInterval: String = "NONE"
)

data class LlmResult(
    val success: Boolean,
    val text: String = "",
    val dueDate: Long? = null,
    val repeatInterval: String = "NONE",
    val items: List<LlmTodoItem>? = null,
    val error: String = ""
)

class ApiService(private val settings: SettingsDataStore) {

    suspend fun isStreamingMode(): Boolean {
        return settings.sttMode.first() == "streaming"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS).build()
    private val jsonMt = "application/json; charset=utf-8".toMediaType()

    suspend fun transcribeAudio(audioFile: File): SttResult {
        try {
            val ak = settings.sttApiKey.first()
            if (ak.isBlank()) return SttResult(false, error = "STT API key not configured")
            val rid = settings.sttResourceId.first().ifBlank { "volc.bigasr.auc_turbo" }
            val uid = ak.take(15)
            val audioBytes = withContext(Dispatchers.IO) { audioFile.readBytes() }
            val b64 = Base64.encodeToString(audioBytes, Base64.NO_WRAP)

            val body = JSONObject().apply {
                put("user", JSONObject().apply { put("uid", uid) })
                put("audio", JSONObject().apply { put("data", b64) })
                put("request", JSONObject().apply {
                    put("model_name", settings.sttModel.first().ifBlank { "bigmodel" })
                })
            }

            val req = Request.Builder()
                .url("https://openspeech.bytedance.com/api/v3/auc/bigmodel/recognize/flash")
                .header("X-Api-Key", ak)
                .header("X-Api-Resource-Id", rid)
                .header("X-Api-Request-Id", UUID.randomUUID().toString())
                .header("X-Api-Sequence", "-1")
                .header("Content-Type", "application/json")
                .post(body.toString().toRequestBody(jsonMt))
                .build()

            val resp = withContext(Dispatchers.IO) { client.newCall(req).execute() }
            val respBody = resp.body?.string() ?: ""
            if (!resp.isSuccessful) return SttResult(false, error = "ASR (${resp.code}): ${respBody.take(300)}")

            val json = JSONObject(respBody)
            val text = json.optJSONObject("result")?.optString("text", "")?.trim() ?: ""
            if (text.isBlank()) return SttResult(false, error = "No Voice Input")
            return SttResult(true, text = text)
        } catch (e: Exception) {
            return SttResult(false, error = "STT failed: ${e.localizedMessage ?: "Unknown"}")
        }
    }

    suspend fun transcribeAudioStream(
        audioProvider: (StreamingAsrClient.ChunkSender) -> Unit,
        onDebug: ((String) -> Unit)? = null
    ): SttResult {
        val ak = settings.sttApiKey.first()
        if (ak.isBlank()) return SttResult(false, error = "STT API key not configured")
        val rid = settings.sttResourceId.first().ifBlank { "volc.seedasr.sauc.duration" }
        val ep = settings.sttEndpoint.first().ifBlank {
            "wss://openspeech.bytedance.com/api/v3/sauc/bigmodel"
        }

        return withContext(Dispatchers.IO) {
            val cl = StreamingAsrClient(ak, rid, ep)
            val result = cl.transcribe(audioProvider, onDebug)
            if (result.success) SttResult(true, text = result.text)
            else SttResult(false, error = result.error)
        }
    }

    suspend fun refineText(rawText: String): LlmResult {
        try {
            val ep = settings.llmEndpoint.first().ifBlank {
                return LlmResult(false, error = "LLM endpoint not configured")
            }
            val ak = settings.llmApiKey.first().ifBlank {
                return LlmResult(false, error = "LLM API key not configured")
            }
            val md = settings.llmModel.first().ifBlank { "deepseek-v4-flash" }

            val sysPrompt = "将语音转写为JSON数组，格式：[{\"title\":\"...\",\"date\":\"...\",\"time\":\"...\",\"repeat\":\"DAILY/WEEKLY/MONTHLY/NONE\"}]\n" +
                "规则：\n" +
                "- title：保留原意，删语气词\n" +
                "- date：提取日期（如\"明天\"、\"下周一\"），无则为\"none\"\n" +
                "- time：提取24h时间（如\"下午3点\"→\"15:00\"），无则为\"none\"\n" +
                "- repeat：提取重复周期，无则为\"NONE\"\n" +
                "- 多任务返回多个对象\n" +
                "- 仅输出JSON数组，不要解释\n\n" +
                "语音内容：\n" + rawText

            val msgs = org.json.JSONArray().apply {
                put(JSONObject().apply { put("role", "user"); put("content", sysPrompt) })
            }
            val rb = JSONObject().apply {
                put("model", md); put("messages", msgs)
                put("max_tokens", 200); put("temperature", 0.1)
            }
            val rq = Request.Builder()
                .url(ep).header("Authorization", "Bearer $ak")
                .header("Content-Type", "application/json")
                .post(rb.toString().toRequestBody(jsonMt)).build()
            val rp = withContext(Dispatchers.IO) { client.newCall(rq).execute() }
            val bd = rp.body?.string() ?: ""
            if (!rp.isSuccessful) return LlmResult(false, error = "LLM (${rp.code}): ${bd.take(200)}")

            val content = JSONObject(bd)
                .optJSONArray("choices")?.optJSONObject(0)
                ?.optJSONObject("message")?.optString("content", "")
                ?.trim() ?: ""

            if (content.isBlank()) return LlmResult(false, error = "LLM empty")

            // Strip markdown code blocks if present
            val cleanContent = content
                .replace(Regex("```json\\s*", RegexOption.IGNORE_CASE), "")
                .replace(Regex("```\\s*$", RegexOption.MULTILINE), "")
                .trim()

            fun parseItem(obj: JSONObject): LlmTodoItem {
                val title = obj.optString("title", "").trim()
                val dateStr = obj.optString("date", "none")
                val timeStr = obj.optString("time", "none")
                val repeatStr = obj.optString("repeat", "NONE").let { r ->
                    when {
                        r.equals("DAILY", true) -> "DAILY"
                        r.equals("WEEKLY", true) -> "WEEKLY"
                        r.equals("MONTHLY", true) -> "MONTHLY"
                        else -> "NONE"
                    }
                }
                var dueDate = parseDateString(dateStr)
                // Apply time if provided
                if (!timeStr.equals("none", true) && timeStr.isNotBlank()) {
                    val parts = timeStr.split(":")
                    if (parts.size == 2) {
                        try {
                            val h = parts[0].toInt()
                            val m = parts[1].toInt()
                            val cal = Calendar.getInstance().apply {
                                // If no date was given, default to today
                                if (dueDate == null) {
                                    timeInMillis = System.currentTimeMillis()
                                    set(Calendar.HOUR_OF_DAY, 0)
                                    set(Calendar.MINUTE, 0)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                } else {
                                    timeInMillis = dueDate
                                }
                            }
                            cal.set(Calendar.HOUR_OF_DAY, h)
                            cal.set(Calendar.MINUTE, m)
                            cal.set(Calendar.SECOND, 0)
                            cal.set(Calendar.MILLISECOND, 0)
                            dueDate = cal.timeInMillis
                        } catch (_: Exception) { }
                    }
                }
                return LlmTodoItem(title = title.ifBlank { "Untitled" }, dueDate = dueDate, repeatInterval = repeatStr)
            }

            // Try JSONArray first (new format)
            val array = try { JSONArray(cleanContent) } catch (_: Exception) { null }
            if (array != null && array.length() > 0) {
                val items = mutableListOf<LlmTodoItem>()
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i)
                    if (obj != null) {
                        val item = parseItem(obj)
                        if (item.title.isNotBlank()) items.add(item)
                    }
                }
                if (items.isNotEmpty()) {
                    val first = items.first()
                    return LlmResult(
                        success = true, text = first.title,
                        dueDate = first.dueDate, repeatInterval = first.repeatInterval,
                        items = if (items.size > 1) items else null
                    )
                }
            }

            // Fallback: try single JSON object
            val json = try { JSONObject(cleanContent) } catch (_: Exception) { null }
            if (json != null) {
                val item = parseItem(json)
                if (item.title.isNotBlank()) {
                    return LlmResult(success = true, text = item.title,
                        dueDate = item.dueDate, repeatInterval = item.repeatInterval)
                }
            }

            // Plain text fallback: take first meaningful line, skip garbage
            val clean = cleanContent.trim().replace(Regex("^[\\[\\]{}()\"'\\s,]+|[\\[\\]{}()\"'\\s,]+$\""), "").trim()
            val firstLine = clean.lines().firstOrNull { it.isNotBlank() && it.length > 1 } ?: content
            return LlmResult(true, text = firstLine)
        } catch (e: Exception) {
            return LlmResult(false, error = "LLM failed: ${e.localizedMessage ?: "Unknown"}")
        }
    }

    private fun parseDateString(dateStr: String): Long? {
        val s = dateStr.trim()
        if (s.equals("none", true) || s.isEmpty() ||
            s.equals("无", true) || s.equals("没有", true)) return null

        val cal = Calendar.getInstance()
        val now = Calendar.getInstance()

        val dayMap = mapOf(
            "一" to Calendar.MONDAY, "二" to Calendar.TUESDAY,
            "三" to Calendar.WEDNESDAY, "四" to Calendar.THURSDAY,
            "五" to Calendar.FRIDAY, "六" to Calendar.SATURDAY,
            "日" to Calendar.SUNDAY, "天" to Calendar.SUNDAY
        )

        when {
            s == "今天" || s == "今日" -> { }
            s == "明天" || s == "明日" -> cal.add(Calendar.DAY_OF_YEAR, 1)
            s == "后天" || s == "后日" -> cal.add(Calendar.DAY_OF_YEAR, 2)
            s == "大后天" -> cal.add(Calendar.DAY_OF_YEAR, 3)

            // 周五 / 星期X / 这周五 → this week's day
            s.matches(Regex("(周|星期|这周|这个星期)[一二三四五六日天]")) ||
            s.matches(Regex("[一二三四五六日天]")) -> {
                val dayChar = Regex("[一二三四五六日天]").find(s)?.value ?: return null
                val targetDay = dayMap[dayChar] ?: return null
                val currentDay = cal.get(Calendar.DAY_OF_WEEK)
                var diff = targetDay - currentDay
                // If today is the target day, assume next week (only if it's a plain day name like "周三" and it's already that day)
                if (diff < 0 || (diff == 0 && !s.startsWith("这"))) diff += 7
                cal.add(Calendar.DAY_OF_YEAR, diff)
            }

            // 下周X
            s.matches(Regex("下周[一二三四五六日天]")) -> {
                val dayChar = s.last().toString()
                val targetDay = dayMap[dayChar] ?: return null
                // Go to next week's target day
                var diff = targetDay - cal.get(Calendar.DAY_OF_WEEK)
                if (diff <= 0) diff += 7
                diff += 7  // next week
                cal.add(Calendar.DAY_OF_YEAR, diff)
            }

            // X月X号/日
            s.matches(Regex("[0-9]+月[0-9]+[号日]")) -> {
                val parts = Regex("([0-9]+)月([0-9]+)[号日]").find(s)
                if (parts != null) {
                    cal.set(Calendar.MONTH, parts.groupValues[1].toInt() - 1)
                    cal.set(Calendar.DAY_OF_MONTH, parts.groupValues[2].toInt())
                    if (cal.before(now)) cal.add(Calendar.YEAR, 1)
                }
            }

            // 下个月X号
            s.startsWith("下个月") -> {
                cal.add(Calendar.MONTH, 1)
                val dayMatch = Regex("([0-9]+)[号日]").find(s)
                if (dayMatch != null) {
                    cal.set(Calendar.DAY_OF_MONTH, dayMatch.groupValues[1].toInt())
                }
            }

            else -> return null
        }

        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
