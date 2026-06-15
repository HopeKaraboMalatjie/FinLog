package com.finlog.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.finlog.data.model.Transaction
import com.finlog.data.model.Category
import com.finlog.data.model.Budget
import com.finlog.data.model.Goal
import com.finlog.data.model.Wallet
import kotlinx.coroutines.flow.Flow

data class CategoryTotal(val categoryName: String, val total: Double)

@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(t: Transaction): Long
    @Update suspend fun update(t: Transaction): Int
    @Delete suspend fun delete(t: Transaction): Int
    @Query("SELECT * FROM transactions ORDER BY date DESC") fun getAll(): Flow<List<Transaction>>
    @Query("SELECT * FROM transactions WHERE date BETWEEN :from AND :to ORDER BY date DESC")
    fun getByDateRange(from: Long, to: Long): Flow<List<Transaction>>
    @Query("SELECT * FROM transactions WHERE categoryId = :catId ORDER BY date DESC")
    fun getByCategory(catId: String): Flow<List<Transaction>>
    @Query("SELECT * FROM transactions WHERE type = :type ORDER BY date DESC")
    fun getByType(type: String): Flow<List<Transaction>>
    @Query("SELECT COALESCE(SUM(amount),0.0) FROM transactions WHERE categoryId=:catId AND type='EXPENSE' AND date BETWEEN :from AND :to")
    suspend fun sumExpense(catId: String, from: Long, to: Long): Double
    @Query("SELECT COALESCE(SUM(amount),0.0) FROM transactions WHERE type=:type AND date BETWEEN :from AND :to")
    suspend fun sumByType(type: String, from: Long, to: Long): Double
    @Query("SELECT * FROM transactions WHERE id=:id LIMIT 1") suspend fun getById(id: String): Transaction?
    @Query("DELETE FROM transactions WHERE id = :id") suspend fun deleteById(id: String): Int
    
    @Query("""
        SELECT 
            (SELECT COALESCE(SUM(amount), 0.0) FROM transactions WHERE type = 'INCOME') - 
            (SELECT COALESCE(SUM(amount), 0.0) FROM transactions WHERE type = 'EXPENSE')
    """)
    fun totalCalculatedBalance(): Flow<Double>

    @Query("""
        SELECT categoryName, COALESCE(SUM(amount), 0.0) as total
        FROM transactions
        WHERE type = 'EXPENSE' AND date BETWEEN :from AND :to
        GROUP BY categoryId
        ORDER BY total DESC
    """)
    suspend fun getCategoryTotals(from: Long, to: Long): List<CategoryTotal>
}

@Dao
interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(c: Category): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAll(list: List<Category>): List<Long>
    @Delete suspend fun delete(c: Category): Int
    @Query("SELECT * FROM categories ORDER BY isCustom ASC, name ASC") fun getAll(): Flow<List<Category>>
}

@Dao
interface BudgetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(b: Budget): Long
    @Delete suspend fun delete(b: Budget): Int
    @Query("SELECT * FROM budgets WHERE month=:month AND year=:year") fun getForMonth(month: Int, year: Int): Flow<List<Budget>>
    @Query("SELECT * FROM budgets WHERE month=:month AND year=:year") suspend fun getForMonthList(month: Int, year: Int): List<Budget>
    @Query("SELECT * FROM budgets WHERE categoryId=:catId AND month=:month AND year=:year LIMIT 1")
    suspend fun getForCategory(catId: String, month: Int, year: Int): Budget?
    @Query("UPDATE budgets SET spentAmount=:amount WHERE id=:id") suspend fun updateSpent(id: String, amount: Double): Int
}

@Dao
interface GoalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(g: Goal): Long
    @Update suspend fun update(g: Goal): Int
    @Delete suspend fun delete(g: Goal): Int
    @Query("SELECT * FROM goals ORDER BY createdAt DESC") fun getAll(): Flow<List<Goal>>
    @Query("SELECT * FROM goals WHERE id=:id LIMIT 1") suspend fun getById(id: String): Goal?
}

@Dao
interface WalletDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(w: Wallet): Long
    @Update suspend fun update(w: Wallet): Int
    @Delete suspend fun delete(w: Wallet): Int
    @Query("SELECT * FROM wallets ORDER BY name ASC") fun getAll(): Flow<List<Wallet>>
    @Query("SELECT * FROM wallets WHERE id=:id LIMIT 1") suspend fun getById(id: String): Wallet?
    @Query("SELECT COALESCE(SUM(balance),0.0) FROM wallets") fun totalBalance(): Flow<Double>
    @Query("UPDATE wallets SET balance=balance+:delta WHERE id=:id") suspend fun adjustBalance(id: String, delta: Double): Int
}

@Database(
    entities = [Transaction::class, Category::class, Budget::class, Goal::class, Wallet::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun budgetDao(): BudgetDao
    abstract fun goalDao(): GoalDao
    abstract fun walletDao(): WalletDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE budgets ADD COLUMN minGoal REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE budgets ADD COLUMN categoryEmoji TEXT NOT NULL DEFAULT '💰'")
            }
        }

        fun get(ctx: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            Room.databaseBuilder(ctx.applicationContext, AppDatabase::class.java, "finlog.db")
                .addMigrations(MIGRATION_1_2)
                .build().also { INSTANCE = it }
        }
    }
}
