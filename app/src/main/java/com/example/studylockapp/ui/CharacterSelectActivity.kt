package com.example.studylockapp.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.studylockapp.R
import com.example.studylockapp.data.AppSettings
import com.example.studylockapp.data.notification.StudyCharacter
import com.google.android.material.card.MaterialCardView

class CharacterSelectActivity : AppCompatActivity() {

    private lateinit var appSettings: AppSettings
    private lateinit var recycler: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_character_select)

        appSettings = AppSettings(this)
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
                StudyCharacter.LUNA -> "素直じゃないけど、実は応援しています。"
                StudyCharacter.ARTHUR -> "優雅な言葉で君をエスコートします。"
            }

            val isUnlocked = totalGoals >= char.unlockGoalDays
            val isSelected = char.id == selectedId

            if (isUnlocked) {
                holder.lock.visibility = View.GONE
                holder.condition.visibility = View.GONE
                holder.card.alpha = 1.0f
                holder.card.setOnClickListener {
                    appSettings.selectedCharacterId = char.id
                    notifyDataSetChanged()
                }
            } else {
                holder.lock.visibility = View.VISIBLE
                holder.condition.visibility = View.VISIBLE
                holder.condition.text = "${char.unlockGoalDays}日達成で解放"
                holder.card.alpha = 0.5f
                holder.card.setOnClickListener(null)
            }

            if (isSelected) {
                holder.card.strokeColor = ContextCompat.getColor(this@CharacterSelectActivity, R.color.brand_orange)
                holder.card.strokeWidth = 6
            } else {
                holder.card.strokeColor = ContextCompat.getColor(this@CharacterSelectActivity, R.color.app_outline)
                holder.card.strokeWidth = 2
            }
        }

        override fun getItemCount() = list.size
    }
}
