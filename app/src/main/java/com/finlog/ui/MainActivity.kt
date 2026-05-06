package com.finlog.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.finlog.R
import com.finlog.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        val nav = (supportFragmentManager.findFragmentById(R.id.navHostFragment) as NavHostFragment).navController
        b.bottomNav.setupWithNavController(nav)
        nav.addOnDestinationChangedListener { _, dest, _ ->
            b.bottomNav.visibility = if (dest.id in listOf(
                R.id.addEditTransactionFragment,
                R.id.transactionDetailFragment,
                R.id.profileFragment,
            )) View.GONE else View.VISIBLE
        }
    }
}
