# FinLog 🌿

> Financial Logging — Track, Plan, Grow  
> Personal Budgeting Android App — OPSC6311

## Demo Video
🎬 [Watch on YouTube](https://youtube.com/shorts/ZQ70Ww_9lGc?feature=shared)
## GitHub
[GitHub link](https://github.com/HopeKaraboMalatjie/FinLog)
## Team
| Name | Student No. |
|------|-------------|
| Hope Malatjie | ST10444867 |
| Chantal Mafa | ST10444319 |
| Karabo Mojapelo | ST10436116 |
| Keabetswe Modiba | ST10438507 |

## Recent Major Improvements (June 2026)
The application has recently undergone a major architectural overhaul to ensure maximum reliability and real-time responsiveness:

- **Reactive Architecture**: Fully refactored ViewModels using `switchMap` and repository flows. All screens (Dashboard, Reports, Calendar) now update instantly upon any database change.
- **Proper Financial Logic**: Refactored the "Total Balance" calculation to follow standard accounting principles: `Total Income - Total Expenses`.
- **Smart Transaction Deletion**: Guaranteed record removal using `deleteById` with atomic wallet balance reversal (intelligently handling Income, Expenses, and Transfers).
- **Stability & Performance**: Eliminated memory leaks by binding coroutines to the `lifecycleScope` and optimized database queries for speed.
- **Brand Identity**: Updated app icon and launcher to the signature vibrant **Leaf** design 🌿.

## How to Run
1. Open Android Studio → File → Open → select the FinLog folder
2. Wait for Gradle sync (needs internet once to download dependencies)
3. Press Run ▶

## Tech Stack
- **Language**: Kotlin + Kotlin DSL (`.gradle.kts`)
- **Database**: Room Database (SQLite offline storage with reactive Flow/LiveData)
- **Architecture**: MVVM + Repository + Reactive Binding
- **UI**: Jetpack Navigation, Material Design 3, Fragment-based Navigation
- **Charts**: MPAndroidChart (Bar + Pie + Heatmap)
- **Utilities**: Glide (Images), NotificationManager (Alerts)

## Running Tests
```bash
./gradlew test
```

## Core Features
- **Smart Transactions**: Log income/expenses with photos, time tracking, and smart wallet adjustment.
- **Budgeting Engine**: Color-coded progress bars with reactive Health Scores and real-time status alerts.
- **Financial Goals**: Set targets and track progress with milestone celebrations (25% to 100%).
- **Interactive Reports**: 6-month historical overview and category-wise spending breakdowns.
- **Calendar Heatmap**: Visualize spending patterns across the month with a reactive daily summary.
- **Wallet Management**: Manage multiple wallets and perform internal transfers with balanced accounting.
- **Gamification**: Earn badges and points for consistent logging and hitting budget targets.
