package com.finlog

import com.finlog.data.model.*
import org.junit.Assert.*
import org.junit.Test

class FinLogTest {

    @Test
    fun `budget percentage calculates correctly`() {
        val b = Budget(categoryId="cat_food", categoryName="Food", limitAmount=2000.0, spentAmount=1600.0)
        assertEquals(80.0, b.percentage, 0.01)
        assertTrue(b.isNearLimit)
        assertFalse(b.isOverBudget)
    }

    @Test
    fun `budget over limit detection`() {
        val b = Budget(limitAmount=500.0, spentAmount=600.0)
        assertTrue(b.isOverBudget)
        assertTrue(b.isNearLimit)
    }

    @Test
    fun `goal progress calculates correctly`() {
        val g = Goal(name="Holiday", targetAmount=10000.0, currentAmount=5000.0)
        assertEquals(50.0, g.progress, 0.01)
        assertFalse(g.isCompleted)
    }

    @Test
    fun `goal is completed when target reached`() {
        val g = Goal(targetAmount=5000.0, currentAmount=5000.0)
        assertTrue(g.isCompleted)
        assertEquals(100.0, g.progress, 0.01)
    }

    @Test
    fun `password validation rules`() {
        fun valid(pw: String) = pw.length >= 8 && pw.any { it.isUpperCase() } && pw.any { it.isDigit() }
        assertFalse(valid("password"))
        assertFalse(valid("Password"))
        assertFalse(valid("password1"))
        assertTrue(valid("Password1"))
    }

    @Test
    fun `default categories count is 12`() {
        assertEquals(12, DEFAULT_CATEGORIES.size)
    }

    @Test
    fun `all required category names present`() {
        val names = DEFAULT_CATEGORIES.map { it.name }
        listOf("Food","Transport","Housing","Entertainment","Health","Education",
               "Shopping","Subscriptions","Utilities","Savings","Investment","Other")
            .forEach { assertTrue("Missing: $it", it in names) }
    }

    @Test
    fun `wallet transfer balance delta logic`() {
        val from = 5000.0; val to = 1000.0; val amount = 500.0
        assertEquals(4500.0, from - amount, 0.01)
        assertEquals(1500.0, to   + amount, 0.01)
    }

    @Test
    fun `transaction type constants are correct`() {
        assertEquals("INCOME",   Transaction.TYPE_INCOME)
        assertEquals("EXPENSE",  Transaction.TYPE_EXPENSE)
        assertEquals("TRANSFER", Transaction.TYPE_TRANSFER)
    }
}
