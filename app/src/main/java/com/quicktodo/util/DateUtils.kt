package com.quicktodo.util

import java.util.Calendar

fun parseChineseDate(dateStr: String, baseTimeMillis: Long = System.currentTimeMillis()): Long? {
    val s = dateStr.trim()
    if (s.equals("none", true) || s.isEmpty() ||
        s.equals("无", true) || s.equals("没有", true)) return null

    val cal = Calendar.getInstance().apply { timeInMillis = baseTimeMillis }
    val now = Calendar.getInstance().apply { timeInMillis = baseTimeMillis }

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

        s.matches(Regex("(周|星期|这周|这个星期)[一二三四五六日天]")) ||
            s.matches(Regex("[一二三四五六日天]")) -> {
            val dayChar = Regex("[一二三四五六日天]").find(s)?.value ?: return null
            val targetDay = dayMap[dayChar] ?: return null
            val currentDay = cal.get(Calendar.DAY_OF_WEEK)
            var diff = targetDay - currentDay
            if (diff < 0 || (diff == 0 && !s.startsWith("这"))) diff += 7
            cal.add(Calendar.DAY_OF_YEAR, diff)
        }

        s.matches(Regex("下周[一二三四五六日天]")) -> {
            val targetDay = dayMap[s.last().toString()] ?: return null
            var diff = targetDay - cal.get(Calendar.DAY_OF_WEEK)
            if (diff <= 0) diff += 7
            diff += 7
            cal.add(Calendar.DAY_OF_YEAR, diff)
        }

        s.matches(Regex("[0-9]+月[0-9]+[号日]")) -> {
            val parts = Regex("([0-9]+)月([0-9]+)[号日]").find(s) ?: return null
            cal.set(Calendar.MONTH, parts.groupValues[1].toInt() - 1)
            cal.set(Calendar.DAY_OF_MONTH, parts.groupValues[2].toInt())
            if (cal.before(now)) cal.add(Calendar.YEAR, 1)
        }

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

fun mergeDateWithExistingTime(dateMs: Long, existing: Long?): Long {
    val oldCal = existing?.let { Calendar.getInstance().apply { timeInMillis = it } }
    val oldHasCustomTime = oldCal?.let {
        it.get(Calendar.HOUR_OF_DAY) != 23 || it.get(Calendar.MINUTE) != 59
    } ?: false
    val newCal = Calendar.getInstance().apply { timeInMillis = dateMs }
    if (oldHasCustomTime) {
        newCal.set(Calendar.HOUR_OF_DAY, oldCal.get(Calendar.HOUR_OF_DAY))
        newCal.set(Calendar.MINUTE, oldCal.get(Calendar.MINUTE))
        newCal.set(Calendar.SECOND, 0)
    } else {
        newCal.set(Calendar.HOUR_OF_DAY, 23)
        newCal.set(Calendar.MINUTE, 59)
        newCal.set(Calendar.SECOND, 59)
    }
    newCal.set(Calendar.MILLISECOND, 0)
    return newCal.timeInMillis
}
