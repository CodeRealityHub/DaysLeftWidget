package com.example.daysleftwidget

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class DaysLeftWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_UPDATE = "com.example.daysleftwidget.ACTION_UPDATE"

        fun isLeapYear(year: Int): Boolean {
            return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
        }

        /** Days remaining after today, i.e. NOT counting today itself. */
        fun daysLeftInYear(today: LocalDate): Long {
            val endOfYear = LocalDate.of(today.year, 12, 31)
            return ChronoUnit.DAYS.between(today, endOfYear)
        }

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val today = LocalDate.now()
            val year = today.year
            val leap = isLeapYear(year)
            val totalDaysInYear = if (leap) 366 else 365

            // Excluding today (e.g. "155 days left")
            val daysLeftExcludingToday = daysLeftInYear(today)
            // Including today (e.g. "156 if you count today")
            val daysLeftIncludingToday = daysLeftExcludingToday + 1

            // Weeks left, based on the excluding-today count
            val weeksLeft = daysLeftExcludingToday / 7
            val extraDays = daysLeftExcludingToday % 7

            // Current day-of-year, e.g. "day 210 of 365"
            val dayNumber = today.dayOfYear

            val views = RemoteViews(context.packageName, R.layout.widget_days_left)

            views.setTextViewText(R.id.tv_year, year.toString())

            views.setTextViewText(R.id.tv_days_left, daysLeftExcludingToday.toString())
            views.setTextViewText(
                R.id.tv_days_label,
                if (daysLeftExcludingToday == 1L) "day left" else "days left"
            )

            val weeksText = if (extraDays == 0L) "$weeksLeft weeks left" else "${weeksLeft}w ${extraDays}d left"
            val leapText = if (leap) "Leap year" else "Not a leap year"

            views.setTextViewText(
                R.id.tv_stats_row1,
                "$daysLeftIncludingToday days incl. today  •  $weeksText"
            )

            views.setTextViewText(
                R.id.tv_stats_row2,
                "Day $dayNumber of $totalDaysInYear  •  $leapText"
            )

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        /** Schedules the widget to refresh shortly after midnight so it stays accurate. */
        @SuppressLint("ScheduleExactAlarm")
        fun scheduleMidnightUpdate(context: Context) {
            val intent = Intent(context, DaysLeftWidgetProvider::class.java).apply {
                action = ACTION_UPDATE
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val tomorrow = LocalDate.now().plusDays(1)
            val triggerAtMillis = tomorrow.atStartOfDay(java.time.ZoneId.systemDefault())
                .toInstant().toEpochMilli() + 1000 // 1 sec after midnight

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }

        /**
         * Prompts the user to exempt this app from battery optimization, if it
         * isn't already exempt. Needed on real devices (Samsung/Xiaomi/OnePlus, etc.)
         * where OEM battery managers can delay or drop the midnight alarm — this
         * doesn't happen on emulators, which is why the widget can lag by a day
         * only on physical hardware.
         */
        fun requestIgnoreBatteryOptimizations(context: Context) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val alreadyExempt = powerManager.isIgnoringBatteryOptimizations(context.packageName)
            if (alreadyExempt) return

            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                // Some OEMs (notably MIUI) block this intent outright; fail silently
                // rather than crash — the widget still works, just less reliably.
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
        scheduleMidnightUpdate(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        Log.d("DaysLeftWidget", "onReceive fired with action: ${intent.action}")

        val relevantAction = intent.action == ACTION_UPDATE ||
                intent.action == Intent.ACTION_DATE_CHANGED ||
                intent.action == Intent.ACTION_TIME_CHANGED ||
                intent.action == Intent.ACTION_TIMEZONE_CHANGED ||
                intent.action == Intent.ACTION_BOOT_COMPLETED

        if (relevantAction) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = android.content.ComponentName(context, DaysLeftWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId)
            }
            scheduleMidnightUpdate(context)
        }
    }

    override fun onEnabled(context: Context) {
        scheduleMidnightUpdate(context)
        requestIgnoreBatteryOptimizations(context)
    }
}