
package com.example.daysleftwidget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class DaysLeftWidgetProvider : AppWidgetProvider() {

    companion object {

        const val ACTION_UPDATE =
            "com.example.daysleftwidget.ACTION_UPDATE"

        private const val TAG = "DaysLeftWidget"

        // Correct leap-year calculation
        fun isLeapYear(year: Int): Boolean {
            return (year % 4 == 0 && year % 100 != 0) ||
                    (year % 400 == 0)
        }

        // Days remaining after today
        fun daysLeftInYear(today: LocalDate): Long {
            val endOfYear = LocalDate.of(today.year, 12, 31)
            return ChronoUnit.DAYS.between(today, endOfYear)
        }

        // Update individual widget
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {

            val today = LocalDate.now()
            val year = today.year

            val leap = isLeapYear(year)
            val totalDaysInYear = if (leap) 366 else 365

            // Excluding today
            val daysLeftExcludingToday = daysLeftInYear(today)

            // Including today
            val daysLeftIncludingToday =
                daysLeftExcludingToday + 1

            // Weeks remaining
            val weeksLeft = daysLeftExcludingToday / 7
            val extraDays = daysLeftExcludingToday % 7

            // Current day number
            val dayNumber = today.dayOfYear

            val views = RemoteViews(
                context.packageName,
                R.layout.widget_days_left
            )

            views.setTextViewText(
                R.id.tv_year,
                year.toString()
            )

            views.setTextViewText(
                R.id.tv_days_left,
                daysLeftExcludingToday.toString()
            )

            views.setTextViewText(
                R.id.tv_days_label,
                if (daysLeftExcludingToday == 1L)
                    "day left"
                else
                    "days left"
            )

            val weeksText =
                if (extraDays == 0L) {
                    "$weeksLeft weeks left"
                } else {
                    "${weeksLeft}w ${extraDays}d left"
                }

            val leapText =
                if (leap) "Leap year" else "Not a leap year"

            views.setTextViewText(
                R.id.tv_stats_row1,
                "$daysLeftIncludingToday days incl. today  •  $weeksText"
            )

            views.setTextViewText(
                R.id.tv_stats_row2,
                "Day $dayNumber of $totalDaysInYear  •  $leapText"
            )

            appWidgetManager.updateAppWidget(
                appWidgetId,
                views
            )
        }

        // Create the pending intent used for automatic updates
        private fun getUpdatePendingIntent(
            context: Context
        ): PendingIntent {

            val intent = Intent(
                context,
                DaysLeftWidgetProvider::class.java
            ).apply {
                action = ACTION_UPDATE
            }

            return PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
            )
        }

        // Schedule automatic midnight refresh
        fun scheduleMidnightUpdate(context: Context) {

            val alarmManager =
                context.getSystemService(
                    Context.ALARM_SERVICE
                ) as AlarmManager

            val pendingIntent =
                getUpdatePendingIntent(context)

            val tomorrow = LocalDate.now().plusDays(1)

            val triggerAtMillis =
                tomorrow
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli() + 1000

            try {

                // Use exact alarm when permission is available
                if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    alarmManager.canScheduleExactAlarms()
                ) {

                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )

                } else {

                    // Fallback: no special exact-alarm permission required
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }

            } catch (e: SecurityException) {

                Log.w(
                    TAG,
                    "Exact alarm unavailable. Using fallback.",
                    e
                )

                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        }

        // Update all installed widgets
        fun updateAllWidgets(context: Context) {

            val manager =
                AppWidgetManager.getInstance(context)

            val componentName = ComponentName(
                context,
                DaysLeftWidgetProvider::class.java
            )

            val widgetIds =
                manager.getAppWidgetIds(componentName)

            for (widgetId in widgetIds) {

                updateAppWidget(
                    context,
                    manager,
                    widgetId
                )
            }
        }

        // Cancel scheduled alarm when no widgets remain
        fun cancelMidnightUpdate(context: Context) {

            val alarmManager =
                context.getSystemService(
                    Context.ALARM_SERVICE
                ) as AlarmManager

            alarmManager.cancel(
                getUpdatePendingIntent(context)
            )
        }
    }

    // Called when Android updates the widget
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {

        for (appWidgetId in appWidgetIds) {

            updateAppWidget(
                context,
                appWidgetManager,
                appWidgetId
            )
        }

        scheduleMidnightUpdate(context)
    }

    // Handle automatic and system broadcasts
    override fun onReceive(
        context: Context,
        intent: Intent
    ) {

        super.onReceive(context, intent)

        Log.d(
            TAG,
            "Received action: ${intent.action}"
        )

        when (intent.action) {

            ACTION_UPDATE,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_BOOT_COMPLETED -> {

                updateAllWidgets(context)

                // Recalculate next midnight using current date/timezone
                scheduleMidnightUpdate(context)
            }
        }
    }

    // Called when the first widget is added
    override fun onEnabled(context: Context) {

        super.onEnabled(context)

        updateAllWidgets(context)

        scheduleMidnightUpdate(context)
    }

    // Called when the last widget is removed
    override fun onDisabled(context: Context) {

        super.onDisabled(context)

        cancelMidnightUpdate(context)
    }
}
