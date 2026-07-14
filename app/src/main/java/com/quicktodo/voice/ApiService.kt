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
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import java.util.UUID
import java.util.concurrent.TimeUnit

data class SttResult(val success: Boolean, val text: String = "", val error: String = "")

data class LlmResult(
    val success: Boolean,
    val text: String = "",
    val dueDate: Long? = null,
    val repeatInterval: String = "NONE",
    val error: String = ""
)

class ApiService(private val settings: SettingsDataStore) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS).build()
    private val jsonMt = "application/json; charset=utf-8".toMediaType()

    suspend fun transcribeAudio(audioFile: File): SttResult {
        try {
            val ak = settings.sttApiKey.first()
            if (ak.isBlank()) return SttResult(false, error = "STT API key not configured")
            val uid = ak.take(15)
            val audioBytes = withContext(Dispatchers.IO) { audioFile.readBytes() }
            val b64 = Base64.encodeToString(audioBytes, Base64.NO_WRAP)

            val body = JSONObject().apply {
                put("user", JSONObject().apply { put("uid", uid) })
                put("audio", JSONObject().apply { put("data", b64) })
                put("request", JSONObject().apply { put("model_name", "bigmodel") })
            }

            val req = Request.Builder()
                .url("https://openspeech.bytedance.com/api/v3/auc/bigmodel/recognize/flash")
                .header("X-Api-Key", ak)
                .header("X-Api-Resource-Id", "volc.bigasr.auc_turbo")
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

    suspend fun refineText(rawText: String): LlmResult {
        try {
            val ep = settings.llmEndpoint.first().ifBlank {
                return LlmResult(false, error = "LLM endpoint not configured")
            }
            val ak = settings.llmApiKey.first().ifBlank {
                return LlmResult(false, error = "LLM API key not configured")
            }
            val md = settings.llmModel.first().ifBlank { "deepseek-chat" }

            val sysPrompt = "请将用户的语音转写内容整理为待办事项，以JSON格式返回。\n\n" +
                "要求：\n" +
                "1. 一项任务对应一条待办，输出格式：{\"title\": \"...\", \"date\": \"...或none\", \"repeat\": \"DAILY/WEEKLY/MONTHLY/NONE\"}\n" +
                "2. title：对内容进行改写，但尽量保留用户原始表达的含义和内容长度。只删除语气词（如\"嗯\"、\"啊\"、\"那个\"）、无效重复、以及明显说错的内容。不要过度精简或改写，保留完整意图。\n" +
                "3. date：提取日期、时间、截止时间，如\"3月15日\"、\"下周一\"；未提及则为\"none\"\n" +
                "4. repeat：提取重复周期，\"DAILY\"/\"WEEKLY\"/\"MONTHLY\"/\"NONE\"\n" +
                "5. 信息不明确时，不要擅自推测\n" +
                "6. 仅输出JSON，不添加解释\n\n" +
                "示例：\n" +
                "\"明天去买牛奶\" -> {\"title\":\"明天去买牛奶\",\"date\":\"明天\",\"repeat\":\"NONE\"}\n" +
                "\"每周一开周会\" -> {\"title\":\"每周一开周会\",\"date\":\"下周一\",\"repeat\":\"WEEKLY\"}\n" +
                "\"每天吃药\" -> {\"title\":\"每天吃药\",\"date\":\"none\",\"repeat\":\"DAILY\"}\n" +
                "\"每个月1号交话费\" -> {\"title\":\"每个月1号交话费\",\"date\":\"下个月1号\",\"repeat\":\"MONTHLY\"}\n\n" +
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

            // Try JSON first, fallback to plain text
            val json = try { JSONObject(content) } catch (_: Exception) { null }
            if (json != null) {
                val title = json.optString("title", content).trim()
                val dateStr = json.optString("date", "none")
                val repeatStr = json.optString("repeat", "NONE").let { r ->
                    when {
                        r.equals("DAILY", true) -> "DAILY"
                        r.equals("WEEKLY", true) -> "WEEKLY"
                        r.equals("MONTHLY", true) -> "MONTHLY"
                        else -> "NONE"
                    }
                }
                val dueDate = parseDateString(dateStr)
                return LlmResult(true, text = title, dueDate = dueDate, repeatInterval = repeatStr)
            }

            // Sometimes LLM returns a JSON array instead of object (e.g. "[", "[{...}]")
            val array = try { JSONArray(content) } catch (_: Exception) { null }
            if (array != null && array.length() > 0) {
                val first = array.optJSONObject(0)
                if (first != null) {
                    val title = first.optString("title", "").trim()
                    if (title.isNotBlank()) {
                        val dateStr = first.optString("date", "none")
                        val repeatStr = first.optString("repeat", "NONE").let { r ->
                            when {
                                r.equals("DAILY", true) -> "DAILY"
                                r.equals("WEEKLY", true) -> "WEEKLY"
                                r.equals("MONTHLY", true) -> "MONTHLY"
                                else -> "NONE"
                            }
                        }
                        val dueDate = parseDateString(dateStr)
                        return LlmResult(true, text = title, dueDate = dueDate, repeatInterval = repeatStr)
                    }
                }
            }

            // Plain text fallback: take first meaningful line, skip garbage
            val clean = content.trim().replace(Regex("^[\\[\\]{}()\"'\\s,]+|[\\[\\]{}()\"'\\s,]+$\""), "").trim()
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
