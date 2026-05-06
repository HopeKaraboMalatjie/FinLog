package com.finlog.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val amount: Double = 0.0,
    val type: String = Transaction.TYPE_EXPENSE,
    val categoryId: String = "",
    val categoryName: String = "",
    val description: String = "",
    val date: Long = System.currentTimeMillis(),
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long = System.currentTimeMillis(),
    val paymentMethod: String = "CASH",
    val walletId: String = "",
    val photoPath: String = "",
    val isRecurring: Boolean = false,
    val recurringInterval: String = "NONE"
) {
    companion object {
        const val TYPE_INCOME   = "INCOME"
        const val TYPE_EXPENSE  = "EXPENSE"
        const val TYPE_TRANSFER = "TRANSFER"
    }
}

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val emoji: String = "💰",
    val colorHex: String = "#1A6B4A",
    val isCustom: Boolean = false
)

val DEFAULT_CATEGORIES = listOf(
    Category("cat_food",          "Food",          "🍔", "#EF4444"),
    Category("cat_transport",     "Transport",     "🚗", "#F59E0B"),
    Category("cat_housing",       "Housing",       "🏠", "#3B82F6"),
    Category("cat_entertainment", "Entertainment", "🎮", "#8B5CF6"),
    Category("cat_health",        "Health",        "💊", "#EC4899"),
    Category("cat_education",     "Education",     "📚", "#06B6D4"),
    Category("cat_shopping",      "Shopping",      "🛍", "#F97316"),
    Category("cat_subscriptions", "Subscriptions", "📱", "#6366F1"),
    Category("cat_utilities",     "Utilities",     "⚡", "#84CC16"),
    Category("cat_savings",       "Savings",       "🐷", "#22C55E"),
    Category("cat_investment",    "Investment",    "📈", "#10B981"),
    Category("cat_other",         "Other",         "💰", "#9CA3AF")
)

@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val categoryId: String = "",
    val categoryName: String = "",
    val limitAmount: Double = 0.0,
    val spentAmount: Double = 0.0,
    val month: Int = 0,
    val year: Int = 0
) {
    val percentage: Double   get() = if (limitAmount > 0) (spentAmount / limitAmount) * 100.0 else 0.0
    val isOverBudget: Boolean get() = spentAmount >= limitAmount
    val isNearLimit: Boolean  get() = percentage >= 80.0
}

@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val emoji: String = "🎯",
    val targetAmount: Double = 0.0,
    val currentAmount: Double = 0.0,
    val targetDateMs: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
) {
    val progress: Double     get() = if (targetAmount > 0) (currentAmount / targetAmount) * 100.0 else 0.0
    val isCompleted: Boolean  get() = currentAmount >= targetAmount
    fun monthlyRequired(): Double {
        if (targetDateMs == 0L) return 0.0
        val months = ((targetDateMs - System.currentTimeMillis()) / (1000L * 60 * 60 * 24 * 30)).toInt()
        val remaining = targetAmount - currentAmount
        return if (months > 0) remaining / months else remaining
    }
}

@Entity(tableName = "wallets")
data class Wallet(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val emoji: String = "💳",
    val balance: Double = 0.0,
    val currency: String = "ZAR"
)
