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

class ProfileAdapter(private val onItemClick: (ProfileWithUsage) -> Unit) :
    ListAdapter<ProfileWithUsage, ProfileAdapter.ProfileViewHolder>(ProfileComparator()) {

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
        private val tvDailyLimit: TextView = itemView.findViewById(R.id.tvDailyLimit)
        private val tvSessionLimit: TextView = itemView.findViewById(R.id.tvSessionLimit)
        private val tvRestDuration: TextView = itemView.findViewById(R.id.tvRestDuration)

        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
        }

        fun bind(item: ProfileWithUsage) {
            val profile = item.profile
            tvProfileName.text = profile.name

            val typeText = if (profile.isRestricted) "Restricted Profile" else "Parent / Admin"
            tvProfileType.text = typeText

            val dailyLimit = if (profile.dailyLimitMinutes == -1) "Unlimited" else "${profile.dailyLimitMinutes}m"
            val sessionLimit = if (profile.sessionLimitMinutes == -1) "Unlimited" else "${profile.sessionLimitMinutes}m"
            val restDuration = "${profile.restDurationMinutes}m"

            tvDailyLimit.text = "Daily: $dailyLimit (Used: ${item.dailyUsage}m)"
            tvSessionLimit.text = "Session: $sessionLimit (Used: ${item.sessionUsage}m)"
            tvRestDuration.text = "Rest: $restDuration"

            // Should hide these details for unrestricted?
            if (!profile.isRestricted) {
                tvDailyLimit.visibility = View.GONE
                tvSessionLimit.visibility = View.GONE
                tvRestDuration.visibility = View.GONE
            } else {
                tvDailyLimit.visibility = View.VISIBLE
                tvSessionLimit.visibility = View.VISIBLE
                tvRestDuration.visibility = View.VISIBLE
            }
        }
    }

    class ProfileComparator : DiffUtil.ItemCallback<ProfileWithUsage>() {
        override fun areItemsTheSame(oldItem: ProfileWithUsage, newItem: ProfileWithUsage): Boolean {
            return oldItem.profile.id == newItem.profile.id
        }

        override fun areContentsTheSame(oldItem: ProfileWithUsage, newItem: ProfileWithUsage): Boolean {
            return oldItem == newItem
        }
    }
}
