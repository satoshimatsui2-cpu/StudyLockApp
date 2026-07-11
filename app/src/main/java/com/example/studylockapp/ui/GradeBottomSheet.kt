package com.example.studylockapp.ui

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.studylockapp.GradeUtils
import com.example.studylockapp.R
import com.example.studylockapp.data.AppDatabase
import com.example.studylockapp.data.AppSettings
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class GradeBottomSheet(
    private val onGradeSelected: (String) -> Unit
) : BottomSheetDialogFragment() {

    private lateinit var settings: AppSettings

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as BottomSheetDialog
            bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                ?.setBackgroundColor(Color.TRANSPARENT)
        }
        return dialog
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setDimAmount(0.5f)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        settings = AppSettings(requireContext())
        return inflater.inflate(R.layout.dialog_grade_picker, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadGradeStats(view)
    }

    private fun loadGradeStats(rootView: View) {
        val container = rootView.findViewById<LinearLayout>(R.id.container_grades)
        val db = AppDatabase.getInstance(requireContext())
        
        // 1:5級, 2:4級, 3:3級, 4:準2級, 5:2級, 6:準1級, 7:1級
        val gradeKeys = listOf("1", "2", "3", "4", "5", "6", "7")

        lifecycleScope.launch {
            val statsList = withContext(Dispatchers.IO) {
                gradeKeys.map { key ->
                    val gradeInt = key.toInt()
                    val total = db.wordDao().countTotalWordsByGrade(gradeInt)
                    val basic = db.wordMasteryDao().countBasicMasteredByGrade(gradeInt)
                    val longterm = db.wordMasteryDao().countLongTermMasteredByGrade(gradeInt)
                    
                    GradeStats(
                        key = key,
                        name = GradeUtils.toDisplay(key),
                        totalCount = total,
                        basicCount = basic,
                        basicPct = if (total > 0) (basic * 100 / total) else 0,
                        longtermCount = longterm,
                        longtermPct = if (total > 0) (longterm * 100 / total) else 0
                    )
                }
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

                // 現在選択中の級をハイライト
                if (stats.key == currentSelectedGrade) {
                    // 選択中：太い紺色の枠線、背景は白、チェックマークは非表示
                    card.strokeColor = ContextCompat.getColor(requireContext(), R.color.navy_primary)
                    card.strokeWidth = (3 * resources.displayMetrics.density).toInt()
                    card.setCardBackgroundColor(Color.WHITE)
                    gradeNameText.setTextColor(ContextCompat.getColor(requireContext(), R.color.navy_primary))
                    itemView.findViewById<View>(R.id.image_selected_check).visibility = View.GONE
                } else {
                    // 未選択：バックのグレーを少し濃く(#EEEEEE)、文字はさらに薄いグレー(#9E9E9E)
                    card.strokeColor = Color.TRANSPARENT
                    card.strokeWidth = 0
                    card.setCardBackgroundColor(Color.parseColor("#EEEEEE")) 
                    gradeNameText.setTextColor(Color.parseColor("#9E9E9E"))
                    itemView.findViewById<View>(R.id.image_selected_check).visibility = View.GONE
                }

                itemView.setOnClickListener {
                    onGradeSelected(stats.key)
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
