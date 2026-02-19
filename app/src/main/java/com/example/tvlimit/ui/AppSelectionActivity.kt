package com.example.tvlimit.ui

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tvlimit.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppSelectionActivity : ComponentActivity() {

    private lateinit var adapter: AppAdapter
    private val appList = mutableListOf<AppItem>()

    companion object {
        const val EXTRA_SELECTED_APPS = "selected_apps"
        const val RESULT_APPS = "result_apps"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_selection)

        val rv = findViewById<RecyclerView>(R.id.rvAppList)
        rv.layoutManager = LinearLayoutManager(this)

        val preSelected = intent.getStringExtra(EXTRA_SELECTED_APPS)?.split(",")?.toSet() ?: emptySet()

        findViewById<Button>(R.id.btnDone).setOnClickListener {
            val selectedPackages = appList.filter { it.isSelected }.joinToString(",") { it.packageName }
            val data = Intent()
            data.putExtra(RESULT_APPS, selectedPackages)
            setResult(Activity.RESULT_OK, data)
            finish()
        }

        loadApps(preSelected)
    }

    private fun loadApps(preSelected: Set<String>) {
        CoroutineScope(Dispatchers.IO).launch {
            val pm = packageManager
            val intentLauncher = Intent(Intent.ACTION_MAIN, null)
            intentLauncher.addCategory(Intent.CATEGORY_LAUNCHER)

            val intentLeanback = Intent(Intent.ACTION_MAIN, null)
            intentLeanback.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)

            val appsLauncher = pm.queryIntentActivities(intentLauncher, 0)
            val appsLeanback = pm.queryIntentActivities(intentLeanback, 0)

            val combinedApps = (appsLauncher + appsLeanback).distinctBy { it.activityInfo.packageName }

            val items = combinedApps.mapNotNull { resolveInfo ->
                val packageName = resolveInfo.activityInfo.packageName
                if (packageName == this@AppSelectionActivity.packageName) return@mapNotNull null // Don't block self

                AppItem(
                    name = resolveInfo.loadLabel(pm).toString(),
                    packageName = packageName,
                    icon = resolveInfo.loadIcon(pm),
                    isSelected = preSelected.contains(packageName)
                )
            }.sortedBy { it.name }

            withContext(Dispatchers.Main) {
                appList.clear()
                appList.addAll(items)
                adapter = AppAdapter(appList) { item, isSelected ->
                    item.isSelected = isSelected
                }
                findViewById<RecyclerView>(R.id.rvAppList).adapter = adapter
            }
        }
    }
}
