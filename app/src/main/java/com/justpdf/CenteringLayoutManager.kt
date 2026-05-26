package com.justpdf

import android.content.Context
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * A LinearLayoutManager that vertically centres its content when the total
 * content height is less than the RecyclerView's height.
 *
 * This ensures a single-page PDF (or any short document) appears centred on
 * screen rather than pinned to the top.  Multi-page documents that exceed the
 * viewport are unaffected — they scroll normally from the top.
 */
class CenteringLayoutManager(context: Context) : LinearLayoutManager(context) {

    override fun generateDefaultLayoutParams(): RecyclerView.LayoutParams =
        RecyclerView.LayoutParams(
            RecyclerView.LayoutParams.MATCH_PARENT,
            RecyclerView.LayoutParams.WRAP_CONTENT
        )

    override fun onLayoutChildren(
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State
    ) {
        super.onLayoutChildren(recycler, state)
        centerIfNeeded()
    }

    private fun centerIfNeeded() {
        if (childCount == 0) return

        val totalContentHeight = (0 until childCount).sumOf { getChildAt(it)?.height ?: 0 }
        val availableHeight = height - paddingTop - paddingBottom

        if (totalContentHeight < availableHeight) {
            val topOffset = (availableHeight - totalContentHeight) / 2
            offsetChildrenVertical(topOffset - (getChildAt(0)?.top ?: 0))
        }
    }
}
