package com.finlog.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.finlog.R
import com.finlog.databinding.ActivityMainBinding
import com.finlog.ui.auth.AuthActivity

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.navHostFragment) as NavHostFragment
        val navController = navHostFragment.navController
        
        b.bottomNav.setupWithNavController(navController)
        b.navigationView.setupWithNavController(navController)

        // Sync header data reactively
        val header = b.navigationView.getHeaderView(0)
        val prefs = getSharedPreferences("finlog_prefs", 0)
        
        val updateHeader = {
            header.findViewById<TextView>(R.id.tvNavHeaderName).text = prefs.getString("display_name", "FinLog User")
            header.findViewById<TextView>(R.id.tvNavHeaderEmail).text = prefs.getString("email", "user@finlog.com")
        }
        updateHeader()

        // Reactive: Update header when preferences change
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "display_name" || key == "email") updateHeader()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)

        b.navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_logout -> {
                    prefs.edit().putBoolean("logged_in", false).apply()
                    startActivity(Intent(this, AuthActivity::class.java))
                    finish()
                }
                else -> {
                    navController.navigate(item.itemId)
                }
            }
            b.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        navController.addOnDestinationChangedListener { _, dest, _ ->
            b.bottomNav.visibility = if (dest.id in listOf(
                R.id.addEditTransactionFragment,
                R.id.transactionDetailFragment,
                R.id.profileFragment,
            )) View.GONE else View.VISIBLE
        }
    }

    fun openDrawer() {
        b.drawerLayout.openDrawer(GravityCompat.START)
    }
}
