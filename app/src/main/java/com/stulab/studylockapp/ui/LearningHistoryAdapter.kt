package com.stulab.studylockapp.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
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
import com.stulab.studylockapp.R
import com.stulab.studylockapp.data.WordHistoryItem
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
        private val textMasteryIcon: TextView = itemView.findViewById(R.id.text_mastery_icon)
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
            
            val cardView = itemView as? MaterialCardView
            if (item.isWordVoiceChecked && item.isSentenceVoiceChecked) {
                cardView?.strokeColor = Color.parseColor("#F9A825")
                cardView?.strokeWidth = dpToPx(1.5f)
            } else if (item.isWordVoiceChecked || item.isSentenceVoiceChecked) {
                cardView?.strokeColor = Color.parseColor("#EEEEEE")
                cardView?.strokeWidth = dpToPx(1.0f)
            } else {
                cardView?.strokeWidth = 0
            }

            // Status Chip
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

            textCountSuccess.text = "○ ${item.successCount}"
            textCountFailure.text = "× ${item.failureCount}"
            val scoreVisibility = if (item.isNew) View.GONE else View.VISIBLE
            textCountSuccess.visibility = scoreVisibility
            textCountFailure.visibility = scoreVisibility

            textReviewSummary.text = item.reviewStatusLabel
            textReviewSummary.visibility = if (item.isReviewWaiting || item.isNew) View.GONE else View.VISIBLE

            // Mastery Icon
            textMasteryIcon.text = when (item.tierLabel) {
                "長期マスター" -> "🏆"
                "基礎マスター" -> "✪"
                else -> ""
            }

            // Voice Badges (Chips)
            layoutIcons.removeAllViews()
            if (item.isWordVoiceChecked) {
                layoutIcons.addView(createVoiceChip("単語OK", Color.parseColor("#4CAF50")))
            }
            if (item.isSentenceVoiceChecked) {
                layoutIcons.addView(createVoiceChip("例文OK", Color.parseColor("#4527A0")))
            }
            if (item.hasPendingListenReview) {
                layoutIcons.addView(createVoiceChip("音声復習", Color.parseColor("#FF9800")))
            }

            // Expand/Collapse logic
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
                
                // Initialize buttons to prevent reuse issues
                buttonWordCheck.apply {
                    text = "単語"
                    setIconResource(R.drawable.ic_mic_24)
                    visibility = View.VISIBLE
                    backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F8F8F8"))
                    strokeColor = ColorStateList.valueOf(Color.parseColor("#DDDDDD"))
                    setOnClickListener { onWordCheckClick(item) }
                }

                val words = item.sentence.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                buttonSentenceCheck.apply {
                    text = "例文"
                    setIconResource(R.drawable.ic_mic_24)
                    backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F8F8F8"))
                    strokeColor = ColorStateList.valueOf(Color.parseColor("#DDDDDD"))
                    if (words.size >= 3) {
                        visibility = View.VISIBLE
                        setOnClickListener { onSentenceCheckClick(item) }
                    } else {
                        visibility = View.GONE
                    }
                }
            }
        }

        private fun createVoiceChip(label: String, color: Int): View {
            val chip = LinearLayout(itemView.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dpToPx(6f), dpToPx(2f), dpToPx(8f), dpToPx(2f))
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(0, dpToPx(2f), 0, dpToPx(2f))
                layoutParams = params
                
                val bg = ContextCompat.getDrawable(context, R.drawable.bg_status_chip)?.mutate()
                bg?.setTint(color)
                background = bg
            }

            val icon = ImageView(itemView.context).apply {
                setImageResource(R.drawable.ic_mic_24)
                imageTintList = ColorStateList.valueOf(Color.WHITE)
                val iconSize = dpToPx(12f)
                layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                    marginEnd = dpToPx(4f)
                }
            }

            val text = TextView(itemView.context).apply {
                text = label
                textSize = 11f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
            }

            chip.addView(icon)
            chip.addView(text)
            return chip
        }

        private fun dpToPx(dp: Float): Int {
            return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                itemView.resources.displayMetrics
            ).toInt()
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
