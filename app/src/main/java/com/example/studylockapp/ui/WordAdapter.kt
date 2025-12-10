package com.example.studylockapp.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.studylockapp.R
import java.time.LocalDate

class WordAdapter(private var items: List<WordDisplayItem>) :
    RecyclerView.Adapter<WordAdapter.WordViewHolder>() {

    class WordViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val colWord: TextView = itemView.findViewById(R.id.col_word)
        val colJapanese: TextView = itemView.findViewById(R.id.col_japanese)
        val colGrade: TextView = itemView.findViewById(R.id.col_grade)
        val colPos: TextView = itemView.findViewById(R.id.col_pos)
        val colCategory: TextView = itemView.findViewById(R.id.col_category)
        val colMLevel: TextView = itemView.findViewById(R.id.col_m_level)
        val colMDue: TextView = itemView.findViewById(R.id.col_m_due)
        val colLLevel: TextView = itemView.findViewById(R.id.col_l_level)
        val colLDue: TextView = itemView.findViewById(R.id.col_l_due)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WordViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_word, parent, false)
        return WordViewHolder(view)
    }

    override fun onBindViewHolder(holder: WordViewHolder, position: Int) {
        val item = items[position]
        holder.colWord.text = item.word
        holder.colJapanese.text = item.japanese
        holder.colGrade.text = item.grade
        holder.colPos.text = item.pos ?: "-"
        holder.colCategory.text = item.category
        holder.colMLevel.text = item.mLevel?.toString() ?: "-"
        holder.colMDue.text = toDateString(item.mDue)
        holder.colLLevel.text = item.lLevel?.toString() ?: "-"
        holder.colLDue.text = toDateString(item.lDue)
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<WordDisplayItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    private fun toDateString(epochDay: Long?): String {
        if (epochDay == null || epochDay <= 0) return "-"
        return LocalDate.ofEpochDay(epochDay).toString()
    }
}
