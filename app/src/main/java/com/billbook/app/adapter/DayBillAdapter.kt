package com.billbook.app.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.billbook.app.R
import com.billbook.app.databinding.ItemBillBinding
import com.billbook.app.model.Bill
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DayBillAdapter(
    private val onItemClick: (Bill) -> Unit,
    private val onItemLongClick: (Bill) -> Unit
) : RecyclerView.Adapter<DayBillAdapter.BillViewHolder>() {

    private val items = mutableListOf<Bill>()
    private val dateFormat = SimpleDateFormat("HH:mm", Locale.CHINA)
    private var textColor: Int = 0xFF1A1A2E.toInt()
    private var secondaryColor: Int = 0x801A1A2E.toInt()
    private var shadowColor: Int = 0xC0000000.toInt()

    fun submitList(bills: List<Bill>) {
        items.clear()
        items.addAll(bills)
        notifyDataSetChanged()
    }

    fun setTextColors(primary: Int, secondary: Int, shadow: Int = 0xC0000000.toInt()) {
        textColor = primary
        secondaryColor = secondary
        shadowColor = shadow
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BillViewHolder {
        val binding = ItemBillBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BillViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BillViewHolder, position: Int) {
        holder.bind(items[position])
    }

    inner class BillViewHolder(private val binding: ItemBillBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(bill: Bill) {
            binding.apply {
                tvCategoryIcon.text = bill.categoryIcon.ifEmpty { "📦" }
                tvCategoryName.text = bill.categoryName
                tvCategoryName.setTextColor(textColor)
                tvCategoryName.setShadowLayer(4f, 0f, 1f, shadowColor)
                tvRemark.text = bill.remark.ifEmpty { bill.payType }
                tvRemark.setTextColor(secondaryColor)
                tvRemark.setShadowLayer(3f, 0f, 1f, shadowColor)
                tvTime.text = dateFormat.format(Date(bill.billTime))
                tvTime.setTextColor(secondaryColor)
                tvTime.setShadowLayer(3f, 0f, 1f, shadowColor)

                val prefix = if (bill.type == Bill.TYPE_EXPENSE) "-" else "+"
                tvMoney.text = String.format("%s¥%.2f", prefix, bill.money)

                val colorRes = if (bill.type == Bill.TYPE_EXPENSE) R.color.expense_red else R.color.income_green
                tvMoney.setTextColor(ContextCompat.getColor(root.context, colorRes))
                tvMoney.setShadowLayer(4f, 0f, 1f, shadowColor)

                root.setOnClickListener { onItemClick(bill) }
                root.setOnLongClickListener { onItemLongClick(bill); true }
            }
        }
    }
}
