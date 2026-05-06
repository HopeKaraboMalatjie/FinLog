package com.finlog

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.util.Log
import com.finlog.data.local.AppDatabase
import com.finlog.data.repository.Repository

class FinLogApp : Application() {
    lateinit var repo: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.get(this)
        repo = Repository(db.transactionDao(), db.categoryDao(), db.budgetDao(), db.goalDao(), db.walletDao())
        createChannels()
        Log.d("FinLogApp", "Started")
    }

    private fun createChannels() {
        val mgr = getSystemService(NotificationManager::class.java)
        mgr.createNotificationChannel(NotificationChannel(CH_BUDGET, "Budget Alerts", NotificationManager.IMPORTANCE_HIGH))
        mgr.createNotificationChannel(NotificationChannel(CH_GOALS,  "Goal Milestones", NotificationManager.IMPORTANCE_DEFAULT))
    }

    companion object {
        const val CH_BUDGET = "ch_budget"
        const val CH_GOALS  = "ch_goals"
    }
}
