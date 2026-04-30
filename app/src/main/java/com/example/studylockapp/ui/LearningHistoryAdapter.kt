package com.example.studylockapp.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.studylockapp.R
import com.example.studylockapp.data.WordHistoryItem

class LearningHistoryAdapter(
    private val onEditClick: (WordHistoryItem) -> Unit
) : ListAdapter<WordHistoryItem, LearningHistoryAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_learning_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, onEditClick) {
            item.isExpanded = !item.isExpanded
            notifyItemChanged(position)
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textWord: TextView = itemView.findViewById(R.id.text_word)
        private val textMeaning: TextView = itemView.findViewById(R.id.text_meaning)
        private val textGradeLevel: TextView = itemView.findViewById(R.id.text_grade_level)
        private val textReviewSummary: TextView = itemView.findViewById(R.id.text_review_summary)
        private val layoutIcons: LinearLayout = itemView.findViewById(R.id.layout_status_icons)
        private val layoutDetail: LinearLayout = itemView.findViewById(R.id.layout_detail_container)
        private val textDetail: TextView = itemView.findViewById(R.id.text_detail_content)

        fun bind(item: WordHistoryItem, onEditClick: (WordHistoryItem) -> Unit, onClick: () -> Unit) {
            textWord.text = item.word
            textMeaning.text = item.japanese
            textGradeLevel.text = "${item.gradeLabel} / ${item.levelLabel}"
            textReviewSummary.text = "${item.reviewStatusLabel} ・ ${item.scoreLabel}"

            // 復習待ちの場合は赤色にするなどの調整
            if (item.reviewStatusLabel == "復習待ち") {
                textReviewSummary.setTextColor(Color.parseColor("#F44336"))
            } else {
                textReviewSummary.setTextColor(Color.parseColor("#757575"))
            }

            // バッジの生成
            layoutIcons.removeAllViews()
            layoutIcons.addView(createBadge(item.tierLabel, getTierColor(item.tierLabel)))
            if (item.hasPendingListenReview) {
                layoutIcons.addView(createBadge("音声復習", Color.parseColor("#FF9800")))
            }

            // 展開・折りたたみ制御
            layoutDetail.visibility = if (item.isExpanded) View.VISIBLE else View.GONE
            itemView.setOnClickListener { onClick() }

            if (item.isExpanded) {
                val detailText = buildString {
                    append("説明: ${item.description}\n")
                    append("例文: ${item.sentence}\n")
                    append("訳: ${item.japaneseSentence}\n")
                    append("次回モード: ${item.scheduledModeLabel}\n")
                    append(item.lastSeenLabel)
                }
                textDetail.text = detailText
            }
        }

        private fun createBadge(text: String, color: Int): TextView {
            val tv = TextView(itemView.context)
            tv.text = text
            tv.textSize = 10f
            tv.setTextColor(Color.WHITE)
            tv.setPadding(12, 4, 12, 4)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(4, 0, 4, 0)
            tv.layoutParams = params
            
            val bg = ContextCompat.getDrawable(itemView.context, R.drawable.bg_circle_indicator)?.mutate()
            bg?.setTint(color)
            tv.background = bg
            return tv
        }

        private fun getTierColor(tier: String): Int {
            return when (tier) {
                "長期マスター" -> Color.parseColor("#4DD0E1") // クリスタル
                "基礎マスター" -> Color.parseColor("#FFCA28") // ゴールド
                else -> Color.parseColor("#9E9E9E") // シルバー
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<WordHistoryItem>() {
        override fun areItemsTheSame(oldItem: WordHistoryItem, newItem: WordHistoryItem) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: WordHistoryItem, newItem: WordHistoryItem) = oldItem == newItem
    }
}
