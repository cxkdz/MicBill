package com.billbook.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.billbook.app.R
import com.billbook.app.model.Category
import com.billbook.app.util.CategoryIconMapper
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

    fun setSelectedCategoryId(id: Long) {
        val newPos = categories.indexOfFirst { it.id == id }
        if (newPos == selectedPosition) return
        val old = selectedPosition
        selectedPosition = newPos
        if (old != -1) notifyItemChanged(old)
        if (newPos != -1) notifyItemChanged(newPos)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val iconContainer: FrameLayout = view.findViewById(R.id.iconContainer)
        val ivIcon: ImageView = view.findViewById(R.id.ivIcon)
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

        // 优先使用自定义的 iconResId，否则使用 CategoryIconMapper 获取
        val iconResId = if (category.iconResId != 0) {
            category.iconResId
        } else {
            CategoryIconMapper.getIconResId(category.name, category.type)
        }

        if (iconResId != 0) {
            holder.ivIcon.setImageResource(iconResId)
            holder.ivIcon.visibility = View.VISIBLE
            holder.tvIcon.visibility = View.GONE
        } else {
            // 兼容旧的 emoji 图标
            holder.tvIcon.text = category.icon
            holder.tvIcon.visibility = View.VISIBLE
            holder.ivIcon.visibility = View.GONE
        }

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
