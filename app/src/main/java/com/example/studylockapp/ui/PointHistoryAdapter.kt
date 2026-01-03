package com.example.studylockapp.ui

import android.content.pm.PackageManager
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.studylockapp.R
import com.example.studylockapp.data.UnlockHistoryEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class PointHistoryAdapter(
    private val onCancelClick: (UnlockHistoryEntity) -> Unit
) : ListAdapter<UnlockHistoryEntity, PointHistoryAdapter.HistoryViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_point_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, onCancelClick)
    }

    class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgIcon: ImageView = itemView.findViewById(R.id.img_app_icon)
        private val textAppName: TextView = itemView.findViewById(R.id.text_app_name)
        private val textUsedPoints: TextView = itemView.findViewById(R.id.text_used_points)
        private val textRemaining: TextView = itemView.findViewById(R.id.text_remaining_time)
        private val textUnlockedAt: TextView = itemView.findViewById(R.id.text_unlocked_at) // 追加
        private val btnCancel: Button = itemView.findViewById(R.id.btn_cancel)

        // 日付フォーマッターの定義
        private val dateFormat = SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault())

        fun bind(item: UnlockHistoryEntity, onCancelClick: (UnlockHistoryEntity) -> Unit) {
            val pm = itemView.context.packageManager

            // 1. アプリ情報の取得 (アイコンと名前)
            try {
                val appInfo = pm.getApplicationInfo(item.packageName, 0)
                imgIcon.setImageDrawable(pm.getApplicationIcon(appInfo))
                textAppName.text = pm.getApplicationLabel(appInfo)
            } catch (e: PackageManager.NameNotFoundException) {
                imgIcon.setImageResource(android.R.drawable.sym_def_app_icon)
                textAppName.text = item.packageName
            }

            // 2. ポイント表示
            textUsedPoints.text = "消費: ${item.usedPoints} pt"

            // 3. 開放日時の表示 (追加)
            // item.unlockedAt はエポック秒なのでミリ秒に変換してDateオブジェクトを作成
            val date = Date(item.unlockedAt * 1000)
            textUnlockedAt.text = dateFormat.format(date)

            // 4. 残り時間の計算
            val currentTimeSec = System.currentTimeMillis() / 1000
            val endTimeSec = item.unlockedAt + item.unlockDurationSec
            val remainingSec = endTimeSec - currentTimeSec

            when {
                item.cancelled -> {
                    textRemaining.text = "キャンセル済み"
                    textRemaining.setTextColor(Color.DKGRAY)
                    btnCancel.visibility = View.GONE
                }
                remainingSec > 0 -> {
                    val remainingMin = TimeUnit.SECONDS.toMinutes(remainingSec) + 1
                    textRemaining.text = "残り ${remainingMin}分"
                    textRemaining.setTextColor(Color.parseColor("#0D47A1"))
                    btnCancel.visibility = View.VISIBLE
                    btnCancel.isEnabled = true // 再利用のために毎回有効化
                    btnCancel.setOnClickListener { 
                        it.isEnabled = false // 即座にボタンを無効化
                        onCancelClick(item) 
                    }
                }
                else -> {
                    textRemaining.text = "完了"
                    textRemaining.setTextColor(Color.GRAY)
                    btnCancel.visibility = View.GONE
                }
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<UnlockHistoryEntity>() {
        override fun areItemsTheSame(oldItem: UnlockHistoryEntity, newItem: UnlockHistoryEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: UnlockHistoryEntity, newItem: UnlockHistoryEntity): Boolean {
            // `cancelled` フラグの変更を検知するために、オブジェクト全体を比較する
            return oldItem == newItem
        }
    }
}