package com.billbook.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.billbook.app.R
import com.billbook.app.model.Category

class CategoryPagerAdapter(
    private val categories: List<Category>,
    private val itemsPerPage: Int = 12,
    private val onCategoryClick: (Category) -> Unit
) : RecyclerView.Adapter<CategoryPagerAdapter.PageViewHolder>() {

    private var selectedCategoryId: Long = -1
    private val pageAdapters = mutableListOf<AnimeCategoryAdapter>()

    class PageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val rvPage: RecyclerView = view.findViewById(R.id.rvPageCategories)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category_page, parent, false)
        val holder = PageViewHolder(view)
        holder.rvPage.layoutManager = GridLayoutManager(parent.context, 4)
        holder.rvPage.isNestedScrollingEnabled = false
        return holder
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        val start = position * itemsPerPage
        val end = minOf(start + itemsPerPage, categories.size)
        val pageItems = categories.subList(start, end)

        val adapter = AnimeCategoryAdapter(pageItems) { category ->
            selectedCategoryId = category.id
            onCategoryClick(category)
            pageAdapters.forEach { it.setSelectedCategoryId(selectedCategoryId) }
        }
        adapter.setSelectedCategoryId(selectedCategoryId)

        holder.rvPage.adapter = adapter

        if (pageAdapters.size <= position) {
            pageAdapters.add(adapter)
        } else {
            pageAdapters[position] = adapter
        }
    }

    override fun getItemCount(): Int {
        return if (categories.isEmpty()) 0 else (categories.size + itemsPerPage - 1) / itemsPerPage
    }

    fun setSelectedCategoryId(id: Long) {
        selectedCategoryId = id
        pageAdapters.forEach { it.setSelectedCategoryId(id) }
    }

    /** 某个分类落在第几页；找不到返回 -1。避免调用方再写死 itemsPerPage。 */
    fun pageIndexFor(categoryId: Long): Int {
        val index = categories.indexOfFirst { it.id == categoryId }
        return if (index < 0) -1 else index / itemsPerPage
    }
}
