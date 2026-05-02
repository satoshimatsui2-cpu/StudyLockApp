package com.example.studylockapp.ui

import android.graphics.Color
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.studylockapp.R
import com.example.studylockapp.data.WordHistoryItem
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class LearningHistoryAdapter(
    private val onEditClick: (WordHistoryItem) -> Unit,
    private val onWordCheckClick: (WordHistoryItem) -> Unit,
    private val onSentenceCheckClick: (WordHistoryItem) -> Unit
) : ListAdapter<WordHistoryItem, LearningHistoryAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_learning_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, onEditClick, onWordCheckClick, onSentenceCheckClick) {
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
        private val buttonWordCheck: MaterialButton = itemView.findViewById(R.id.button_voice_check)
        private val buttonSentenceCheck: MaterialButton = itemView.findViewById(R.id.button_sentence_check)

        // Status Chip Components
        private val layoutStatusChip: LinearLayout = itemView.findViewById(R.id.layout_status_chip)
        private val imageStatusIcon: ImageView = itemView.findViewById(R.id.image_status_icon)
        private val textStatusLabel: TextView = itemView.findViewById(R.id.text_status_label)
        private val textCountSuccess: TextView = itemView.findViewById(R.id.text_count_success)
        private val textCountFailure: TextView = itemView.findViewById(R.id.text_count_failure)

        fun bind(
            item: WordHistoryItem,
            onEditClick: (WordHistoryItem) -> Unit,
            onWordCheckClick: (WordHistoryItem) -> Unit,
            onSentenceCheckClick: (WordHistoryItem) -> Unit,
            onClick: () -> Unit
        ) {
            textWord.text = item.word
            textMeaning.text = item.japanese
            textGradeLevel.text = "${item.gradeLabel} / ${item.levelLabel}"
            
            // 豪華な枠線の演出 (両方OKならゴールド)
            val cardView = itemView as? MaterialCardView
            if (item.isWordVoiceChecked && item.isSentenceVoiceChecked) {
                cardView?.strokeColor = Color.parseColor("#F9A825") // 控えめなゴールド
                cardView?.strokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1.5f, itemView.resources.displayMetrics).toInt()
            } else if (item.isWordVoiceChecked || item.isSentenceVoiceChecked) {
                cardView?.strokeColor = Color.parseColor("#EEEEEE") // 片方だけならごく薄いグレー
                cardView?.strokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1.0f, itemView.resources.displayMetrics).toInt()
            } else {
                cardView?.strokeWidth = 0
            }

            // 状態チップの制御
            when {
                item.isNew -> {
                    textStatusLabel.text = "NEW"
                    textStatusLabel.setTextColor(Color.parseColor("#E91E63"))
                    imageStatusIcon.visibility = View.GONE
                    layoutStatusChip.background.mutate().setTint(Color.parseColor("#FCE4EC"))
                }
                item.isReviewWaiting -> {
                    textStatusLabel.text = "復習"
                    textStatusLabel.setTextColor(Color.parseColor("#F44336"))
                    imageStatusIcon.visibility = View.VISIBLE
                    imageStatusIcon.setImageResource(R.drawable.ic_refresh_24)
                    imageStatusIcon.drawable?.setTint(Color.parseColor("#F44336"))
                    layoutStatusChip.background.mutate().setTint(Color.parseColor("#FFEBEE"))
                }
                else -> {
                    textStatusLabel.text = "学習済"
                    textStatusLabel.setTextColor(Color.parseColor("#757575"))
                    imageStatusIcon.visibility = View.GONE
                    layoutStatusChip.background.mutate().setTint(Color.parseColor("#F5F5F5"))
                }
            }

            // スコア表示
            textCountSuccess.text = "○ ${item.successCount}"
            textCountFailure.text = "× ${item.failureCount}"
            
            val scoreVisibility = if (item.isNew) View.GONE else View.VISIBLE
            textCountSuccess.visibility = scoreVisibility
            textCountFailure.visibility = scoreVisibility

            textReviewSummary.text = item.reviewStatusLabel
            textReviewSummary.visibility = if (item.isReviewWaiting || item.isNew) View.GONE else View.VISIBLE

            // バッジの生成
            layoutIcons.removeAllViews()
            layoutIcons.addView(createBadge(item.tierLabel, getTierColor(item.tierLabel)))
            
            // 音声チェックバッジ
            if (item.isWordVoiceChecked) {
                layoutIcons.addView(createBadge("🎙 単語OK", Color.parseColor("#4CAF50"))) // Green
            }
            if (item.isSentenceVoiceChecked) {
                layoutIcons.addView(createBadge("📖 例文OK", Color.parseColor("#4527A0"))) // Purple
            }

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
                
                buttonWordCheck.setOnClickListener { onWordCheckClick(item) }
                
                // 例文が3語以上あるかチェック
                val words = item.sentence.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                if (words.size >= 3) {
                    buttonSentenceCheck.visibility = View.VISIBLE
                    buttonSentenceCheck.setOnClickListener { onSentenceCheckClick(item) }
                } else {
                    buttonSentenceCheck.visibility = View.GONE
                }
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
                "長期マスター" -> Color.parseColor("#4DD0E1")
                "基礎マスター" -> Color.parseColor("#FFCA28")
                else -> Color.parseColor("#9E9E9E")
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<WordHistoryItem>() {
        override fun areItemsTheSame(oldItem: WordHistoryItem, newItem: WordHistoryItem): Boolean {
            return oldItem.id == newItem.id
        }
        override fun areContentsTheSame(oldItem: WordHistoryItem, newItem: WordHistoryItem): Boolean {
            return oldItem == newItem
        }
    }
}
