package app.streamlink.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

fun formatBitrate(bitsPerSecond: Long): String {
    if (bitsPerSecond <= 0L) return "0 kbps"
    return if (bitsPerSecond >= 1_000_000L) {
        String.format(Locale.US, "%.1f Mbps", bitsPerSecond / 1_000_000.0)
    } else {
        "${bitsPerSecond / 1000} kbps"
    }
}

fun formatDuration(millis: Long): String {
    val safe = millis.coerceAtLeast(0L)
    val hours = TimeUnit.MILLISECONDS.toHours(safe)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(safe) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(safe) % 60
    return if (hours > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

fun formatClock(timestamp: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) String.format(Locale.US, "%.1f MB", mb)
    else String.format(Locale.US, "%.0f KB", kb)
}
