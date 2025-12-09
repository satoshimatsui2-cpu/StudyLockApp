package com.example.studylockapp.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.studylockapp.R
import com.example.studylockapp.data.WordEntity

class WordAdapter(private var items: List<WordEntity>) :
    RecyclerView.Adapter<WordAdapter.WordViewHolder>() {

    class WordViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val colWord: TextView = itemView.findViewById(R.id.col_word)
        val colJapanese: TextView = itemView.findViewById(R.id.col_japanese)
        val colGrade: TextView = itemView.findViewById(R.id.col_grade)
        val colPos: TextView = itemView.findViewById(R.id.col_pos)
        val colCategory: TextView = itemView.findViewById(R.id.col_category)
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
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<WordEntity>) {
        items = newItems
        notifyDataSetChanged()
    }
}

