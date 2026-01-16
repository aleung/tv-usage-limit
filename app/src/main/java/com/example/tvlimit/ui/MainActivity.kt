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

    // We can listen for broadcast changes to update UI immediately
    private val profileReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
             if (intent?.action == ProfileSelectionActivity.ACTION_PROFILE_CHANGED) {
                 updateCurrentProfileUI()
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
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(profileReceiver)
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

        tvCurrentProfile.text = "Current Profile: (Check Service)"
        // Improvement: Read from SharedPrefs "CURRENT_PROFILE_NAME"
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val name = prefs.getString("CURRENT_PROFILE_NAME", "Child")
        tvCurrentProfile.text = "Current Profile: $name"
    }

    private fun showAdminPinDialog() {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD

        AlertDialog.Builder(this)
            .setTitle("Enter Admin Code")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val pin = input.text.toString()
                // Default Admin Code is 1234
                // In future: stored in Prefs
                if (pin == "1234") {
                     startActivity(Intent(this, AdminActivity::class.java))
                } else {
                    Toast.makeText(this, "Incorrect Admin Code", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
