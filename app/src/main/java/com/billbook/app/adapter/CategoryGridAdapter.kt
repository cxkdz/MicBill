package com.billbook.app.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.billbook.app.databinding.ItemCategoryGridBinding
import com.billbook.app.model.Category
import com.billbook.app.util.CategoryIconMapper

class CategoryGridAdapter(
    private val categories: List<Category>,
    private val onCategoryClick: (Category) -> Unit
) : RecyclerView.Adapter<CategoryGridAdapter.ViewHolder>() {

    private var selectedPosition = -1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCategoryGridBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(categories[position], position == selectedPosition)
    }

    override fun getItemCount() = categories.size

    inner class ViewHolder(private val binding: ItemCategoryGridBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(category: Category, isSelected: Boolean) {
            binding.apply {
                // 使用 CategoryIconMapper 获取图标资源 ID
                val iconResId = if (category.iconResId != 0) {
                    category.iconResId
                } else {
                    CategoryIconMapper.getIconResId(category.name, category.type)
                }

                if (iconResId != 0) {
                    ivIcon.setImageResource(iconResId)
                    ivIcon.visibility = android.view.View.VISIBLE
                    tvIcon.visibility = android.view.View.GONE
                } else {
                    // 兼容旧的 emoji 图标
                    tvIcon.text = category.icon
                    tvIcon.visibility = android.view.View.VISIBLE
                    ivIcon.visibility = android.view.View.GONE
                }
                tvName.text = category.name

                // 选中状态高亮
                if (isSelected) {
                    root.setBackgroundColor(Color.parseColor("#E3F2FD"))
                    ivIcon.scaleX = 1.2f
                    ivIcon.scaleY = 1.2f
                    tvIcon.scaleX = 1.2f
                    tvIcon.scaleY = 1.2f
                } else {
                    root.setBackgroundColor(Color.TRANSPARENT)
                    ivIcon.scaleX = 1.0f
                    ivIcon.scaleY = 1.0f
                    tvIcon.scaleX = 1.0f
                    tvIcon.scaleY = 1.0f
                }

                root.setOnClickListener {
                    val oldPos = selectedPosition
                    selectedPosition = adapterPosition
                    if (oldPos >= 0) notifyItemChanged(oldPos)
                    notifyItemChanged(selectedPosition)
                    onCategoryClick(category)
                }
            }
        }
    }
}
