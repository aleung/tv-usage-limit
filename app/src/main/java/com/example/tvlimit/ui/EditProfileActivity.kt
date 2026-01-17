package com.example.tvlimit.ui

import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.tvlimit.R
import com.example.tvlimit.data.AppDatabase
import com.example.tvlimit.data.Profile
import android.content.Intent
import com.example.tvlimit.service.TimeTrackingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditProfileActivity : AppCompatActivity() {

    private lateinit var etProfileName: EditText
    private lateinit var etPin: EditText
    private lateinit var cbRestricted: CheckBox
    private lateinit var etDailyLimit: EditText
    private lateinit var etSessionLimit: EditText
    private lateinit var etRestDuration: EditText
    private lateinit var btnSave: Button

    private var profileId: Int = -1
    private var currentProfile: Profile? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        etProfileName = findViewById(R.id.etProfileName)
        etPin = findViewById(R.id.etPin)
        cbRestricted = findViewById(R.id.cbRestricted)
        etDailyLimit = findViewById(R.id.etDailyLimit)
        etSessionLimit = findViewById(R.id.etSessionLimit)
        etRestDuration = findViewById(R.id.etRestDuration)
        btnSave = findViewById(R.id.btnSave)

        profileId = intent.getIntExtra("PROFILE_ID", -1)

        if (profileId != -1) {
            loadProfile(profileId)
        } else {
            finish() // Should not happen
        }

        btnSave.setOnClickListener {
            saveProfile()
        }
    }

    private fun loadProfile(id: Int) {
        scope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(applicationContext)
            currentProfile = db.profileDao().getProfileById(id)

            withContext(Dispatchers.Main) {
                currentProfile?.let { profile ->
                    etProfileName.setText(profile.name)
                    etPin.setText(profile.pin ?: "")
                    cbRestricted.isChecked = profile.isRestricted
                    etDailyLimit.setText(profile.dailyLimitMinutes.toString())
                    etSessionLimit.setText(profile.sessionLimitMinutes.toString())
                    etRestDuration.setText(profile.restDurationMinutes.toString())
                }
            }
        }
    }

    private fun saveProfile() {
        val pin = etPin.text.toString()
        val restricted = cbRestricted.isChecked
        val daily = etDailyLimit.text.toString().toIntOrNull() ?: -1
        val session = etSessionLimit.text.toString().toIntOrNull() ?: -1
        val rest = etRestDuration.text.toString().toIntOrNull() ?: 0

        // Validation for Unrestricted Profile
        if (!restricted && pin.isEmpty()) {
            Toast.makeText(this, "Unrestricted profiles MUST have a PIN", Toast.LENGTH_LONG).show()
            return
        }

        currentProfile?.let { oldProfile ->
            val newProfile = oldProfile.copy(
                pin = if (pin.isEmpty()) null else pin,
                isRestricted = restricted,
                dailyLimitMinutes = daily,
                sessionLimitMinutes = session,
                restDurationMinutes = rest
            )

            scope.launch(Dispatchers.IO) {
                val db = AppDatabase.getDatabase(applicationContext)
                db.profileDao().updateProfile(newProfile)
                withContext(Dispatchers.Main) {
                    // Normalize intent construction to avoid "Unresolved reference" if TimeTrackingService is not imported or visible
                    // But we imported it above.
                    val intent = Intent(TimeTrackingService.ACTION_PROFILE_UPDATED)
                    intent.putExtra(TimeTrackingService.EXTRA_PROFILE_ID_UPDATE, newProfile.id)
                    sendBroadcast(intent)

                    Toast.makeText(this@EditProfileActivity, "Profile Saved", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }
}
