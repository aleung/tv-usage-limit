package com.example.tvlimit.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tvlimit.R
import com.example.tvlimit.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AdminActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ProfileAdapter
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)

        recyclerView = findViewById(R.id.recyclerViewProfiles)
        adapter = ProfileAdapter { profile ->
            val intent = Intent(this, EditProfileActivity::class.java)
            intent.putExtra("PROFILE_ID", profile.id)
            startActivity(intent)
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
                withContext(Dispatchers.Main) {
                    adapter.submitList(profiles)
                }
            }
        }
    }
}
