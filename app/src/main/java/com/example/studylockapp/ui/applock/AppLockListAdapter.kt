package com.example.studylockapp.ui.applock

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.materialswitch.MaterialSwitch
import androidx.recyclerview.widget.RecyclerView
import com.example.studylockapp.R

data class AppLockDisplayItem(
    val packageName: String,
    val label: String,
    val isLocked: Boolean
)

class AppLockListAdapter(
    private var items: List<AppLockDisplayItem>,
    private val onToggle: (AppLockDisplayItem, Boolean) -> Unit
) : RecyclerView.Adapter<AppLockListAdapter.ViewHolder>() {

    class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val label: TextView = v.findViewById(R.id.text_app_label)
        val switch: MaterialSwitch = v.findViewById(R.id.switch_lock)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_lock_app, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.label.text = item.label
        holder.switch.setOnCheckedChangeListener(null)
        holder.switch.isChecked = item.isLocked
        holder.switch.setOnCheckedChangeListener { _, checked ->
            onToggle(item, checked)
        }
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newList: List<AppLockDisplayItem>) {
        items = newList
        notifyDataSetChanged()
    }
}