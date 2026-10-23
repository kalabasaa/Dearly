package com.dearly.app.utils

import android.content.Context
import android.text.format.DateUtils
import java.util.Calendar

/** Local, readable timestamps. Stored values are always epoch millis. */
object Formatters {
    fun dateTime(context: Context, millis: Long): String {
        var flags = DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH
        if (!isThisYear(millis)) flags = flags or DateUtils.FORMAT_SHOW_YEAR
        return DateUtils.formatDateTime(context, millis, flags)
    }

    fun date(context: Context, millis: Long): String {
        var flags = DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH
        if (!isThisYear(millis)) flags = flags or DateUtils.FORMAT_SHOW_YEAR
        return DateUtils.formatDateTime(context, millis, flags)
    }

    fun time(context: Context, millis: Long): String =
        DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_TIME)

    fun fullDate(context: Context, millis: Long): String =
        DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_SHOW_WEEKDAY)

    fun monthYear(context: Context, millis: Long): String =
        DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NO_MONTH_DAY or DateUtils.FORMAT_SHOW_YEAR)

    private fun isThisYear(millis: Long): Boolean {
        val c = Calendar.getInstance()
        val year = c.get(Calendar.YEAR)
        c.timeInMillis = millis
        return c.get(Calendar.YEAR) == year
    }
}
