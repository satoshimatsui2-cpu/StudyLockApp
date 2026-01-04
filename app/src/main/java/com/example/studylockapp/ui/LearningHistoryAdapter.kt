package com.example.studylockapp.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.studylockapp.R
import com.example.studylockapp.data.WordHistoryItem
import com.example.studylockapp.data.ModeStatus
import java.text.SimpleDateFormat
import java.util.*

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
            // タップ時の処理: 展開フラグを反転して更新
            item.isExpanded = !item.isExpanded
            notifyItemChanged(position)
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textWord: TextView = itemView.findViewById(R.id.text_word)
        private val textMeaning: TextView = itemView.findViewById(R.id.text_meaning)
        private val layoutIcons: LinearLayout = itemView.findViewById(R.id.layout_status_icons)
        private val layoutDetail: LinearLayout = itemView.findViewById(R.id.layout_detail_container)
        private val textDetail: TextView = itemView.findViewById(R.id.text_detail_content)
        private val buttonEdit: ImageButton = itemView.findViewById(R.id.button_edit)

        fun bind(item: WordHistoryItem, onEditClick: (WordHistoryItem) -> Unit, onClick: () -> Unit) {
            textWord.text = item.word
            textMeaning.text = item.meaning

            // アイコンの生成
            layoutIcons.removeAllViews()
            item.statuses.forEach { status ->
                val iconView = createStatusIcon(itemView, status)
                layoutIcons.addView(iconView)
            }

            // 展開・折りたたみ制御
            layoutDetail.visibility = if (item.isExpanded) View.VISIBLE else View.GONE
            buttonEdit.visibility = if (item.isExpanded) View.VISIBLE else View.GONE

            itemView.setOnClickListener { onClick() }
            buttonEdit.setOnClickListener { onEditClick(item) }

            // 詳細テキストの生成（表形式っぽく整形）
            if (item.isExpanded) {
                val sb = StringBuilder()
                val dateFormat = SimpleDateFormat("MM/dd", Locale.getDefault())
                item.statuses.forEach {
                    val dateStr = dateFormat.format(Date(it.nextReviewDate * 1000L)) // 秒 -> ミリ秒
                    sb.append("${it.modeName}: Lv.${it.level}  (Next: $dateStr)\n")
                }
                textDetail.text = sb.toString()
            }
        }

        private fun createStatusIcon(view: View, status: ModeStatus): View {
            // アイコンのコンテナ（バッジを表示するためにFrameLayoutを使用）
            val container = FrameLayout(view.context)
            val params = LinearLayout.LayoutParams(40, 40) // サイズ調整
            params.setMargins(8, 0, 8, 0)
            container.layoutParams = params

            // 丸いインジケーター
            val indicator = View(view.context)
            val indicatorParams = FrameLayout.LayoutParams(32, 32)
            indicatorParams.gravity = android.view.Gravity.CENTER
            indicator.layoutParams = indicatorParams
            
            // レベルに応じた色設定
            indicator.setBackgroundResource(R.drawable.bg_circle_indicator) 
            
            val color = when (status.level) {
                in 1..2 -> Color.parseColor("#9E9E9E") // Gray
                in 3..5 -> Color.parseColor("#2196F3") // Blue
                in 6..7 -> Color.parseColor("#4CAF50") // Green
                8 -> Color.parseColor("#FFC107")       // Gold
                else -> Color.LTGRAY
            }
            indicator.background.setTint(color)
            
            container.addView(indicator)

            // 復習アラート（赤丸バッジ）
            if (status.isReviewNeeded) {
                val badge = View(view.context)
                val badgeParams = FrameLayout.LayoutParams(12, 12)
                badgeParams.gravity = android.view.Gravity.TOP or android.view.Gravity.END
                badge.layoutParams = badgeParams
                badge.setBackgroundResource(R.drawable.bg_circle_indicator)
                badge.background.setTint(Color.RED)
                container.addView(badge)
            }

            return container
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<WordHistoryItem>() {
        override fun areItemsTheSame(oldItem: WordHistoryItem, newItem: WordHistoryItem) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: WordHistoryItem, newItem: WordHistoryItem) = oldItem == newItem
    }
}