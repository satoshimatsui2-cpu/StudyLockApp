package com.example.studylockapp.ui

import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.studylockapp.R
import com.example.studylockapp.data.db.PracticalHistoryEntity
import com.example.studylockapp.databinding.BottomSheetPracticalHistoryDetailBinding
import com.example.studylockapp.learning.practical.PracticalListeningTtsController
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PracticalHistoryDetailBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetPracticalHistoryDetailBinding? = null
    private val binding get() = _binding!!

    private var historyItem: PracticalHistoryEntity? = null
    private var ttsController: PracticalListeningTtsController? = null

    companion object {
        fun newInstance(item: PracticalHistoryEntity): PracticalHistoryDetailBottomSheet {
            return PracticalHistoryDetailBottomSheet().apply {
                historyItem = item
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetPracticalHistoryDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val item = historyItem ?: return

        setupTts()
        displayDetails(item)
    }

    private fun setupTts() {
        ttsController = PracticalListeningTtsController(requireContext()).apply {
            onSegmentStart = { id -> highlightSegment(id) }
            onComplete = { clearHighlight() }
        }
    }

    private fun displayDetails(item: PracticalHistoryEntity) {
        val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
        val typeName = if (item.questionType == "LISTENING") "リスニング" else "穴埋め"

        binding.textDetailTitle.text = getString(R.string.practical_history_type_grade, typeName, item.grade)
        binding.textDetailDate.text = dateFormat.format(Date(item.answeredAt))
        binding.textDetailQuestionBody.text = item.questionText
        binding.textDetailYourAnswer.text = getString(R.string.practical_history_your_answer, item.selectedAnswer)
        binding.textDetailCorrectAnswer.text = getString(R.string.practical_history_correct_answer, item.correctAnswer)
        binding.textDetailExplanation.text = item.explanation

        // 状態バッジの設定
        val (statusText, colorRes) = when (item.resultStatus) {
            "CORRECT" -> getString(R.string.practical_history_status_correct) to R.color.choice_correct_text
            "WRONG" -> getString(R.string.practical_history_status_wrong) to R.color.choice_wrong_text
            else -> getString(R.string.practical_history_status_unscored) to R.color.text_sub
        }
        binding.textDetailResultBadge.text = statusText
        binding.textDetailResultBadge.setTextColor(ContextCompat.getColor(requireContext(), colorRes))
        binding.textDetailPoints.text = getString(R.string.practical_history_points_format, 
            if (item.points > 0) "+${item.points}" else "${item.points}")

        // リスニングセクションの制御
        if (item.questionType == "LISTENING" && !item.ttsScriptSnapshot.isNullOrBlank()) {
            binding.layoutDetailListening.visibility = View.VISIBLE
            renderScript(item.ttsScriptSnapshot)
            
            binding.buttonReplayDetail.setOnClickListener {
                ttsController?.play(item.ttsScriptSnapshot, item.grade)
            }
        } else {
            binding.layoutDetailListening.visibility = View.GONE
        }
    }

    private fun renderScript(script: String) {
        val container = binding.containerDetailScript
        container.removeAllViews()
        
        val segments = ttsController?.parseScript(script) ?: return
        segments.forEach { segment ->
            if (segment.displayText.isBlank()) return@forEach
            
            // 同一IDの複数セグメント（Question分割など）を1つにまとめる
            if (container.findViewWithTag<View>(segment.id) != null) return@forEach

            val textView = TextView(context).apply {
                tag = segment.id
                text = segment.displayText
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(ContextCompat.getColor(context, R.color.text_main))
                setPadding(0, 4, 0, 4)
            }
            container.addView(textView)
        }
    }

    private fun highlightSegment(id: Int) {
        val container = binding.containerDetailScript
        for (i in 0 until container.childCount) {
            val view = container.getChildAt(i) as? TextView ?: continue
            if (view.tag == id) {
                view.setTypeface(null, Typeface.BOLD)
                view.setTextColor(ContextCompat.getColor(requireContext(), R.color.navy_primary))
                view.setBackgroundResource(R.drawable.bg_badge_navy_soft)
            } else {
                view.setTypeface(null, Typeface.NORMAL)
                view.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_main))
                view.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        }
    }

    private fun clearHighlight() {
        highlightSegment(-1)
    }

    override fun onDestroyView() {
        ttsController?.shutdown()
        super.onDestroyView()
        _binding = null
    }
}
