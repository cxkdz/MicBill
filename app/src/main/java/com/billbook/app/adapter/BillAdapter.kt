package com.billbook.app.adapter

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.billbook.app.R
import com.billbook.app.databinding.ItemBillBinding
import com.billbook.app.databinding.ItemBillHeaderBinding
import com.billbook.app.model.Bill
import com.billbook.app.util.CategoryIconMapper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class BillAdapter(
    private val onItemClick: (Bill) -> Unit,
    private val onItemLongClick: (Bill) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_BILL = 1
    }

    private val items = mutableListOf<DisplayItem>()
    private val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
    private val headerFormat = SimpleDateFormat("MM月dd日 EEEE", Locale.CHINA)
    private val dayFormat = SimpleDateFormat("yyyyMMdd", Locale.CHINA)
    private var topDayGroupIndex = 0
    private var textColor: Int = 0xFF1A1A2E.toInt()
    private var secondaryColor: Int = 0x801A1A2E.toInt()
    private var shadowColor: Int = 0xC0000000.toInt()

    fun setTextColors(primary: Int, secondary: Int, shadow: Int = 0xC0000000.toInt()) {
        textColor = primary
        secondaryColor = secondary
        shadowColor = shadow
    }

    sealed class DisplayItem {
        class Header(val dateText: String) : DisplayItem()
        class BillItem(val bill: Bill, val isLastInDay: Boolean) : DisplayItem()
    }

    fun submitList(bills: List<Bill>) {
        items.clear()
        dayGroupIndices.clear()
        val grouped = groupByDay(bills)
        var groupIdx = 0
        grouped.forEach { dayBills ->
            dayBills.forEachIndexed { index, bill ->
                if (index == 0) {
                    val cal = Calendar.getInstance().apply { timeInMillis = bill.billTime }
                    items.add(DisplayItem.Header(headerFormat.format(cal.time)))
                    dayGroupIndices[items.size - 1] = groupIdx
                }
                items.add(DisplayItem.BillItem(bill, index == dayBills.lastIndex))
                dayGroupIndices[items.size - 1] = groupIdx
            }
            groupIdx++
        }
        topDayGroupIndex = 0
        notifyDataSetChanged()
    }

    // 每个位置对应的天组索引
    private val dayGroupIndices = mutableMapOf<Int, Int>()

    /** 获取某个位置的天组索引 */
    fun getDayGroupForPosition(position: Int): Int {
        return dayGroupIndices[position] ?: 0
    }

    /** 设置当前顶部天组索引，触发alpha更新 */
    fun setTopDayGroup(index: Int) {
        if (index != topDayGroupIndex) {
            topDayGroupIndex = index
            notifyDataSetChanged()
        }
    }

    /** 根据距离顶部天组的远近计算alpha */
    fun getAlphaForPosition(position: Int): Float {
        val groupIdx = dayGroupIndices[position] ?: 0
        val distance = groupIdx - topDayGroupIndex
        return when {
            distance <= 0 -> 1.0f
            distance == 1 -> 0.85f
            distance == 2 -> 0.7f
            else -> 0.55f
        }
    }

    /** 根据距离计算垂直偏移（叠加效果） */
    fun getTranslationYForPosition(position: Int): Float {
        val groupIdx = dayGroupIndices[position] ?: 0
        val distance = groupIdx - topDayGroupIndex
        return when {
            distance <= 0 -> 0f
            distance == 1 -> -8f
            distance == 2 -> -14f
            else -> -18f
        }
    }

    /** 根据距离计算缩放 */
    fun getScaleForPosition(position: Int): Float {
        val groupIdx = dayGroupIndices[position] ?: 0
        val distance = groupIdx - topDayGroupIndex
        return when {
            distance <= 0 -> 1.0f
            distance == 1 -> 0.98f
            distance == 2 -> 0.96f
            else -> 0.94f
        }
    }

    private fun groupByDay(bills: List<Bill>): List<List<Bill>> {
        if (bills.isEmpty()) return emptyList()
        val sorted = bills.sortedByDescending { it.billTime }
        val groups = mutableListOf<MutableList<Bill>>()
        var currentDay = ""
        var currentGroup = mutableListOf<Bill>()
        for (bill in sorted) {
            val day = dayFormat.format(Date(bill.billTime))
            if (day != currentDay) {
                if (currentGroup.isNotEmpty()) groups.add(currentGroup)
                currentGroup = mutableListOf()
                currentDay = day
            }
            currentGroup.add(bill)
        }
        if (currentGroup.isNotEmpty()) groups.add(currentGroup)
        return groups
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is DisplayItem.Header -> TYPE_HEADER
            is DisplayItem.BillItem -> TYPE_BILL
        }
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_HEADER -> {
                val binding = ItemBillHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                HeaderViewHolder(binding)
            }
            else -> {
                val binding = ItemBillBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                BillViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is DisplayItem.Header -> (holder as HeaderViewHolder).bind(item)
            is DisplayItem.BillItem -> (holder as BillViewHolder).bind(item)
        }

        // 应用叠加效果
        val alpha = getAlphaForPosition(position)
        val translationY = getTranslationYForPosition(position)
        val scale = getScaleForPosition(position)

        holder.itemView.alpha = alpha
        holder.itemView.translationY = translationY
        holder.itemView.scaleX = scale
        holder.itemView.scaleY = scale
    }

    inner class HeaderViewHolder(private val binding: ItemBillHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: DisplayItem.Header) {
            binding.tvDate.text = item.dateText
            binding.tvDate.setTextColor(secondaryColor)
            binding.tvDate.setShadowLayer(3f, 0f, 1f, shadowColor)
        }
    }

    inner class BillViewHolder(private val binding: ItemBillBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: DisplayItem.BillItem) {
            val bill = item.bill
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
                    // 兼容旧的 emoji 图标
                    tvCategoryIcon.text = bill.categoryIcon.ifEmpty { "📦" }
                    tvCategoryIcon.visibility = View.VISIBLE
                    ivCategoryIcon.visibility = View.GONE
                }
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

    /** 为同一天的账单之间画一条细分隔线 */
    class DayDividerDecoration : RecyclerView.ItemDecoration() {
        private val paint = Paint().apply {
            color = Color.parseColor("#15FFFFFF")
            strokeWidth = 1f
        }

        override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
            val adapter = parent.adapter as? BillAdapter ?: return
            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i)
                val pos = parent.getChildAdapterPosition(child)
                if (pos < 0 || pos >= adapter.items.size) continue
                val item = adapter.items[pos]
                // 只在同一天的BillItem之间画线（非最后一条 & 下一条也是BillItem）
                if (item is DisplayItem.BillItem && !item.isLastInDay) {
                    val params = child.layoutParams as RecyclerView.LayoutParams
                    val left = child.paddingLeft + 60f
                    val right = child.width - child.paddingRight - 16f
                    val top = (child.bottom + params.bottomMargin).toFloat()
                    c.drawLine(left, top, right, top, paint)
                }
            }
        }
    }
}
