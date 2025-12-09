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
        val textWord: TextView = itemView.findViewById(R.id.text_word)
        val textJapanese: TextView = itemView.findViewById(R.id.text_japanese)
        val textDetail: TextView = itemView.findViewById(R.id.text_detail)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WordViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_word, parent, false)
        return WordViewHolder(view)
    }

    override fun onBindViewHolder(holder: WordViewHolder, position: Int) {
        val item = items[position]
        holder.textWord.text = item.word
        holder.textJapanese.text = item.japanese
        holder.textDetail.text = "grade: ${item.grade} / pos: ${item.pos ?: "-"} / cat: ${item.category}"
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<WordEntity>) {
        items = newItems
        notifyDataSetChanged()
    }
}

