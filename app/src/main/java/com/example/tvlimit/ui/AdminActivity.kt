package com.example.tvlimit.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tvlimit.R
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceManager
import com.example.tvlimit.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AdminActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ProfileAdapter
    private lateinit var btnChangePassword: Button
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)

        recyclerView = findViewById(R.id.recyclerViewProfiles)
        btnChangePassword = findViewById(R.id.btnChangePassword)

        adapter = ProfileAdapter { item ->
            val profile = item.profile
            val intent = Intent(this, EditProfileActivity::class.java)
            intent.putExtra("PROFILE_ID", profile.id)
            startActivity(intent)
        }

        btnChangePassword.setOnClickListener {
            showChangePasswordDialog()
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        loadProfiles()
    }

    override fun onResume() {
        super.onResume()
        loadProfiles() // Reload in case edits were made
    }

    private fun loadProfiles() {
        scope.launch(Dispatchers.IO) {
            val database = AppDatabase.getDatabase(applicationContext)
            val profileDao = database.profileDao()
            // Collecting Flow inside a simplified coroutine for now, or just use simpler query if available.
            // ProfileDao returns Flow<List<Profile>>. Let's assume we can collect it.
            // Ideally we'd use ViewModel and LiveData/Flow, but sticking to basic simplified structure as per plan.
            // Wait, ProfileDao in 'view_file' output earlier showed: fun getAllProfiles(): Flow<List<Profile>>
            // Collecting Flow directly in Activity without ViewModel lifecycle awareness is a bit risky but functional for MVP.
            // Let's use a simpler one-shot fetch for MVP or simply collect the flow.

            profileDao.getAllProfiles().collect { profiles ->
                val profilesWithUsage = profiles.map { profile ->
                    // For Admin view, we might not care about detailed usage toggles,
                    // or we can fetch them if we want consistent UI.
                    // For now, passing 0s to satisfy the Type.
                    ProfileWithUsage(profile, 0, 0)
                }
                withContext(Dispatchers.Main) {
                    adapter.submitList(profilesWithUsage)
                }
            }
        }
    }

    private fun showChangePasswordDialog() {
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(50, 40, 50, 10)

        val newPinInput = EditText(this)
        newPinInput.hint = "New PIN"
        newPinInput.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        layout.addView(newPinInput)

        AlertDialog.Builder(this)
            .setTitle("Change Admin Password")
            .setView(layout)
            .setPositiveButton("Change") { _, _ ->
                val newPin = newPinInput.text.toString()

                if (newPin.length < 4) {
                    Toast.makeText(this, "PIN must be at least 4 digits", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val prefs = PreferenceManager.getDefaultSharedPreferences(this)
                prefs.edit().putString("ADMIN_PIN", newPin).apply()
                Toast.makeText(this, "Password Changed Successfully", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
