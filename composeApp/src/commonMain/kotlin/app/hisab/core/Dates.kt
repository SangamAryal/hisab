package app.hisab.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** PocketBase stores dates as "2026-10-08 12:00:00.000Z" (UTC). */
@OptIn(ExperimentalTime::class)
object Dates {
    private val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    fun today(): LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    /** A calendar day stored at noon UTC, so it shows as the same day in every timezone. */
    fun toPb(date: LocalDate): String = "$date 12:00:00.000Z"

    fun fromPb(value: String): LocalDate? =
        runCatching { LocalDate.parse(value.trim().take(10)) }.getOrNull()

    /** "Oct 8" this year, "Oct 8, 2025" otherwise. */
    fun display(value: String, today: LocalDate = today()): String {
        val d = fromPb(value) ?: return ""
        val base = "${months[d.month.ordinal]} ${d.day}"
        return if (d.year == today.year) base else "$base, ${d.year}"
    }

    fun monthLabel(value: String): String {
        val d = fromPb(value) ?: return ""
        return "${months[d.month.ordinal]} ${d.year}"
    }
}
