package com.finlog.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.finlog.databinding.ActivitySplashBinding
import com.finlog.ui.auth.AuthActivity

class SplashActivity : AppCompatActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val b = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(b.root)
        val loggedIn = getSharedPreferences("finlog_prefs", 0).getBoolean("logged_in", false)
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, if (loggedIn) MainActivity::class.java else AuthActivity::class.java))
            finish()
        }, 1800L)
    }
}
