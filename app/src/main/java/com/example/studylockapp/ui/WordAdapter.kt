package com.example.studylockapp.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.studylockapp.R

class WordAdapter(private var items: List<WordDisplayItem>) :
    RecyclerView.Adapter<WordAdapter.WordViewHolder>() {

    class WordViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val colWord: TextView = itemView.findViewById(R.id.col_word)
        val colGrade: TextView = itemView.findViewById(R.id.col_grade)

        val colMLevel: TextView = itemView.findViewById(R.id.col_m_level)
        val colMDue: TextView = itemView.findViewById(R.id.col_m_due)

        val colLLevel: TextView = itemView.findViewById(R.id.col_l_level)
        val colLDue: TextView = itemView.findViewById(R.id.col_l_due)

        val colJeLevel: TextView = itemView.findViewById(R.id.col_je_level)
        val colJeDue: TextView = itemView.findViewById(R.id.col_je_due)

        val colEe1Level: TextView = itemView.findViewById(R.id.col_ee1_level)
        val colEe1Due: TextView = itemView.findViewById(R.id.col_ee1_due)

        val colEe2Level: TextView = itemView.findViewById(R.id.col_ee2_level)
        val colEe2Due: TextView = itemView.findViewById(R.id.col_ee2_due)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WordViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_word, parent, false)
        return WordViewHolder(view)
    }

    override fun onBindViewHolder(holder: WordViewHolder, position: Int) {
        val item = items[position]
        holder.colWord.text = item.word
        holder.colGrade.text = item.grade

        holder.colMLevel.text = item.mLevel?.toString() ?: "-"
        holder.colMDue.text = item.mDueText

        holder.colLLevel.text = item.lLevel?.toString() ?: "-"
        holder.colLDue.text = item.lDueText

        holder.colJeLevel.text = item.jeLevel?.toString() ?: "-"
        holder.colJeDue.text = item.jeDueText

        holder.colEe1Level.text = item.ee1Level?.toString() ?: "-"
        holder.colEe1Due.text = item.ee1DueText

        holder.colEe2Level.text = item.ee2Level?.toString() ?: "-"
        holder.colEe2Due.text = item.ee2DueText
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<WordDisplayItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}