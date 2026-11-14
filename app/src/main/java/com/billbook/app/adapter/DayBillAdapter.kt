package com.billbook.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.billbook.app.R
import com.billbook.app.databinding.ItemBillBinding
import com.billbook.app.model.Bill
import com.billbook.app.util.CategoryIconMapper
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
                // 使用 CategoryIconMapper 获取图标资源 ID
                val iconResId = if (bill.categoryIconResId != 0) {
                    bill.categoryIconResId
                } else {
                    CategoryIconMapper.getIconResId(bill.categoryName, bill.type)
                }

                if (iconResId != 0) {
                    ivCategoryIcon.setImageResource(iconResId)
                    ivCategoryIcon.visibility = View.VISIBLE
                    tvCategoryIcon.visibility = View.GONE
                } else {
                    // 兜底分支：不再显示系统 emoji，图标位留空
                    tvCategoryIcon.text = ""
                    tvCategoryIcon.visibility = View.VISIBLE
                    ivCategoryIcon.visibility = View.GONE
                }

                // 主行 = 用户写的「标题」（DB 字段仍是 remark）；没写时回退分类名
                tvCategoryName.text = bill.remark.ifBlank { bill.categoryName }
                tvCategoryName.setTextColor(textColor)
                tvCategoryName.setShadowLayer(4f, 0f, 1f, shadowColor)
                // 副行 = 支付方式
                tvRemark.text = bill.payType.ifBlank { bill.categoryName }
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
