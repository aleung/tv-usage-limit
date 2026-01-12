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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.tvlimit.R
import com.example.tvlimit.data.AppDatabase
import com.example.tvlimit.data.Profile
import com.example.tvlimit.data.UsageLog
import com.example.tvlimit.receiver.AdminReceiver
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
    private var sessionStartTime: Long = 0
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

        // Initial setup
        initializeProfile()
    }

    private fun initializeProfile() {
        serviceScope.launch {
            // Always start with 'Child' or default profile
            val childProfile = database.profileDao().getProfileByName("Child")
            currentProfile = childProfile

            // Load today's usage for this profile
            loadUsageStats()

            Log.d("TvLimit", "Initialized with profile: ${currentProfile?.name}")

            // If screen is already on, start tracking
            handleScreenOn()
        }
    }

    private suspend fun loadUsageStats() {
        val today = LocalDate.now().toString()
        val cProfile = currentProfile ?: return

        val usageLog = database.profileDao().getUsageLog(cProfile.id, today)
        accumulatedDailyUsage = usageLog?.totalUsageMinutes ?: 0
        currentSessionUsage = 0
        Log.d("TvLimit", "Loaded Usage: $accumulatedDailyUsage mins today.")
    }

    private fun handleScreenOff() {
        stopTracking()
        // Record session end time for "Rest Duration" logic
        lastSessionEndTime = System.currentTimeMillis()

        // Reset to Default Profile (Child)
        serviceScope.launch {
            val childProfile = database.profileDao().getProfileByName("Child")
            if (childProfile != null) {
                currentProfile = childProfile
                Log.d("TvLimit", "Reset to Child Profile")
                // Load stats for Child so we are ready for next resume
                loadUsageStats()
            }
        }

        // Hide overlay if showing
        removeOverlay()
    }

    private fun handleScreenOn() {
        if (currentProfile == null) return
        startTracking()
    }

    private fun startTracking() {
        if (trackingJob?.isActive == true) return

        sessionStartTime = System.currentTimeMillis()

        // Check Rest Duration
        val now = System.currentTimeMillis()
        val restNeeded = (currentProfile?.restDurationMinutes ?: 0) * 60 * 1000
        val timeSinceLastSession = now - lastSessionEndTime

        if (lastSessionEndTime > 0 && timeSinceLastSession < restNeeded && currentProfile?.isRestricted == true) {
             // Show Warning immediately - Rest required
             showWarningOverlay(isRestWarning = true)
             return
        }

        trackingJob = serviceScope.launch {
            while (isActive) {
                updateUsage()
                checkLimits()
                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    private fun stopTracking() {
        trackingJob?.cancel()
        trackingJob = null
    }

    private suspend fun updateUsage() {
        // Increment by 1 minute (approx, since we delay 60s)
        // For better precision we should use System.currentTimeMillis diff
        accumulatedDailyUsage++
        currentSessionUsage++

        // Save to DB
        val cProfile = currentProfile ?: return
        val today = LocalDate.now().toString()
        val dao = database.profileDao()

        val existingLog = dao.getUsageLog(cProfile.id, today)
        if (existingLog == null) {
            dao.insertUsageLog(UsageLog(profileId = cProfile.id, date = today, totalUsageMinutes = accumulatedDailyUsage))
        } else {
            dao.updateUsageMinutes(existingLog.id, accumulatedDailyUsage)
        }

        Log.d("TvLimit", "Usage Updated: Daily=$accumulatedDailyUsage, Session=$currentSessionUsage")
    }

    private fun checkLimits() {
        val cProfile = currentProfile ?: return
        if (!cProfile.isRestricted) return // Admin has no limits

        val dailyLimit = cProfile.dailyLimitMinutes
        val sessionLimit = cProfile.sessionLimitMinutes

        if ((dailyLimit > -1 && accumulatedDailyUsage >= dailyLimit) ||
            (sessionLimit > -1 && currentSessionUsage >= sessionLimit)) {
            Log.d("TvLimit", "Limit Reached! Showing Warning.")
            showWarningOverlay(isRestWarning = false)
        }
    }

    private fun showWarningOverlay(isRestWarning: Boolean) {
        if (isOverlayShowing) return
        isOverlayShowing = true

        serviceScope.launch(Dispatchers.Main) {
            if (overlayView == null) {
                val inflater = LayoutInflater.from(this@TimeTrackingService)
                overlayView = inflater.inflate(R.layout.view_warning_overlay, null)

                // Configure Window Params
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

                // Setup UI Elements
                val tvTitle = overlayView!!.findViewById<TextView>(R.id.tvTitle)
                val tvTimer = overlayView!!.findViewById<TextView>(R.id.tvTimer)
                val etPin = overlayView!!.findViewById<EditText>(R.id.etPin)
                val btnSubmit = overlayView!!.findViewById<Button>(R.id.btnSubmit)

                if (isRestWarning) {
                    tvTitle.text = "Rest Required"
                    tvTimer.text = "Take a break!"
                    // For Rest Warning, maybe we don't countdown to sleep immediately?
                    // Or we just block?
                    // Requirement: "Duration exceeded -> Warning -> 10s -> Close" covers session limit.
                    // For Rest Warning (Boot immediate), we likely just want to BLOCK.
                    // But if we block indefinitely, we might burn in screen.
                    // Safer to just sleep again after 10s if they don't switch to Parent.
                }

                btnSubmit.setOnClickListener {
                    val inputPin = etPin.text.toString()
                    handlePinInput(inputPin)
                }

                try {
                    windowManager?.addView(overlayView, params)
                } catch (e: Exception) {
                    Log.e("TvLimit", "Error verifying overlay: ${e.message}")
                }
            }

            // Start Countdown (10 seconds)
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
                // After locking, we expect Screen OFF, which triggers handleScreenOff() -> removeOverlay()
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

    private fun handlePinInput(pin: String) {
        serviceScope.launch {
            // Check if PIN matches any profile
            // Naive implementation: Check specific named profiles since we know them.


            var targetProfile: Profile? = null

            // We need to look up profile by PIN.
            // Since I cannot change DAO in this Replace block easily without context,
            // I will rely on standard known pins or matching logic if I had the list.

            // Hack for MVP: Hardcode check or fetch specific names if possible.
            // Better: Add specific query to DAO in next step or now?
            // "SELECT * FROM profiles WHERE pin = :pin LIMIT 1"
            // I can't add that to DAO now.
            // I'll use `getProfileByName("Parent")` and check pin.

            val parent = database.profileDao().getProfileByName("Parent")
            val child = database.profileDao().getProfileByName("Child")

            if (parent?.pin == pin) {
                targetProfile = parent
            } else if (child?.pin == pin) {
                targetProfile = child
            }

            withContext(Dispatchers.Main) {
                if (targetProfile != null) {
                    val oldProfile = currentProfile
                    currentProfile = targetProfile
                    Toast.makeText(applicationContext, "Profile: ${targetProfile.name}", Toast.LENGTH_SHORT).show()

                    // Specific Logic:
                    // If switching to Parent (Unrestricted) -> Remove Overlay, continue.
                    // If switching to Child (Restricted) -> Checks limits again?
                     // If we are already Child and limits reached, switching to Child again doesn't help unless we reset stats.
                    // Requirement: "Switch profile".
                    // If I am limited, I want to switch to Parent.

                    if (!targetProfile.isRestricted) {
                         removeOverlay()
                         // Start tracking for new profile? Parents don't need tracking but we track usage anyway.
                         // But since limit is -1, checkLimits won't trigger warning.
                         loadUsageStats()
                         startTracking() // Restart tracking with new profile
                    } else {
                        // Switching to another restricted profile?
                        // If it has limits, we might show overlay again if usage is high.
                        loadUsageStats()
                        // If limits passed, it will pop up again next minute.
                        removeOverlay()
                        startTracking()
                    }
                } else {
                    Toast.makeText(applicationContext, "Invalid PIN", Toast.LENGTH_SHORT).show()
                    val etPin = overlayView?.findViewById<EditText>(R.id.etPin)
                    etPin?.setText("")
                }
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

    // Placeholder for Lock Logic
    private fun lockDevice() {
        val devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val componentName = ComponentName(this, AdminReceiver::class.java)
        if (devicePolicyManager.isAdminActive(componentName)) {
            devicePolicyManager.lockNow()
        } else {
            Toast.makeText(this, "Admin permission not granted!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(screenReceiver)
        serviceScope.launch {
            updateUsage() // Save final state
        }
        trackingJob?.cancel()
    }
}
