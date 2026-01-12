package com.example.tvlimit.ui

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.tvlimit.R
import com.example.tvlimit.receiver.AdminReceiver
import com.example.tvlimit.service.TimeTrackingService

class MainActivity : AppCompatActivity() {

    private lateinit var btnOverlay: Button
    private lateinit var btnAdmin: Button
    private lateinit var btnStartService: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnOverlay = findViewById(R.id.btnOverlay)
        btnAdmin = findViewById(R.id.btnAdmin)
        btnStartService = findViewById(R.id.btnStartService)

        btnOverlay.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName"))
                startActivityForResult(intent, 101)
            } else {
                Toast.makeText(this, "Overlay permission already granted", Toast.LENGTH_SHORT).show()
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
            } else {
                Toast.makeText(this, "Admin permission already granted", Toast.LENGTH_SHORT).show()
            }
        }

        btnStartService.setOnClickListener {
            val intent = Intent(this, TimeTrackingService::class.java)
            startForegroundService(intent)
            Toast.makeText(this, "Service Started", Toast.LENGTH_SHORT).show()
        }

        checkPermissions()
    }

    private fun checkPermissions() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val componentName = ComponentName(this, AdminReceiver::class.java)

        if (Settings.canDrawOverlays(this) && dpm.isAdminActive(componentName)) {
            // All good, auto start service just in case
            val intent = Intent(this, TimeTrackingService::class.java)
            startForegroundService(intent)
        }
    }
}
