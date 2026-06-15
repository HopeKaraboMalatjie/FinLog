package com.finlog.data.repository

import android.util.Log
import com.finlog.data.local.*
import com.finlog.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.Calendar

private const val TAG = "FinLogRepo"

class Repository(
    private val txDao: TransactionDao,
    private val catDao: CategoryDao,
    private val budgetDao: BudgetDao,
    private val goalDao: GoalDao,
    private val walletDao: WalletDao
) {
    fun getTransactions(): Flow<List<Transaction>> = txDao.getAll()
    fun getByDateRange(from: Long, to: Long) = txDao.getByDateRange(from, to)
    fun getByCategory(catId: String) = txDao.getByCategory(catId)
    fun getByType(type: String) = txDao.getByType(type)
    suspend fun getById(id: String) = txDao.getById(id)

    suspend fun addTransaction(t: Transaction) {
        txDao.insert(t)
        if (t.walletId.isNotEmpty()) {
            walletDao.adjustBalance(t.walletId, calculateDelta(t))
        }
        refreshBudget(t.categoryId, t.date)
    }

    suspend fun updateTransaction(t: Transaction) {
        val old = txDao.getById(t.id) ?: return
        
        // Revert old wallet impact
        if (old.walletId.isNotEmpty()) {
            walletDao.adjustBalance(old.walletId, -calculateDelta(old))
        }
        
        // Apply new wallet impact
        if (t.walletId.isNotEmpty()) {
            walletDao.adjustBalance(t.walletId, calculateDelta(t))
        }
        
        txDao.update(t)
        refreshBudget(old.categoryId, old.date)
        if (t.categoryId != old.categoryId) refreshBudget(t.categoryId, t.date)
    }

    suspend fun deleteTransaction(t: Transaction) {
        val fresh = txDao.getById(t.id) ?: return
        
        // Revert wallet impact before deleting
        if (fresh.walletId.isNotEmpty()) {
            walletDao.adjustBalance(fresh.walletId, -calculateDelta(fresh))
        }

        txDao.deleteById(fresh.id)
        refreshBudget(fresh.categoryId, fresh.date)
    }

    private fun calculateDelta(t: Transaction): Double {
        return when (t.type) {
            Transaction.TYPE_INCOME -> t.amount
            Transaction.TYPE_EXPENSE -> -t.amount
            Transaction.TYPE_TRANSFER -> {
                if (t.description.contains("Transfer from", ignoreCase = true)) t.amount
                else -t.amount // "Transfer to"
            }
            else -> 0.0
        }
    }

    private suspend fun refreshBudget(catId: String, dateMs: Long) {
        val cal = Calendar.getInstance().apply { timeInMillis = dateMs }
        val m = cal.get(Calendar.MONTH) + 1; val y = cal.get(Calendar.YEAR)
        val budget = budgetDao.getForCategory(catId, m, y) ?: return
        
        // ROBUST DATES: Start of first day to end of last day of the month
        val start = Calendar.getInstance().apply { 
            set(y, m-1, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0) 
        }.timeInMillis
        val end = Calendar.getInstance().apply { 
            set(y, m-1, cal.getActualMaximum(Calendar.DAY_OF_MONTH), 23, 59, 59)
            set(Calendar.MILLISECOND, 999) 
        }.timeInMillis
        
        budgetDao.updateSpent(budget.id, txDao.sumExpense(catId, start, end))
    }

    suspend fun refreshAllBudgetsForMonth(month: Int, year: Int) {
        val start = Calendar.getInstance().apply { 
            set(year, month-1, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0) 
        }.timeInMillis
        val cal = Calendar.getInstance().apply { set(year, month-1, 1) }
        val end = Calendar.getInstance().apply { 
            set(year, month-1, cal.getActualMaximum(Calendar.DAY_OF_MONTH), 23, 59, 59)
            set(Calendar.MILLISECOND, 999) 
        }.timeInMillis
        
        val budgets = budgetDao.getForMonthList(month, year)
        budgets.forEach { budget ->
            budgetDao.updateSpent(budget.id, txDao.sumExpense(budget.categoryId, start, end))
        }
    }

    fun getCategories() = catDao.getAll()
    suspend fun seedDefaultCategories() {
        catDao.insertAll(DEFAULT_CATEGORIES)
        // Add a default wallet if none exists to ensure balance shows
        val wallets = walletDao.getAll().first()
        if (wallets.isEmpty()) {
            walletDao.insert(Wallet(name = "Main Wallet", balance = 0.0))
        }
    }
    suspend fun addCategory(c: Category) = catDao.insert(c)
    suspend fun deleteCategory(c: Category) = catDao.delete(c)

    fun getBudgets(month: Int, year: Int) = budgetDao.getForMonth(month, year)
    suspend fun saveBudget(b: Budget) { budgetDao.insert(b); Log.d(TAG, "Budget: ${b.categoryName}") }
    suspend fun deleteBudget(b: Budget) = budgetDao.delete(b)

    fun getGoals() = goalDao.getAll()
    suspend fun saveGoal(g: Goal) { goalDao.insert(g); Log.d(TAG, "Goal: ${g.name}") }
    suspend fun deleteGoal(g: Goal) = goalDao.delete(g)
    suspend fun contributeToGoal(id: String, amount: Double) {
        val g = goalDao.getById(id) ?: return
        goalDao.update(g.copy(currentAmount = g.currentAmount + amount))
        Log.d(TAG, "Contributed $amount to ${g.name}")
    }

    fun getWallets() = walletDao.getAll()
    
    // Proper Financial Total: Calculated as Total Income - Total Expense
    fun getTotalBalance() = txDao.totalCalculatedBalance()

    suspend fun saveWallet(w: Wallet) { walletDao.insert(w); Log.d(TAG, "Wallet: ${w.name}") }
    suspend fun deleteWallet(w: Wallet) = walletDao.delete(w)

    suspend fun transfer(fromId: String, toId: String, amount: Double) {
        val from = walletDao.getById(fromId) ?: return
        val to   = walletDao.getById(toId)   ?: return
        walletDao.adjustBalance(fromId, -amount)
        walletDao.adjustBalance(toId,  +amount)
        txDao.insert(Transaction(amount=amount, type=Transaction.TYPE_TRANSFER, walletId=fromId,
            description="Transfer to ${to.name}", categoryId="cat_other", categoryName="Transfer"))
        txDao.insert(Transaction(amount=amount, type=Transaction.TYPE_TRANSFER, walletId=toId,
            description="Transfer from ${from.name}", categoryId="cat_other", categoryName="Transfer"))
        Log.d(TAG, "Transfer $amount from ${from.name} to ${to.name}")
    }

    suspend fun monthlyTotal(type: String, month: Int, year: Int): Double {
        val s = Calendar.getInstance().apply { 
            set(year, month-1, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0) 
        }.timeInMillis
        val e = Calendar.getInstance().apply { 
            set(year, month-1, 1) // Ensure it's on the correct month before getActualMax
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
        return txDao.sumByType(type, s, e)
    }

    suspend fun getCategoryTotals(from: Long, to: Long) = txDao.getCategoryTotals(from, to)
}
