package com.snapfree.app.util

import java.text.DateFormat
import java.util.Date
import java.util.Locale

object Formatters {
    fun size(bytes: Long): String {
        if (bytes <= 0) return ""
        val units = arrayOf("B", "KB", "MB", "GB")
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) {
            value /= 1024
            unit++
        }
        return if (unit <= 1) String.format(Locale.getDefault(), "%.0f %s", value, units[unit])
        else String.format(Locale.getDefault(), "%.1f %s", value, units[unit])
    }

    fun duration(seconds: Int): String {
        if (seconds <= 0) return ""
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.ROOT, "%d:%02d", m, s)
    }

    fun eta(seconds: Long): String = if (seconds <= 0) "" else duration(seconds.toInt())

    fun dateTime(millis: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))
}
