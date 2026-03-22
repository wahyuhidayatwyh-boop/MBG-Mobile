package com.why.mbgdapur

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.NumberFormat
import java.util.*

class HistoryAdapter(
    private var historyList: List<HistoryReport>,
    private val onSelectionChanged: (Int) -> Unit,
    private val onItemClick: (HistoryReport) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    private var isSelectionMode = false
    private val selectedItems = mutableSetOf<String>()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDate: TextView = view.findViewById(R.id.tvItemDate)
        val tvSummary: TextView = view.findViewById(R.id.tvItemSummary)
        val cbSelect: CheckBox = view.findViewById(R.id.cbSelectHistory)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_history_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val report = historyList[position]
        holder.tvDate.text = report.id
        
        val revenueFormatted = NumberFormat.getCurrencyInstance(Locale("id", "ID")).format(report.total_pendapatan)
        holder.tvSummary.text = "${report.porsi_realisasi} Porsi • $revenueFormatted"

        holder.cbSelect.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
        holder.cbSelect.isChecked = selectedItems.contains(report.id)

        holder.cbSelect.setOnClickListener {
            toggleSelection(report.id)
        }

        holder.itemView.setOnClickListener {
            if (isSelectionMode) {
                toggleSelection(report.id)
            } else {
                onItemClick(report)
            }
        }

        holder.itemView.setOnLongClickListener {
            if (!isSelectionMode) {
                setSelectionMode(true)
                toggleSelection(report.id)
                true
            } else false
        }
    }

    private fun toggleSelection(id: String) {
        if (selectedItems.contains(id)) {
            selectedItems.remove(id)
        } else {
            selectedItems.add(id)
        }
        notifyDataSetChanged()
        onSelectionChanged(selectedItems.size)
    }

    fun setSelectionMode(enabled: Boolean) {
        isSelectionMode = enabled
        if (!enabled) selectedItems.clear()
        notifyDataSetChanged()
        onSelectionChanged(selectedItems.size)
    }

    fun selectAll() {
        selectedItems.clear()
        historyList.forEach { selectedItems.add(it.id) }
        notifyDataSetChanged()
        onSelectionChanged(selectedItems.size)
    }

    fun getSelectedIds(): List<String> = selectedItems.toList()

    fun isSelectionModeActive() = isSelectionMode

    override fun getItemCount() = historyList.size

    fun updateData(newList: List<HistoryReport>) {
        historyList = newList
        notifyDataSetChanged()
    }
}
