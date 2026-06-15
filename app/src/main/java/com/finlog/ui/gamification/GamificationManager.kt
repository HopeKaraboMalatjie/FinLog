package com.finlog.ui.gamification

import android.content.Context
import android.content.SharedPreferences
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.*

data class Badge(
    val id: String,
    val name: String,
    val emoji: String,
    val description: String,
    val isEarned: Boolean = false
)

object GamificationManager {

    private const val PREFS = "finlog_gamification"

    fun getPrefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ── Streak tracking ─────────────────────────────────────────────
    fun recordLogToday(ctx: Context) {
        val prefs = getPrefs(ctx)
        val today = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val lastLog = prefs.getString("last_log_date", "")
        val streak = prefs.getInt("log_streak", 0)

        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterday = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(cal.time)

        val newStreak = when (lastLog) {
            today     -> streak                 // already logged today
            yesterday -> streak + 1             // consecutive day
            else      -> 1                      // streak broken, restart
        }
        prefs.edit().putString("last_log_date", today).putInt("log_streak", newStreak).apply()

        // Unlock streak badges
        if (newStreak >= 3  && !isBadgeEarned(ctx, "streak_3"))  unlockBadge(ctx, "streak_3")
        if (newStreak >= 7  && !isBadgeEarned(ctx, "streak_7"))  unlockBadge(ctx, "streak_7")
        if (newStreak >= 30 && !isBadgeEarned(ctx, "streak_30")) unlockBadge(ctx, "streak_30")
    }

    fun getStreak(ctx: Context) = getPrefs(ctx).getInt("log_streak", 0)

    // ── Badge management ─────────────────────────────────────────────
    fun unlockBadge(ctx: Context, badgeId: String): Boolean {
        if (isBadgeEarned(ctx, badgeId)) return false
        getPrefs(ctx).edit().putBoolean("badge_$badgeId", true).apply()
        return true
    }

    fun isBadgeEarned(ctx: Context, badgeId: String): Boolean =
        getPrefs(ctx).getBoolean("badge_$badgeId", false)

    // First transaction badge — call this when first transaction is added
    fun checkFirstTransaction(ctx: Context, txCount: Int): Badge? {
        if (txCount >= 50 && unlockBadge(ctx, "tx_50")) return getBadge(ctx, "tx_50")
        if (txCount >= 10 && unlockBadge(ctx, "tx_10")) return getBadge(ctx, "tx_10")
        if (txCount >= 1 && unlockBadge(ctx, "first_tx")) return getBadge(ctx, "first_tx")
        return null
    }

    private fun getBadge(ctx: Context, id: String): Badge? {
        return getAllBadges(ctx).find { it.id == id }
    }

    // Budget master — call this when all budgets are within limit
    fun checkBudgetMaster(ctx: Context, allWithinBudget: Boolean) {
        if (allWithinBudget && !isBadgeEarned(ctx, "budget_master")) unlockBadge(ctx, "budget_master")
    }

    // Goal completed
    fun checkGoalCompleted(ctx: Context) {
        if (!isBadgeEarned(ctx, "first_goal")) unlockBadge(ctx, "first_goal")
    }

    // ── All badges definition ─────────────────────────────────────────
    fun getAllBadges(ctx: Context): List<Badge> = listOf(
        Badge("first_tx",      "First Step",      "🚀", "Log your first transaction",         isBadgeEarned(ctx, "first_tx")),
        Badge("tx_10",         "Getting Started",  "📊", "Log 10 transactions",               isBadgeEarned(ctx, "tx_10")),
        Badge("tx_50",         "Power Tracker",    "⚡", "Log 50 transactions",               isBadgeEarned(ctx, "tx_50")),
        Badge("streak_3",      "On a Roll",        "🔥", "Log expenses 3 days in a row",      isBadgeEarned(ctx, "streak_3")),
        Badge("streak_7",      "Week Warrior",     "🗓️", "7-day logging streak",             isBadgeEarned(ctx, "streak_7")),
        Badge("streak_30",     "Month Master",     "🏆", "30-day logging streak",             isBadgeEarned(ctx, "streak_30")),
        Badge("budget_master", "Budget Boss",      "💎", "Keep all categories within budget", isBadgeEarned(ctx, "budget_master")),
        Badge("first_goal",    "Dream Chaser",     "🎯", "Complete your first savings goal",  isBadgeEarned(ctx, "first_goal")),
    )

    // ── Points system ─────────────────────────────────────────────────
    fun addPoints(ctx: Context, points: Int) {
        val prefs = getPrefs(ctx)
        val current = prefs.getInt("total_points", 0)
        prefs.edit().putInt("total_points", current + points).apply()
    }
    fun getPoints(ctx: Context) = getPrefs(ctx).getInt("total_points", 0)

    fun getLevelName(points: Int) = when {
        points < 100  -> "Beginner 🌱"
        points < 300  -> "Saver 💰"
        points < 600  -> "Planner 📋"
        points < 1000 -> "Expert 🎯"
        else          -> "Finance Pro 🏆"
    }

    fun showBadgeUnlocked(fragment: Fragment, badge: Badge) {
        MaterialAlertDialogBuilder(fragment.requireContext())
            .setTitle("🎉 Badge Unlocked!")
            .setMessage("${badge.emoji} ${badge.name}\n\n${badge.description}")
            .setPositiveButton("Awesome! 🚀") { d, _ -> d.dismiss() }
            .show()
    }
}
