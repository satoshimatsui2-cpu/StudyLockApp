package com.example.studylockapp.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.studylockapp.R
import com.example.studylockapp.data.AppSettings
import com.example.studylockapp.data.PointManager
import com.example.studylockapp.data.notification.StudyCharacter
import com.example.studylockapp.ui.alert.AppDialogHelper
import com.google.android.material.card.MaterialCardView

class CharacterSelectActivity : AppCompatActivity() {

    private lateinit var appSettings: AppSettings
    private lateinit var pointManager: PointManager
    private lateinit var recycler: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_character_select)

        appSettings = AppSettings(this)
        pointManager = PointManager(this)
        recycler = findViewById(R.id.recycler_characters)

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = CharacterAdapter()
    }

    inner class CharacterAdapter : RecyclerView.Adapter<CharacterAdapter.VH>() {
        private val list = StudyCharacter.values()
        private val totalGoals = appSettings.totalGoalsMetCount
        private val selectedId = appSettings.selectedCharacterId

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val card = v.findViewById<MaterialCardView>(R.id.card_character)
            val name = v.findViewById<TextView>(R.id.text_character_name)
            val desc = v.findViewById<TextView>(R.id.text_character_desc)
            val icon = v.findViewById<ImageView>(R.id.image_character_icon)
            val lock = v.findViewById<ImageView>(R.id.image_lock)
            val condition = v.findViewById<TextView>(R.id.text_unlock_condition)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_character_card, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val char = list[position]
            holder.name.text = char.displayName
            
            holder.desc.text = when(char) {
                StudyCharacter.GEORGE -> "親切で丁寧な応援スタイルです。"
                StudyCharacter.ATSUSHI -> "熱い言葉でやる気を引き出します。"
                StudyCharacter.SHIN -> "冷徹なまでに厳しい、最高のライバルです。"
                StudyCharacter.LUNA -> "素直じゃないけど、実は応援しています。"
                StudyCharacter.HARU -> "いつでも元気いっぱい！君を信じて応援します。"
                StudyCharacter.ARTHUR -> "優雅な言葉で君をエスコートします。"
                StudyCharacter.ROBOSUKE -> "論理的かつ厳しく、君の学習を管理します。"
                StudyCharacter.LEO -> "俺様全開！逆らうことは許されません。"
            }

            val isPurchased = appSettings.isCharacterUnlocked(char.id)
            val canPurchase = totalGoals >= char.unlockGoalDays
            val isSelected = char.id == selectedId

            when {
                isPurchased -> {
                    // 購入済み：自由に選択可能
                    holder.lock.visibility = View.GONE
                    holder.condition.visibility = View.GONE
                    holder.card.alpha = 1.0f
                    holder.card.setOnClickListener {
                        appSettings.selectedCharacterId = char.id
                        notifyDataSetChanged()
                    }
                }
                canPurchase -> {
                    // 購入可能：段階的にポイントが増える (400, 600, 800...)
                    val currentUnlockedCount = appSettings.unlockedCharacterIds.size - 1 // 初期キャラ(ジョージ)分を引く
                    val nextCost = 400 + (currentUnlockedCount * 200)
                    
                    holder.lock.visibility = View.VISIBLE
                    holder.lock.setImageResource(R.drawable.ic_monetization_on_24)
                    holder.lock.imageTintList = ContextCompat.getColorStateList(this@CharacterSelectActivity, R.color.brand_orange)
                    holder.condition.visibility = View.VISIBLE
                    holder.condition.text = "${nextCost}ポイントで解放"
                    holder.card.alpha = 1.0f
                    holder.card.setOnClickListener {
                        showUnlockDialog(char, nextCost)
                    }
                }
                else -> {
                    // 条件未達：ロック状態
                    holder.lock.visibility = View.VISIBLE
                    holder.lock.setImageResource(R.drawable.ic_lock_24dp)
                    holder.lock.imageTintList = ContextCompat.getColorStateList(this@CharacterSelectActivity, R.color.text_sub)
                    holder.condition.visibility = View.VISIBLE
                    holder.condition.text = "${char.unlockGoalDays}日達成で解放"
                    holder.card.alpha = 0.5f
                    holder.card.setOnClickListener(null)
                }
            }

            if (isSelected) {
                holder.card.strokeColor = ContextCompat.getColor(this@CharacterSelectActivity, R.color.brand_orange)
                holder.card.strokeWidth = 6
            } else {
                holder.card.strokeColor = ContextCompat.getColor(this@CharacterSelectActivity, R.color.app_outline)
                holder.card.strokeWidth = 2
            }
        }

        private fun showUnlockDialog(char: StudyCharacter, cost: Int) {
            val currentPoints = pointManager.getTotal()
            
            if (currentPoints < cost) {
                AppDialogHelper.showConfirm(
                    context = this@CharacterSelectActivity,
                    title = "ポイントが足りません",
                    message = "解放には${cost}pt必要です。（現在: ${currentPoints}pt）\n学習を進めてポイントを貯めましょう！",
                    positiveText = "OK",
                    negativeText = "",
                    onPositive = {}
                )
                return
            }

            AppDialogHelper.showConfirm(
                context = this@CharacterSelectActivity,
                title = "キャラクター解放",
                message = "${char.displayName}を${cost}ptで解放しますか？",
                positiveText = "解放する",
                negativeText = "キャンセル",
                onPositive = {
                    pointManager.add(-cost)
                    appSettings.unlockCharacter(char.id)
                    notifyDataSetChanged()
                    android.widget.Toast.makeText(this@CharacterSelectActivity, "${char.displayName}を解放しました！", android.widget.Toast.LENGTH_SHORT).show()
                }
            )
        }

        override fun getItemCount() = list.size
    }
}
