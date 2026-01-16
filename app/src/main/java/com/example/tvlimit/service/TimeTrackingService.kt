package com.example.tvlimit.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.tvlimit.R
import com.example.tvlimit.data.AppDatabase
import com.example.tvlimit.data.Profile
import com.example.tvlimit.data.UsageLog
import com.example.tvlimit.receiver.AdminReceiver
import com.example.tvlimit.ui.ProfileSelectionActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class TimeTrackingService : Service() {

    companion object {
        const val CHANNEL_ID = "TvLimitServiceChannel"
        const val NOTIFICATION_ID = 1
        private const val CHECK_INTERVAL_MS = 60000L // Check every minute
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var trackingJob: Job? = null
    private lateinit var database: AppDatabase
    private var currentProfile: Profile? = null

    // Usage Tracking
    private var accumulatedDailyUsage = 0
    private var currentSessionUsage = 0
    private var lastSessionEndTime: Long = 0

    // Overlay
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var isOverlayShowing = false
    private var sleepTimer: CountDownTimer? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                Log.d("TvLimit", "Screen OFF. Resetting to Default Profile.")
                handleScreenOff()
            } else if (intent?.action == Intent.ACTION_SCREEN_ON) {
                Log.d("TvLimit", "Screen ON. Resuming tracking.")
                handleScreenOn()
            }
        }
    }

    private val profileReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ProfileSelectionActivity.ACTION_PROFILE_CHANGED) {
                val profileId = intent.getIntExtra(ProfileSelectionActivity.EXTRA_PROFILE_ID, -1)
                if (profileId != -1) {
                    handleProfileSwitch(profileId)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("TvLimit", "Service onCreate")
        database = AppDatabase.getDatabase(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        startForeground(NOTIFICATION_ID, createNotification())

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenReceiver, filter)

        val profileFilter = IntentFilter(ProfileSelectionActivity.ACTION_PROFILE_CHANGED)
        // Must specify export flag for Android 14+ if targeting new SDKs, though here receiver is dynamic inside service
        // Just standard register works for implicit broadcasts if package limited or explicit.
        // But internal component broadcast is usually fine.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
             registerReceiver(profileReceiver, profileFilter, Context.RECEIVER_EXPORTED) // Or NOT_EXPORTED if internal
        } else {
             registerReceiver(profileReceiver, profileFilter)
        }

        // Initial setup
        initializeProfile()
    }

    private fun initializeProfile() {
        serviceScope.launch {
            // Always start with 'Child' or default profile
            val childProfile = database.profileDao().getProfileByName("Child")
            currentProfile = childProfile
            loadUsageStats()
            Log.d("TvLimit", "Initialized with profile: ${currentProfile?.name}")
            // If screen is already on, start tracking
            handleScreenOn()
        }
    }

    private fun handleProfileSwitch(profileId: Int) {
        serviceScope.launch {
            val newProfile = database.profileDao().getProfileById(profileId)
            if (newProfile != null) {
                currentProfile = newProfile
                loadUsageStats()
                Log.d("TvLimit", "Switched to Profile: ${newProfile.name}")
                Toast.makeText(applicationContext, "Profile: ${newProfile.name}", Toast.LENGTH_SHORT).show()

                // If new profile is NOT restricted, remove overlay
                if (!newProfile.isRestricted) {
                    removeOverlay()
                } else {
                     // If restricted, check limits. If limits still exceeded, overlay stays (or re-checks).
                     // However, usually switching to SAME restricted profile is useless if out of time.
                     // But if switching to ANOTHER restricted profile with time left, overlay should go.
                     // Let's rely on standard loop or explicit check.
                     // Explicit check:
                     checkLimits() // This will show overlay if needed.
                     // If no limit reached, we need to hide overlay if it was showing?
                     // Yes, checkLimits only SHOWS. Need to HIDE if passed.
                     if (!isLimitReached()) {
                         removeOverlay()
                     }
                }
            }
        }
    }

    private fun isLimitReached(): Boolean {
        val cProfile = currentProfile ?: return false
        if (!cProfile.isRestricted) return false
        val daily = cProfile.dailyLimitMinutes
        val session = cProfile.sessionLimitMinutes
        return (daily > -1 && accumulatedDailyUsage >= daily) ||
               (session > -1 && currentSessionUsage >= session)
    }

    private suspend fun loadUsageStats() {
        val today = LocalDate.now().toString()
        val cProfile = currentProfile ?: return

        val usageLog = database.profileDao().getUsageLog(cProfile.id, today)
        accumulatedDailyUsage = usageLog?.totalUsageMinutes ?: 0
        currentSessionUsage = 0 // Reset session usage on profile load / switch?
        // Wait, if I switch profile back and forth, session usage should probably persist if same session?
        // But simplified logic: Switch = New Session context usually or just continue?
        // Spec says "Session Limit". If 45m.
        // If I watch 20m, switch to Parent, switch back.
        // Should it start 0? Or 20?
        // Ideally 20. But tracking "Session" across switches is hard.
        // Let's reset session usage for now as implied "New Session" or strictly "Continuous watching".
        // Actually, if I switch to Parent, I am 'resting' effectively or 'unlimited'.
        // Let's keep it simple: Reset Session on Profile Switch.
        // BUT daily usage is loaded from DB.

        Log.d("TvLimit", "Loaded Usage: $accumulatedDailyUsage mins today.")
    }

    private fun handleScreenOff() {
        stopTracking()
        lastSessionEndTime = System.currentTimeMillis()

        // Reset to Default Profile (Child)
        serviceScope.launch {
            val childProfile = database.profileDao().getProfileByName("Child")
            if (childProfile != null) {
                currentProfile = childProfile
                Log.d("TvLimit", "Reset to Child Profile")
                loadUsageStats()
            }
        }
        removeOverlay()
    }

    private fun handleScreenOn() {
        if (currentProfile == null) return
        startTracking()
    }

    private fun startTracking() {
        if (trackingJob?.isActive == true) return

        // Rest Duration Check Logic (Simplified for now)
        // If we want detailed Rest enforcement, we need to persist "LastSessionEnd" in DB?
        // For now, in-memory is fine.

        trackingJob = serviceScope.launch {
            while (isActive) {
                updateUsage()
                if (isLimitReached()) {
                     showWarningOverlay()
                }
                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    private fun stopTracking() {
        trackingJob?.cancel()
        trackingJob = null
    }

    private suspend fun updateUsage() {
        accumulatedDailyUsage++
        currentSessionUsage++

        val cProfile = currentProfile ?: return
        val today = LocalDate.now().toString()
        val dao = database.profileDao()

        val existingLog = dao.getUsageLog(cProfile.id, today)
        if (existingLog == null) {
            dao.insertUsageLog(UsageLog(profileId = cProfile.id, date = today, totalUsageMinutes = accumulatedDailyUsage))
        } else {
            dao.updateUsageMinutes(existingLog.id, accumulatedDailyUsage)
        }
        Log.d("TvLimit", "Usage Updated: Daily=$accumulatedDailyUsage")
    }

    private fun checkLimits() {
         if (isLimitReached()) {
             showWarningOverlay()
         }
    }

    private fun showWarningOverlay() {
        if (isOverlayShowing) return
        isOverlayShowing = true

        serviceScope.launch(Dispatchers.Main) {
            if (overlayView == null) {
                val inflater = LayoutInflater.from(this@TimeTrackingService)
                overlayView = inflater.inflate(R.layout.view_warning_overlay, null)

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    else
                        WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    android.graphics.PixelFormat.TRANSLUCENT
                )
                params.gravity = Gravity.CENTER

                val btnSwitch = overlayView!!.findViewById<Button>(R.id.btnSwitchProfile)
                btnSwitch.setOnClickListener {
                    // Launch ProfileSelectionActivity
                    val intent = Intent(applicationContext, ProfileSelectionActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(intent)
                    // Overlay remains until profile switches (Broadcast received -> removeOverlay)
                }

                try {
                    windowManager?.addView(overlayView, params)
                } catch (e: Exception) {
                    Log.e("TvLimit", "Error showing overlay: ${e.message}")
                }
            }

            startSleepCountdown()
        }
    }

    private fun startSleepCountdown() {
        sleepTimer?.cancel()
        sleepTimer = object : CountDownTimer(10000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                val tvTimer = overlayView?.findViewById<TextView>(R.id.tvTimer)
                tvTimer?.text = "Turning off in ${seconds}s"
            }

            override fun onFinish() {
                val tvTimer = overlayView?.findViewById<TextView>(R.id.tvTimer)
                tvTimer?.text = "Sleeping..."
                lockDevice()
            }
        }.start()
    }

    private fun removeOverlay() {
        if (!isOverlayShowing) return
        isOverlayShowing = false
        sleepTimer?.cancel()

        serviceScope.launch(Dispatchers.Main) {
            if (overlayView != null) {
                try {
                    windowManager?.removeView(overlayView)
                } catch (e: Exception) {
                    Log.e("TvLimit", "Error removing overlay: ${e.message}")
                }
                overlayView = null
            }
        }
    }

    private fun createNotification(): Notification {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "TV Tracking Service",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TV Usage Limit")
            .setContentText("Monitoring usage...")
            .setSmallIcon(R.drawable.ic_launcher)
            .build()
    }

    private fun lockDevice() {
        val devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val componentName = ComponentName(this, AdminReceiver::class.java)
        if (devicePolicyManager.isAdminActive(componentName)) {
            devicePolicyManager.lockNow()
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(screenReceiver)
        try {
            unregisterReceiver(profileReceiver)
        } catch (e: Exception) {
            // Ignore if not registered
        }
        serviceScope.launch {
            updateUsage()
        }
        trackingJob?.cancel()
    }
}
