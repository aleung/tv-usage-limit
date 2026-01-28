package com.example.tvlimit.ui

import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.example.tvlimit.R
import com.example.tvlimit.receiver.AdminReceiver
import com.example.tvlimit.service.TimeTrackingService
import com.example.tvlimit.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var btnOverlay: Button
    private lateinit var btnAdmin: Button
    private lateinit var btnStartService: Button
    private lateinit var btnSwitchProfile: Button
    private lateinit var btnSettings: Button
    private lateinit var tvCurrentProfile: TextView
    private lateinit var tvDailyUsage: TextView
    private lateinit var tvTimeUntilRest: TextView

    // We can listen for broadcast changes to update UI immediately
    private val profileReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
             if (intent?.action == ProfileSelectionActivity.ACTION_PROFILE_CHANGED) {
                 updateCurrentProfileUI()
             }
        }
    }

    private val usageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == TimeTrackingService.ACTION_USAGE_UPDATE) {
                val daily = intent.getIntExtra(TimeTrackingService.EXTRA_DAILY_USAGE, 0)
                val sessionLimit = intent.getIntExtra(TimeTrackingService.EXTRA_SESSION_LIMIT, -1)
                val sessionUsage = intent.getIntExtra(TimeTrackingService.EXTRA_SESSION_USAGE, 0)
                val profileName = intent.getStringExtra(TimeTrackingService.EXTRA_PROFILE_NAME) ?: "Unknown"

                updateUsageUI(daily, sessionLimit, sessionUsage, profileName)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnOverlay = findViewById(R.id.btnOverlay)
        btnAdmin = findViewById(R.id.btnAdmin)
        btnStartService = findViewById(R.id.btnStartService)
        btnSwitchProfile = findViewById(R.id.btnSwitchProfile)
        btnSettings = findViewById(R.id.btnSettings)
        tvCurrentProfile = findViewById(R.id.tvCurrentProfile)
        tvDailyUsage = findViewById(R.id.tvDailyUsage)
        tvTimeUntilRest = findViewById(R.id.tvTimeUntilRest)

        btnOverlay.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName"))
                startActivityForResult(intent, 101)
            }
        }

        btnAdmin.setOnClickListener {
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val componentName = ComponentName(this, AdminReceiver::class.java)
            if (!dpm.isAdminActive(componentName)) {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "We need this to turn off the screen.")
                startActivityForResult(intent, 102)
            }
        }

        btnStartService.setOnClickListener {
            val intent = Intent(this, TimeTrackingService::class.java)
            startForegroundService(intent)
            Toast.makeText(this, "Service Started", Toast.LENGTH_SHORT).show()
        }

        btnSwitchProfile.setOnClickListener {
            startActivity(Intent(this, ProfileSelectionActivity::class.java))
        }

        btnSettings.setOnClickListener {
            showAdminPinDialog()
        }

        val filter = IntentFilter(ProfileSelectionActivity.ACTION_PROFILE_CHANGED)
        registerReceiver(profileReceiver, filter, Context.RECEIVER_NOT_EXPORTED)

        val usageFilter = IntentFilter(TimeTrackingService.ACTION_USAGE_UPDATE)
        // Service broadcast might be implicit or explicit. Since we are in same app, internal broadcast.
        // If we declared protected broadcast in manifest we might need permission, but local is fine.
        // Assuming we need RECEIVER_EXPORTED if we target Android 14 and it's a "system" broadcast?
        // No, it's our own custom broadcast. RECEIVER_NOT_EXPORTED is safer if possible.
        // Start with RECEIVER_NOT_EXPORTED for security, assuming service is in same process/app.
        registerReceiver(usageReceiver, usageFilter, Context.RECEIVER_NOT_EXPORTED)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(profileReceiver)
        unregisterReceiver(usageReceiver)
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
        updateCurrentProfileUI()
    }

    private fun checkPermissions() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val componentName = ComponentName(this, AdminReceiver::class.java)

        val hasOverlay = Settings.canDrawOverlays(this)
        val hasAdmin = dpm.isAdminActive(componentName)

        if (hasOverlay) {
            btnOverlay.visibility = View.GONE
        } else {
            btnOverlay.visibility = View.VISIBLE
        }

        if (hasAdmin) {
            btnAdmin.visibility = View.GONE
        } else {
            btnAdmin.visibility = View.VISIBLE
        }

        if (hasOverlay && hasAdmin) {
            // Permissions Good - Ensure service is running
             val intent = Intent(this, TimeTrackingService::class.java)
             startForegroundService(intent)
        }
    }

    private fun updateCurrentProfileUI() {
        // We need a way to know current profile.
        // For MVP, we can read what the Service thinks, or what ProfileSelectionActivity set.
        // Since we don't have SharedPrefs storage for ID yet (Service has internal state),
        // let's rely on retrieving it from DB if we stored it, or ask Service?
        // Actually ProfileSelectionActivity broadcasted the ID.
        // But on cold start, Service initializes to Child.
        // Let's implement a quick SharedPreferences read/write in ProfileSelectionActivity AND here.
        // Or better: make ProfileSelectionActivity save to SharedPrefs.
        // I'll add that logic here as a patch or assume it's done.
        // Wait, I didn't add SharedPrefs write to ProfileSelectionActivity.
        // I will add it to Service's receiver logic and have Service broadcast state or similar?
        // Simpler: MainActivity just says "Unknown" or I query DB for name if I save ID in Prefs.

        // Let's just say "Active" for now or try to fetch.
        // Actually, TimeTrackingService defaults to "Child".
        // Let's assume Child if nothing set.

        // Update: We now rely on Service Broadcast for real data.
        // But for immediate feedback on onResume, we still might want "Loading..."

        // tvCurrentProfile.text = "Current Profile: (Check Service)"
        // We will wait for broadcast or retain last known?
        // Let's leave reading from prefs for name if available to avoid "Loading..." flicker if possible.
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val name = prefs.getString("CURRENT_PROFILE_NAME", "Child")
        tvCurrentProfile.text = "Current Profile: $name"
    }

    private fun updateUsageUI(daily: Int, limit: Int, sessionUsage: Int, profileName: String) {
        tvCurrentProfile.text = "Current Profile: $profileName"
        tvDailyUsage.text = "Today's Usage: ${daily} min"

        if (limit > -1) {
            val remaining = limit - sessionUsage
            val displayRemaining = if (remaining < 0) 0 else remaining
            tvTimeUntilRest.text = "Time Until Rest: ${displayRemaining} min"
        } else {
            tvTimeUntilRest.text = "Time Until Rest: Unlimited"
        }
    }

    private fun showAdminPinDialog() {
        val container = android.widget.FrameLayout(this)
        val params = android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.leftMargin = 50
        params.rightMargin = 50
        params.gravity = android.view.Gravity.CENTER

        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        input.filters = arrayOf(android.text.InputFilter.LengthFilter(4))
        input.gravity = android.view.Gravity.CENTER
        input.textSize = 32f
        input.layoutParams = params

        container.addView(input)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Enter Admin Code")
            .setView(container)
            .create()

        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                if (s != null && s.length == 4) {
                    val pin = s.toString()
                    val prefs = PreferenceManager.getDefaultSharedPreferences(this@MainActivity)
                    val storedPin = prefs.getString("ADMIN_PIN", "1234")
                    if (pin == storedPin) {
                        dialog.dismiss()
                        startActivity(Intent(this@MainActivity, AdminActivity::class.java))
                    } else {
                        input.text.clear()
                        Toast.makeText(this@MainActivity, "Incorrect Admin Code", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        dialog.show()
    }

    private fun checkAdminPin(pin: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val storedPin = prefs.getString("ADMIN_PIN", "1234")
        if (pin == storedPin) {
             startActivity(Intent(this, AdminActivity::class.java))
        } else {
            Toast.makeText(this, "Incorrect Admin Code", Toast.LENGTH_SHORT).show()
        }
    }
}
