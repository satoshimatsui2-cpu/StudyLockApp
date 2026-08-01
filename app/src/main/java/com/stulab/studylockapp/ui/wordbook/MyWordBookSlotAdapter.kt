package com.stulab.studylockapp.ui.wordbook

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.stulab.studylockapp.databinding.ItemMyWordBookSlotBinding

class MyWordBookSlotAdapter(
    private val onImportClick: (WordBookSlot) -> Unit,
    private val onDeleteClick: (WordBookSlot) -> Unit,
    private val onRenameClick: (WordBookSlot) -> Unit
) : ListAdapter<WordBookSlot, MyWordBookSlotAdapter.ViewHolder>(DiffCallback()) {

    private var isInteractionEnabled = true

    fun setInteractionEnabled(enabled: Boolean) {
        isInteractionEnabled = enabled
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMyWordBookSlotBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemMyWordBookSlotBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(slot: WordBookSlot) {
            binding.textSlotName.text = slot.displayName
            binding.textWordCount.text = if (slot.isRegistered) {
                "${slot.wordCount}語"
            } else {
                "0語・未登録"
            }

            binding.buttonRename.apply {
                visibility = if (slot.isRegistered) View.VISIBLE else View.GONE
                isEnabled = isInteractionEnabled
                setOnClickListener { onRenameClick(slot) }
            }

            binding.buttonImport.apply {
                text = if (slot.isRegistered) "入れ替え" else "インポート"
                isEnabled = isInteractionEnabled
                setOnClickListener { onImportClick(slot) }
            }

            binding.buttonDelete.apply {
                visibility = if (slot.isRegistered) View.VISIBLE else View.GONE
                isEnabled = isInteractionEnabled
                setOnClickListener { onDeleteClick(slot) }
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<WordBookSlot>() {
        override fun areItemsTheSame(oldItem: WordBookSlot, newItem: WordBookSlot): Boolean {
            return oldItem.grade == newItem.grade
        }
        override fun areContentsTheSame(oldItem: WordBookSlot, newItem: WordBookSlot): Boolean {
            return oldItem == newItem
        }
    }
}
