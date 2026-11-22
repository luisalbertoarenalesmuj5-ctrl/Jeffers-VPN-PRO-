package com.example.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Formatters {

    fun formatDuration(totalSeconds: Long): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024.0) return String.format(Locale.US, "%.2f MB", mb)
        val gb = mb / 1024.0
        return String.format(Locale.US, "%.2f GB", gb)
    }

    fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec < 1024L) return "$bytesPerSec B/s"
        val kb = bytesPerSec / 1024.0
        if (kb < 1024.0) return String.format(Locale.US, "%.1f KB/s", kb)
        val mb = kb / 1024.0
        return String.format(Locale.US, "%.2f MB/s", mb)
    }

    fun formatDateTime(epochMillis: Long): String {
        val sdf = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
        return sdf.format(Date(epochMillis))
    }

    fun maskPassword(password: String): String {
        if (password.isBlank()) return "Sin contraseña"
        if (password.length <= 8) return "•".repeat(password.length)
        return "${password.take(4)}••••••••••••${password.takeLast(4)}"
    }
}
