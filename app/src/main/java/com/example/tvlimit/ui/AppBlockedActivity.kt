package com.example.tvlimit.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.example.tvlimit.R

class AppBlockedActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_blocked)

        val packageName = intent.getStringExtra("blocked_package")
        val tvDetails = findViewById<TextView>(R.id.tvBlockedDetails)
        if (packageName != null) {
            tvDetails.text = "The application '$packageName' is restricted for this profile."
        }

        findViewById<Button>(R.id.btnGoBack).setOnClickListener {
            goBack()
        }

        findViewById<Button>(R.id.btnSwitchProfile).setOnClickListener {
            val intent = Intent(this, ProfileSelectionActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
            finish()
        }
    }

    override fun onBackPressed() {
        goBack()
    }

    private fun goBack() {
        // Go to Home Screen
        val startMain = Intent(Intent.ACTION_MAIN)
        startMain.addCategory(Intent.CATEGORY_HOME)
        startMain.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(startMain)
        finish()
    }
}
