package com.billbook.app.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.billbook.app.databinding.ItemCategoryGridBinding
import com.billbook.app.model.Category

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
                tvIcon.text = category.icon
                tvName.text = category.name

                // 选中状态高亮
                if (isSelected) {
                    root.setBackgroundColor(Color.parseColor("#E3F2FD"))
                    tvIcon.scaleX = 1.2f
                    tvIcon.scaleY = 1.2f
                } else {
                    root.setBackgroundColor(Color.TRANSPARENT)
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
