package com.example.tvlimit.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.tvlimit.R
import com.example.tvlimit.data.Profile

class ProfileAdapter(private val onItemClick: (Profile) -> Unit) :
    ListAdapter<Profile, ProfileAdapter.ProfileViewHolder>(ProfileComparator()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProfileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_profile, parent, false)
        return ProfileViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProfileViewHolder, position: Int) {
        val current = getItem(position)
        holder.bind(current)
    }

    inner class ProfileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvProfileName: TextView = itemView.findViewById(R.id.tvProfileName)
        private val tvProfileType: TextView = itemView.findViewById(R.id.tvProfileType)
        private val tvLimits: TextView = itemView.findViewById(R.id.tvLimits)

        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
        }

        fun bind(profile: Profile) {
            tvProfileName.text = profile.name

            val typeText = if (profile.isRestricted) "Restricted Profile" else "Parent / Admin"
            tvProfileType.text = typeText

            val daily = if (profile.dailyLimitMinutes == -1) "Unlimited" else "${profile.dailyLimitMinutes}m"
            val session = if (profile.sessionLimitMinutes == -1) "Unlimited" else "${profile.sessionLimitMinutes}m"

            tvLimits.text = "Daily: $daily | Session: $session"
        }
    }

    class ProfileComparator : DiffUtil.ItemCallback<Profile>() {
        override fun areItemsTheSame(oldItem: Profile, newItem: Profile): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Profile, newItem: Profile): Boolean {
            return oldItem == newItem
        }
    }
}
