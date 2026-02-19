package com.example.tvlimit.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.tvlimit.R
import android.content.pm.ResolveInfo
import android.content.pm.PackageManager

data class AppItem(
    val name: String,
    val packageName: String,
    val icon: android.graphics.drawable.Drawable,
    var isSelected: Boolean
)

class AppAdapter(
    private val apps: List<AppItem>,
    private val onAppToggled: (AppItem, Boolean) -> Unit
) : RecyclerView.Adapter<AppAdapter.AppViewHolder>() {

    inner class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivIcon: ImageView = view.findViewById(R.id.ivAppIcon)
        val tvName: TextView = view.findViewById(R.id.tvAppName)
        val cbSelected: CheckBox = view.findViewById(R.id.cbSelected)

        init {
            view.setOnClickListener {
                val item = apps[adapterPosition]
                item.isSelected = !item.isSelected
                cbSelected.isChecked = item.isSelected
                onAppToggled(item, item.isSelected)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_selection, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = apps[position]
        holder.tvName.text = app.name
        holder.ivIcon.setImageDrawable(app.icon)
        holder.cbSelected.isChecked = app.isSelected
    }

    override fun getItemCount() = apps.size
}
