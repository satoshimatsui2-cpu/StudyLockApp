package com.stulab.studylockapp.ui

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.stulab.studylockapp.GradeUtils
import com.stulab.studylockapp.R
import com.stulab.studylockapp.data.AppDatabase
import com.stulab.studylockapp.data.AppSettings
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GradeSelectionDialogFragment : DialogFragment() {

    private var onGradeSelected: ((String) -> Unit)? = null
    private lateinit var settings: AppSettings

    companion object {
        fun newInstance(onGradeSelected: (String) -> Unit): GradeSelectionDialogFragment {
            return GradeSelectionDialogFragment().apply {
                this.onGradeSelected = onGradeSelected
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        settings = AppSettings(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_grade_picker, null)
        
        loadGradeStats(view)

        return MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .create()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setDimAmount(0.5f)
            val metrics = resources.displayMetrics
            // 横幅：利用可能な画面幅の約98%（マージンを従来の1/3に縮小）
            val width = (metrics.widthPixels * 0.98).toInt()
            // 縦幅：利用可能な画面高の約88〜90% (1.2倍相当を目指す)
            val height = (metrics.heightPixels * 0.89).toInt()
            
            // タブレット等で広がりすぎないよう、最大幅（例えば600dp）を設ける
            val maxWidth = (600 * metrics.density).toInt()
            setLayout(minOf(width, maxWidth), height)
        }
    }

    private fun loadGradeStats(rootView: View) {
        val container = rootView.findViewById<LinearLayout>(R.id.container_grades)
        val db = AppDatabase.getInstance(requireContext())
        
        // 1:5級, 2:4級, 3:3級, 4:準2級, 5:2級, 6:準1級, 7:1級
        val builtinGradeKeys = listOf("1", "2", "3", "4", "5", "6", "7")

        lifecycleScope.launch {
            val statsList = withContext(Dispatchers.IO) {
                val list = mutableListOf<GradeStats>()
                
                // 1. 組み込み級
                builtinGradeKeys.forEach { key ->
                    val gradeInt = key.toInt()
                    val total = db.wordDao().countTotalWordsByGrade(gradeInt)
                    val basic = db.wordMasteryDao().countBasicMasteredByGrade(gradeInt)
                    val longterm = db.wordMasteryDao().countLongTermMasteredByGrade(gradeInt)
                    
                    list.add(GradeStats(
                        key = key,
                        name = GradeUtils.toDisplay(key, settings),
                        totalCount = total,
                        basicCount = basic,
                        basicPct = if (total > 0) (basic * 100 / total) else 0,
                        longtermCount = longterm,
                        longtermPct = if (total > 0) (longterm * 100 / total) else 0
                    ))
                }

                // 2. マイ単語帳 (登録済みのみ)
                val personalCounts = db.wordDao().getMyWordBookCountsFlow().first()
                personalCounts.sortedBy { it.grade }.forEach { item ->
                    if (item.total > 0) {
                        val key = item.grade.toString()
                        val basic = db.wordMasteryDao().countBasicMasteredByGrade(item.grade)
                        val longterm = db.wordMasteryDao().countLongTermMasteredByGrade(item.grade)

                        list.add(GradeStats(
                            key = key,
                            name = GradeUtils.toDisplay(key, settings),
                            totalCount = item.total,
                            basicCount = basic,
                            basicPct = if (item.total > 0) (basic * 100 / item.total) else 0,
                            longtermCount = longterm,
                            longtermPct = if (item.total > 0) (longterm * 100 / item.total) else 0
                        ))
                    }
                }
                list
            }

            container.removeAllViews()
            val currentSelectedGrade = settings.currentLearningGrade

            statsList.forEach { stats ->
                val itemView = layoutInflater.inflate(R.layout.item_grade_list, container, false)
                val card = itemView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.card_grade_item)
                val gradeNameText = itemView.findViewById<TextView>(R.id.text_grade_name)
                
                itemView.findViewById<TextView>(R.id.text_count_total).text = "${stats.totalCount}語"
                itemView.findViewById<TextView>(R.id.text_count_basic).text = "${stats.basicCount} (${stats.basicPct}%)"
                itemView.findViewById<TextView>(R.id.text_count_longterm).text = "${stats.longtermCount} (${stats.longtermPct}%)"

                gradeNameText.text = stats.name

                // 現在選択中の級をハイライト (元のデザインを復元)
                if (stats.key == currentSelectedGrade) {
                    card.strokeColor = ContextCompat.getColor(requireContext(), R.color.navy_primary)
                    card.strokeWidth = (3 * resources.displayMetrics.density).toInt()
                    card.setCardBackgroundColor(Color.WHITE)
                    gradeNameText.setTextColor(ContextCompat.getColor(requireContext(), R.color.navy_primary))
                    itemView.findViewById<View>(R.id.image_selected_check).visibility = View.VISIBLE
                } else {
                    card.strokeColor = Color.TRANSPARENT
                    card.strokeWidth = 0
                    card.setCardBackgroundColor(Color.parseColor("#EEEEEE")) 
                    gradeNameText.setTextColor(Color.parseColor("#9E9E9E"))
                    itemView.findViewById<View>(R.id.image_selected_check).visibility = View.GONE
                }

                itemView.setOnClickListener {
                    onGradeSelected?.invoke(stats.key)
                    dismiss()
                }
                container.addView(itemView)
            }
        }
    }

    private data class GradeStats(
        val key: String,
        val name: String,
        val totalCount: Int,
        val basicCount: Int,
        val basicPct: Int,
        val longtermCount: Int,
        val longtermPct: Int
    )
}
