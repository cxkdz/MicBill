package com.billbook.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.billbook.app.R
import com.billbook.app.model.Category
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

/**
 * 二次元风格分类适配器
 * - 半透明圆形玻璃卡片
 * - 选中态：发光边框 + 弹簧缩放
 * - icon 层与风格层分离（emoji 不变）
 */
class AnimeCategoryAdapter(
    private val categories: List<Category>,
    private val onCategoryClick: (Category) -> Unit
) : RecyclerView.Adapter<AnimeCategoryAdapter.ViewHolder>() {

    var selectedPosition: Int = -1
        private set

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val iconContainer: FrameLayout = view.findViewById(R.id.iconContainer)
        val tvIcon: TextView = view.findViewById(R.id.tvIcon)
        val tvName: TextView = view.findViewById(R.id.tvName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_anime_category, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val category = categories[position]
        holder.tvIcon.text = category.icon
        holder.tvName.text = category.name

        val isSelected = position == selectedPosition
        holder.iconContainer.setBackgroundResource(
            if (isSelected) R.drawable.bg_anime_card_selected
            else R.drawable.bg_anime_card
        )
        holder.tvName.setTextColor(
            if (isSelected) 0xE0FFFFFF.toInt()
            else 0x80FFFFFF.toInt()
        )

        holder.itemView.setOnClickListener {
            val oldPos = selectedPosition
            selectedPosition = holder.adapterPosition
            if (oldPos != -1) notifyItemChanged(oldPos)
            notifyItemChanged(selectedPosition)

            // 弹簧缩放动画
            SpringAnimation(holder.iconContainer, DynamicAnimation.SCALE_X, 1.15f).apply {
                spring.stiffness = SpringForce.STIFFNESS_HIGH
                spring.dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
                setStartValue(0.85f)
                start()
            }
            SpringAnimation(holder.iconContainer, DynamicAnimation.SCALE_Y, 1.15f).apply {
                spring.stiffness = SpringForce.STIFFNESS_HIGH
                spring.dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
                setStartValue(0.85f)
                start()
            }

            onCategoryClick(category)
        }

        // 选中态的静态缩放
        holder.iconContainer.scaleX = if (isSelected) 1.05f else 1f
        holder.iconContainer.scaleY = if (isSelected) 1.05f else 1f
    }

    override fun getItemCount() = categories.size
}
