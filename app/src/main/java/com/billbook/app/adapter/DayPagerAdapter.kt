package com.billbook.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.billbook.app.R
import com.billbook.app.db.BillDao
import com.billbook.app.model.Bill

class DayPagerAdapter(
    private val onBillClick: (Bill) -> Unit,
    private val onBillLongClick: (Bill) -> Unit
) : RecyclerView.Adapter<DayPagerAdapter.DayViewHolder>() {

    private var dayGroups: List<BillDao.DayBills> = emptyList()
    private var textPrimary: Int = 0xFF1A1A2E.toInt()
    private var textSecondary: Int = 0x801A1A2E.toInt()
    private var shadowColor: Int = 0xC0000000.toInt()

    fun submitDays(days: List<BillDao.DayBills>) {
        dayGroups = days
        notifyDataSetChanged()
    }

    fun getDayCount() = dayGroups.size

    fun getCurrentDays() = dayGroups

    fun getDayText(position: Int): String {
        if (position < 0 || position >= dayGroups.size) return ""
        return dayGroups[position].dateText
    }

    fun setTextColors(primary: Int, secondary: Int, shadow: Int = 0xC0000000.toInt()) {
        textPrimary = primary
        textSecondary = secondary
        shadowColor = shadow
        notifyDataSetChanged()
    }

    override fun getItemCount() = dayGroups.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.layout_day_page, parent, false)
        return DayViewHolder(view)
    }

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
        holder.bind(dayGroups[position])
    }

    inner class DayViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val rvDayBills: RecyclerView = itemView.findViewById(R.id.rvDayBills)

        private val billAdapter = DayBillAdapter(
            onItemClick = { onBillClick(it) },
            onItemLongClick = { onBillLongClick(it) }
        )

        init {
            rvDayBills.layoutManager = LinearLayoutManager(itemView.context)
            rvDayBills.adapter = billAdapter
            rvDayBills.addItemDecoration(BillAdapter.DayDividerDecoration())
        }

        fun bind(day: BillDao.DayBills) {
            billAdapter.setTextColors(textPrimary, textSecondary, shadowColor)
            billAdapter.submitList(day.bills)
        }
    }
}
