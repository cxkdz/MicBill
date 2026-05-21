package com.billbook.app.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.billbook.app.R
import java.util.Calendar

class DateDayAdapter(
    private val onDayClick: (Int) -> Unit
) : RecyclerView.Adapter<DateDayAdapter.DayViewHolder>() {

    private var days: List<Int> = emptyList()
    private var selectedDay: Int = -1
    private var todayDay: Int = -1
    private var firstDayOfWeek: Int = 0 // 0=Sunday

    fun setMonth(year: Int, month: Int) {
        val cal = Calendar.getInstance()
        val today = Calendar.getInstance()

        // Get first day of month
        cal.set(year, month, 1)
        firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1 // 0=Sun

        // Get days in month
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        // Build list: leading empty cells + days
        val dayList = mutableListOf<Int>()
        for (i in 0 until firstDayOfWeek) {
            dayList.add(0) // empty cell
        }
        for (d in 1..daysInMonth) {
            dayList.add(d)
        }

        days = dayList
        selectedDay = -1

        // Mark today if same month
        if (today.get(Calendar.YEAR) == year && today.get(Calendar.MONTH) == month) {
            todayDay = today.get(Calendar.DAY_OF_MONTH)
        } else {
            todayDay = -1
        }

        notifyDataSetChanged()
    }

    fun setSelectedDay(day: Int) {
        val oldSelected = selectedDay
        selectedDay = day
        if (oldSelected > 0) {
            val oldIdx = days.indexOf(oldSelected)
            if (oldIdx >= 0) notifyItemChanged(oldIdx)
        }
        if (day > 0) {
            val newIdx = days.indexOf(day)
            if (newIdx >= 0) notifyItemChanged(newIdx)
        }
    }

    fun getSelectedDay(): Int = selectedDay

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_anime_date_day, parent, false)
        return DayViewHolder(view)
    }

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
        val day = days[position]
        holder.bind(day, day == selectedDay, day == todayDay)
    }

    override fun getItemCount(): Int = days.size

    inner class DayViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDay: TextView = itemView.findViewById(R.id.tvDay)

        fun bind(day: Int, isSelected: Boolean, isToday: Boolean) {
            if (day == 0) {
                tvDay.text = ""
                tvDay.background = null
                tvDay.setOnClickListener(null)
                tvDay.alpha = 0f
                return
            }

            tvDay.alpha = 1f
            tvDay.text = day.toString()

            when {
                isSelected -> {
                    tvDay.setBackgroundResource(R.drawable.bg_anime_date_selected)
                    tvDay.setTextColor(Color.WHITE)
                    // Selection pop animation
                    tvDay.animate()
                        .scaleX(1.15f).scaleY(1.15f)
                        .setDuration(150)
                        .withEndAction {
                            tvDay.animate().scaleX(1f).scaleY(1f)
                                .setDuration(200)
                                .setInterpolator(OvershootInterpolator(2f))
                                .start()
                        }
                        .start()
                }
                isToday -> {
                    tvDay.setBackgroundResource(R.drawable.bg_anime_date_today)
                    tvDay.setTextColor(0xE0FFFFFF.toInt())
                    tvDay.scaleX = 1f
                    tvDay.scaleY = 1f
                }
                else -> {
                    tvDay.setBackgroundResource(R.drawable.bg_anime_date_cell)
                    tvDay.setTextColor(0x99FFFFFF.toInt())
                    tvDay.scaleX = 1f
                    tvDay.scaleY = 1f
                }
            }

            // Weekend coloring for unselected (column 0=Sun, 6=Sat)
            if (!isSelected) {
                val column = adapterPosition % 7
                if (column == 0 || column == 6) {
                    if (!isToday) tvDay.setTextColor(0xCCFF8A80.toInt())
                }
            }

            tvDay.setOnClickListener {
                val prev = selectedDay
                selectedDay = day
                if (prev > 0) {
                    val oldIdx = days.indexOf(prev)
                    if (oldIdx >= 0) notifyItemChanged(oldIdx)
                }
                notifyItemChanged(adapterPosition)
                onDayClick(day)
            }
        }
    }
}
