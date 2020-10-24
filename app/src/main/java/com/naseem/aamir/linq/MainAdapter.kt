package com.naseem.aamir.linq

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.max

class MainAdapter(private val shareButton: View) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private var mItems = mutableListOf<String>()
    private var whiteListed = mutableListOf<Boolean>()
    private var checked = mutableListOf<Boolean>()

    class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val link: TextView = itemView.findViewById(R.id.link)
        val check: CheckBox = itemView.findViewById(R.id.check_box)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (viewType == 0) {
            val inflatedView =
                LayoutInflater.from(parent.context).inflate(R.layout.link_item, parent, false)
            return Holder(inflatedView)
        } else {
            val v = View(parent.context)
            return object : RecyclerView.ViewHolder(v) {}
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (whiteListed[position]) 0 else 1
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val type = getItemViewType(position)
        if (type > 0) return
        val vHolder = holder as Holder
        vHolder.link.text = mItems[position]
        vHolder.check.setOnCheckedChangeListener(null)
        vHolder.check.isChecked = checked[position]
        vHolder.check.setOnCheckedChangeListener { view, isChecked ->
            checked[position] = isChecked
            shareButton.isEnabled = checked.any { it }
        }
    }

    override fun getItemCount(): Int {
        return mItems.size
    }

    fun blackList(position: Int) {
        whiteListed[position] = false
        checked[position] = false
        shareButton.isEnabled = checked.any { it }
        notifyItemChanged(position)
    }

    fun addItems(items: List<String>) {
        items.forEach {
            var flag = false
            mItems.forEachIndexed { i, p ->
                val score = lcs(it, p).toDouble() / max(it.length, p.length)
                if (score > 0.85 && whiteListed[i]) {
                    mItems[i] = it
                    flag = true
                    notifyItemChanged(i)
                    return@forEachIndexed
                } else if (score > 0.85) { //blacklisted, don't do anything
                    flag = true
                }
            }
            if (flag.not() && whiteListed.count { it } < 5) {
                val pos = mItems.size
                mItems.add(it)
                whiteListed.add(true)
                checked.add(false)
                notifyItemInserted(pos)
            }
        }
    }

    fun reset() {
        mItems.clear()
        whiteListed.clear()
        checked.clear()
        shareButton.isEnabled = false
        notifyDataSetChanged()
    }

    private fun lcs(s1: String, s2: String): Int {
        val l1 = s1.length + 1
        val l2 = s2.length + 1
        val dp = Array(l1) { IntArray(l2) }
        for (i in 0 until l1) dp[i][0] = 0
        for (i in 0 until l2) dp[0][i] = 0
        for (i in 1 until l1) {
            for (j in 1 until l2) {
                if (s1[i - 1] == s2[j - 1]) dp[i][j] = dp[i - 1][j - 1] + 1
                else dp[i][j] = max(dp[i - 1][j], dp[i][j - 1])
            }
        }
        return dp[l1 - 1][l2 - 1]
    }

    fun getSelected(): List<String> {
        return mItems.filterIndexed { i, v -> checked[i] }
    }
}