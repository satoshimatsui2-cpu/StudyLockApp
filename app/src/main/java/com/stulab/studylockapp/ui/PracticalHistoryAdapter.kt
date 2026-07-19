package com.stulab.studylockapp.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.stulab.studylockapp.R
import com.stulab.studylockapp.data.db.PracticalHistoryEntity
import com.stulab.studylockapp.databinding.ItemPracticalHistoryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 実践テスト履歴のリスト表示用アダプタ
 */
class PracticalHistoryAdapter(
    private val onItemClick: (PracticalHistoryEntity) -> Unit
) : ListAdapter<PracticalHistoryEntity, PracticalHistoryAdapter.ViewHolder>(DiffCallback) {

    private val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPracticalHistoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemPracticalHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: PracticalHistoryEntity) {
            val context = binding.root.context
            
            // 基本情報の設定
            val typeName = if (item.questionType == "LISTENING") "リスニング" else "穴埋め"
            binding.textTypeAndGrade.text = context.getString(R.string.practical_history_type_grade, typeName, item.grade)
            binding.textDate.text = dateFormat.format(Date(item.answeredAt))
            binding.textPoints.text = context.getString(R.string.practical_history_points_format, if (item.points > 0) "+${item.points}" else "${item.points}")
            
            // 回答セクション（一覧での簡易表示）
            binding.textSelectedAnswer.text = context.getString(R.string.practical_history_your_answer, item.selectedAnswer)
            binding.textCorrectAnswer.text = context.getString(R.string.practical_history_correct_answer, item.correctAnswer)
            binding.textExplanation.text = context.getString(R.string.practical_history_explanation_format, item.explanation)

            // 状態に応じたスタイル切り替え
            when (item.resultStatus) {
                "CORRECT" -> {
                    binding.imageResultIcon.setImageResource(R.drawable.ic_round_stars_24)
                    binding.imageResultIcon.imageTintList = ColorStateList.valueOf(
                        ContextCompat.getColor(context, R.color.choice_correct_text)
                    )
                    binding.textPoints.setTextColor(ContextCompat.getColor(context, R.color.choice_correct_text))
                }
                "WRONG" -> {
                    binding.imageResultIcon.setImageResource(R.drawable.ic_help_outline_24)
                    binding.imageResultIcon.imageTintList = ColorStateList.valueOf(
                        ContextCompat.getColor(context, R.color.choice_wrong_text)
                    )
                    binding.textPoints.setTextColor(ContextCompat.getColor(context, R.color.choice_wrong_text))
                }
                else -> { // UNSCORED
                    binding.imageResultIcon.setImageResource(R.drawable.ic_headphones_24)
                    binding.imageResultIcon.imageTintList = ColorStateList.valueOf(Color.GRAY)
                    binding.textPoints.setTextColor(Color.GRAY)
                    binding.textPoints.text = context.getString(R.string.practical_history_unscored_label)
                }
            }

            // リスニング問題の場合のヒント表示
            binding.textListeningHint.visibility = if (item.questionType == "LISTENING") View.VISIBLE else View.GONE

            // アイテムタップ時のコールバック
            binding.root.setOnClickListener { onItemClick(item) }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<PracticalHistoryEntity>() {
            override fun areItemsTheSame(oldItem: PracticalHistoryEntity, newItem: PracticalHistoryEntity): Boolean {
                return oldItem.id == newItem.id
            }
            override fun areContentsTheSame(oldItem: PracticalHistoryEntity, newItem: PracticalHistoryEntity): Boolean {
                return oldItem == newItem
            }
        }
    }
}
