package com.example.tvlimit.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tvlimit.R
import com.example.tvlimit.data.AppDatabase
import com.example.tvlimit.data.Profile
import androidx.preference.PreferenceManager
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileSelectionActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ProfileAdapter
    private val scope = CoroutineScope(Dispatchers.Main)

    companion object {
        const val ACTION_PROFILE_CHANGED = "com.example.tvlimit.ACTION_PROFILE_CHANGED"
        const val EXTRA_PROFILE_ID = "com.example.tvlimit.EXTRA_PROFILE_ID"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile_selection)

        recyclerView = findViewById(R.id.recyclerViewProfiles)
        recyclerView.layoutManager = GridLayoutManager(this, 2) // Grid for selection

        adapter = ProfileAdapter { profileWithUsage ->
            onProfileClicked(profileWithUsage.profile)
        }
        recyclerView.adapter = adapter

        loadProfiles()
    }

    private fun loadProfiles() {
        scope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(applicationContext)
            val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            val today = LocalDate.now().toString()

            db.profileDao().getAllProfiles().collect { profiles ->
                val profilesWithUsage = profiles.map { profile ->
                    // Fetch Daily Usage
                    val usageLog = db.profileDao().getUsageLog(profile.id, today)
                    val dailyUsage = usageLog?.totalUsageMinutes ?: 0

                    // Fetch Session Usage
                    val lastEnd = prefs.getLong("last_session_end_time_${profile.id}", 0)
                    val storedSessionUsage = prefs.getInt("session_usage_${profile.id}", 0)
                    var currentSession = 0

                    if (lastEnd > 0) {
                        val elapsed = System.currentTimeMillis() - lastEnd
                        val restDurationMs = profile.restDurationMinutes * 60 * 1000L
                        // If rest duration has passed, session resets to 0. Otherwise use stored.
                        if (elapsed < restDurationMs) {
                            currentSession = storedSessionUsage
                        }
                    } else {
                         // No last end time, maybe verify if we have stored usage without end time (unlikely active session not running)
                         // Actually if valid session usage exists without end time it means active?
                         // But Service saves end time on stop/screen off.
                         // If active, Service has state. This Activity is usually shown when switching or valid.
                         // Let's rely on stored prefs which Service updates.
                         // If currently running, Service updates Prefs periodically?
                         // Service saves on profile switch or screen off.
                         // While running, we might be stale.
                         // But for "Choose Profile", usually implies we are switching TO it.
                         // Except "Time Left" logic updates prefs? Not periodically in current Service code.
                         // Service only saves on OFF/SWITCH.
                         // So displayed usage might be slightly behind if currently active profile is viewed?
                         // But if we are viewing "Choose Profile", we probably just opened it.
                         // The service logic: saveProfileState is called on SWITCH and SCREEN OFF.
                         // It is NOT called periodically during run.
                         // So if I am "Child" watching, and open "Switch Profile", stored usage is old.
                         // BUT, when we open ProfileSelectionActivity, likely we are doing so from overlay or main activity.
                         // We should trigger a save?
                         // Just reading prefs is 'best effort' based on current Service implementation.
                         currentSession = storedSessionUsage
                    }

                    ProfileWithUsage(profile, dailyUsage, currentSession)
                }

                withContext(Dispatchers.Main) {
                    adapter.submitList(profilesWithUsage)
                }
            }
        }
    }

    private fun onProfileClicked(profile: Profile) {
        if (!profile.isRestricted) {
            // Unrestricted - Ask for PIN
            showPinDialog(profile)
        } else {
            // Restricted - Switch immediately (Optional: PIN check if strictly configured, but per plan optional)
            switchToProfile(profile)
        }
    }

    private fun showDialog(title: String, verifyPin: (String) -> Boolean) {
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
            .setTitle(title)
            .setView(container)
            .create()

        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                if (s != null && s.length == 4) {
                    val correct = verifyPin(s.toString())
                    if (correct) {
                        dialog.dismiss()
                    } else {
                        input.text.clear()
                        // Toast is handled in verifyPin
                    }
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        dialog.show()
    }

    private fun showPinDialog(profile: Profile) {
        showDialog("Enter PIN for ${profile.name}") { pin ->
            if (pin == profile.pin) {
                switchToProfile(profile)
                true
            } else {
                Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show()
                false
            }
        }
    }

    private fun switchToProfile(profile: Profile) {
        // Broadcast change
        val intent = Intent(ACTION_PROFILE_CHANGED)
        intent.putExtra(EXTRA_PROFILE_ID, profile.id)
        sendBroadcast(intent) // Context-registered receiver in Service should pick this up

        Toast.makeText(this, "Switched to ${profile.name}", Toast.LENGTH_SHORT).show()
        finish()
    }
}

data class ProfileWithUsage(
    val profile: Profile,
    val dailyUsage: Int,
    val sessionUsage: Int
)
