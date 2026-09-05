# Days Left Widget

An Android home-screen widget that shows:
- Number of days left before the current year ends
- Whether the current year is a leap year

## How to use this

1. Open **Android Studio** → **New Project** → choose "Empty Activity" (or just open this folder directly as an existing project — it's already structured as one).
2. Copy these files into the matching locations in your project (paths already match a standard Android Studio layout):
   - `app/src/main/java/com/example/daysleftwidget/DaysLeftWidgetProvider.kt`
   - `app/src/main/res/layout/widget_days_left.xml`
   - `app/src/main/res/drawable/widget_background.xml`
   - `app/src/main/res/xml/days_left_widget_info.xml`
   - `app/src/main/res/values/strings.xml`
   - `app/src/main/AndroidManifest.xml`
   - `app/build.gradle.kts`, `build.gradle.kts`, `settings.gradle.kts`
3. Sync Gradle, then **Run** the app on a device/emulator once (this registers the widget).
4. Long-press your home screen → **Widgets** → find "Days Left Widget" → drag it onto your home screen.

## How it works

- `daysLeftInYear()` computes the difference between today and Dec 31 of the current year using `java.time.temporal.ChronoUnit`.
- `isLeapYear()` uses the standard rule: divisible by 4, but not by 100, unless also divisible by 400.
- The widget refreshes automatically:
  - Once every 3 hours as a system-enforced background fallback (`updatePeriodMillis`), since Android won't reliably fire faster than ~30 min anyway.
  - Precisely at midnight via an exact alarm (`AlarmManager.setExactAndAllowWhileIdle`), so the day count is always accurate first thing each morning.

## Customizing

- **Colors**: edit the gradient in `widget_background.xml`.
- **Size**: adjust `minWidth`/`minHeight`/`targetCellWidth`/`targetCellHeight` in `days_left_widget_info.xml`.
- **Text**: edit `widget_days_left.xml` — e.g. add the current date, or a progress bar for % of year elapsed.

## Requirements
- Android Studio (Koala or newer recommended)
- minSdk 26 (Android 8.0+) — required for `java.time` APIs used here without desugaring
