package com.example.studylockapp.ui

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.PorterDuff
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

        recycler.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, 2)
        recycler.adapter = CharacterAdapter()
    }

    inner class CharacterAdapter : RecyclerView.Adapter<CharacterAdapter.VH>() {
        private val list = StudyCharacter.values()
        
        // 最新の状態をバインド時に取得するため、ここではプロパティとして保持しない

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val card = v.findViewById<MaterialCardView>(R.id.card_character)
            val name = v.findViewById<TextView>(R.id.text_character_name)
            val desc = v.findViewById<TextView>(R.id.text_character_desc)
            val portrait = v.findViewById<ImageView>(R.id.image_character_portrait)
            val lock = v.findViewById<ImageView>(R.id.image_lock)
            val condition = v.findViewById<TextView>(R.id.text_unlock_condition)
            val selectionBorder = v.findViewById<View>(R.id.view_selection_border)
            val selectedCheck = v.findViewById<View>(R.id.image_selected_check)
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
                StudyCharacter.HINA -> "控えめですが、心からあなたを支えます。"
                StudyCharacter.ELENA -> "知的な微笑みで、あなたの深奥を導きます。"
            }

            // 画像のセット
            val resId = getCharacterDrawableId(char.id)
            holder.portrait.setImageResource(resId)

            val isPurchased = appSettings.isCharacterUnlocked(char.id)
            val canPurchase = appSettings.totalGoalsMetCount >= char.unlockGoalDays
            val isSelected = char.id == appSettings.selectedCharacterId

            when {
                isPurchased -> {
                    // 購入済み：カラー表示
                    holder.portrait.clearColorFilter()
                    holder.lock.visibility = View.GONE
                    holder.condition.visibility = View.GONE
                    holder.card.alpha = 1.0f
                    holder.card.setOnClickListener {
                        appSettings.selectedCharacterId = char.id
                        notifyDataSetChanged()
                    }
                }
                canPurchase -> {
                    // 購入可能：シルエット表示
                    holder.portrait.setColorFilter(android.graphics.Color.BLACK, PorterDuff.Mode.SRC_IN)
                    
                    val currentUnlockedCount = appSettings.unlockedCharacterIds.size - 1
                    val nextCost = 400 + (currentUnlockedCount * 200)
                    
                    holder.lock.visibility = View.VISIBLE
                    holder.lock.setImageResource(R.drawable.ic_monetization_on_24)
                    holder.lock.imageTintList = ContextCompat.getColorStateList(this@CharacterSelectActivity, R.color.brand_orange)
                    holder.condition.visibility = View.VISIBLE
                    holder.condition.text = "${nextCost}ptで解放"
                    holder.condition.setBackgroundResource(R.drawable.bg_badge_orange) // 解放可能時はブランド色
                    holder.card.alpha = 1.0f
                    holder.card.setOnClickListener {
                        showUnlockDialog(char, nextCost)
                    }
                }
                else -> {
                    // 条件未達：シルエット表示
                    holder.portrait.setColorFilter(android.graphics.Color.BLACK, PorterDuff.Mode.SRC_IN)
                    
                    val daysLeft = char.unlockGoalDays - appSettings.totalGoalsMetCount
                    
                    holder.lock.visibility = View.VISIBLE
                    holder.lock.setImageResource(R.drawable.ic_lock_24dp)
                    holder.lock.imageTintList = ContextCompat.getColorStateList(this@CharacterSelectActivity, R.color.white)
                    holder.condition.visibility = View.VISIBLE
                    holder.condition.text = "あと${daysLeft}日で解放"
                    holder.condition.setBackgroundResource(R.drawable.bg_badge_condition_locked) // 条件未達時は視認性の高いダーク背景
                    holder.card.alpha = 0.8f
                    holder.card.setOnClickListener(null)
                }
            }

            // 選択状態のネオン枠とチェックアイコンの表示
            holder.selectionBorder.visibility = if (isSelected) View.VISIBLE else View.GONE
            holder.selectedCheck.visibility = if (isSelected) View.VISIBLE else View.GONE
        }

        private fun getCharacterDrawableId(charId: String): Int {
            val resName = "char_$charId"
            val id = resources.getIdentifier(resName, "drawable", packageName)
            return if (id != 0) id else R.drawable.ic_round_stars_24 // 見つからない場合は星アイコン
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
