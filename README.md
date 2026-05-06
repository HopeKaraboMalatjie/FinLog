# FinLog 💚

> Financial Logging — Track, Plan, Grow  
> Personal Budgeting Android App — OPSC6311

## Demo Video
🎬 [Watch on YouTube](https://youtu.be/-xWWp-MzfEA?si=W8Tbet2LKo7MZ_t6)

## Team
| Name | Student No. |
|------|-------------|
| Hope Malatjie | ST10444867 |
| Chantal Mafa | ST10444319 |
| Karabo Mojapelo | ST10436116 |
| Keabetswe Modiba | ST10438507 |

## How to Run
1. Open Android Studio → File → Open → select the FinLog folder
2. Wait for Gradle sync (needs internet once to download dependencies)
3. Press Run ▶

## Tech Stack
- Kotlin + Kotlin DSL (`.gradle.kts`)
- Room Database (SQLite offline storage)
- MVVM + Repository pattern
- Jetpack Navigation Component
- MPAndroidChart (bar + pie charts)
- Glide (image loading)
- Material Design 3

## Running Tests
```bash
./gradlew test
```

## Features
- Register / Login (offline with SharedPreferences)
- Add expenses with date, start time, end time, description, category, optional photo
- Budget tracker with colour-coded progress bars + Health Score
- Savings goals with milestone badges (25%, 50%, 75%, 100%)
- Reports: 6-month bar chart + Spending DNA pie chart
- Multi-wallet management with transfers
- Monthly spending min/max goals
- GitHub Actions CI (auto-runs tests + builds APK on push)
