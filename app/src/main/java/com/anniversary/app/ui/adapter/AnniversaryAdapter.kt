package com.anniversary.app.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.anniversary.app.R
import com.anniversary.app.data.entity.Anniversary
import com.anniversary.app.data.entity.AnniversaryType
import com.anniversary.app.databinding.ItemAnniversaryBinding
import com.anniversary.app.util.DateUtils
import com.anniversary.app.util.LunarCalendar

class AnniversaryAdapter(
    private val onItemClick: (Anniversary) -> Unit,
    private val onItemLongClick: (Anniversary) -> Unit,
    private val onSelectionChanged: (Long, Boolean) -> Unit
) : ListAdapter<Anniversary, AnniversaryAdapter.ViewHolder>(DiffCallback()) {

    var isSelectionMode = false
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    var selectedIds: Set<Long> = emptySet()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAnniversaryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemAnniversaryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(anniversary: Anniversary) {
            binding.tvName.text = anniversary.name
            binding.chipType.text = anniversary.type.displayName

            // 显示农历标识
            if (anniversary.isLunar) {
                binding.chipLunar.visibility = View.VISIBLE
                binding.tvDate.text = DateUtils.formatDate(anniversary)
            } else {
                binding.chipLunar.visibility = View.GONE
                binding.tvDate.text = DateUtils.formatDate(anniversary.date)
            }

            // Set type chip color
            val chipColor = when (anniversary.type) {
                AnniversaryType.BIRTHDAY -> R.color.type_birthday
                AnniversaryType.ANNIVERSARY -> R.color.type_anniversary
                AnniversaryType.FESTIVAL -> R.color.type_festival
                AnniversaryType.CUSTOM -> R.color.type_custom
            }
            binding.chipType.setChipBackgroundColorResource(chipColor)

            // Calculate days
            val days = DateUtils.getDaysUntilNext(anniversary)

            when {
                days > 0 -> {
                    binding.tvDaysCount.text = days.toString()
                    binding.tvDaysLabel.text = itemView.context.getString(R.string.days_later)
                }
                days == 0L -> {
                    binding.tvDaysCount.text = "!"
                    binding.tvDaysLabel.text = itemView.context.getString(R.string.today)
                }
                else -> {
                    binding.tvDaysCount.text = (-days).toString()
                    binding.tvDaysLabel.text = itemView.context.getString(R.string.days_ago)
                }
            }

            // Selection mode
            binding.checkBox.visibility = if (isSelectionMode) View.VISIBLE else View.GONE

            // 先移除监听器，避免设置isChecked时触发回调导致无限递归
            binding.checkBox.setOnCheckedChangeListener(null)
            binding.checkBox.isChecked = selectedIds.contains(anniversary.id)
            binding.checkBox.setOnCheckedChangeListener { _, isChecked ->
                onSelectionChanged(anniversary.id, isChecked)
            }

            binding.root.setOnClickListener {
                if (isSelectionMode) {
                    binding.checkBox.isChecked = !binding.checkBox.isChecked
                } else {
                    onItemClick(anniversary)
                }
            }

            binding.root.setOnLongClickListener {
                onItemLongClick(anniversary)
                true
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Anniversary>() {
        override fun areItemsTheSame(oldItem: Anniversary, newItem: Anniversary): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Anniversary, newItem: Anniversary): Boolean {
            return oldItem == newItem
        }
    }
}
