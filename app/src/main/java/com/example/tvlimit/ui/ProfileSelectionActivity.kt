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

        adapter = ProfileAdapter { profile ->
            onProfileClicked(profile)
        }
        recyclerView.adapter = adapter

        loadProfiles()
    }

    private fun loadProfiles() {
        scope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(applicationContext)
            db.profileDao().getAllProfiles().collect { profiles ->
                withContext(Dispatchers.Main) {
                    adapter.submitList(profiles)
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

    private fun showDialog(title: String, onPinEntered: (String) -> Unit) {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                onPinEntered(input.text.toString())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPinDialog(profile: Profile) {
        showDialog("Enter PIN for ${profile.name}") { pin ->
            if (pin == profile.pin) {
                switchToProfile(profile)
            } else {
                Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show()
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
