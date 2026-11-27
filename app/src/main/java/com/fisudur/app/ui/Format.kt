package com.fisudur.app.ui

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

object Fmt {
    private val days = mapOf(
        DayOfWeek.SATURDAY to "السبت", DayOfWeek.SUNDAY to "الأحد", DayOfWeek.MONDAY to "الاثنين",
        DayOfWeek.TUESDAY to "الثلاثاء", DayOfWeek.WEDNESDAY to "الأربعاء", DayOfWeek.THURSDAY to "الخميس",
        DayOfWeek.FRIDAY to "الجمعة",
    )
    private val months = listOf(
        "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
        "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر",
    )

    /** Saturday first, as the week is usually read in Arabic calendars. */
    val weekOrder = listOf(
        DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
    )

    fun day(d: DayOfWeek): String = days.getValue(d)

    fun date(d: LocalDate): String = "${day(d.dayOfWeek)}، ${d.dayOfMonth} ${months[d.monthValue - 1]} ${d.year}"

    fun shortDate(d: LocalDate): String = "${d.dayOfMonth} ${months[d.monthValue - 1]} ${d.year}"

    private val hijriMonths = listOf(
        "محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
        "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة",
    )

    /** The Hijri date by the Umm al-Qura calendar, e.g. "٦ جمادى الآخرة ١٤٤٨" (digits left to the caller). */
    fun hijri(d: LocalDate): String? = runCatching {
        val h = java.time.chrono.HijrahDate.from(d)
        val day = h.get(java.time.temporal.ChronoField.DAY_OF_MONTH)
        val month = h.get(java.time.temporal.ChronoField.MONTH_OF_YEAR)
        val year = h.get(java.time.temporal.ChronoField.YEAR)
        "$day ${hijriMonths[month - 1]} $year"
    }.getOrNull()

    /** Short names for calendar column heads, Saturday first. */
    val weekInitials = listOf("س", "ح", "ن", "ث", "ر", "خ", "ج")

    fun time(t: LocalTime): String {
        val h = t.hour
        val period = if (h < 12) "ص" else "م"
        val h12 = when {
            h == 0 -> 12
            h > 12 -> h - 12
            else -> h
        }
        return if (t.hour == 0 && t.minute == 0) "منتصف الليل" else "%d:%02d %s".format(h12, t.minute, period)
    }

    fun countdown(d: Duration): String {
        val s = d.seconds.coerceAtLeast(0)
        return "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
    }
}
