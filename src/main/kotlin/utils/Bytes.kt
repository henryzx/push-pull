package utils

import java.util.*

object Bytes {
    fun format(value: Long, locale: Locale = Locale.getDefault()): String = when {
        value < 1024 -> "$value B"
        else -> {
            val z = (63 - java.lang.Long.numberOfLeadingZeros(value)) / 10
            String.format(locale, "%.1f %siB", value.toDouble() / (1L shl z * 10), " KMGTPE"[z])
        }
    }
}