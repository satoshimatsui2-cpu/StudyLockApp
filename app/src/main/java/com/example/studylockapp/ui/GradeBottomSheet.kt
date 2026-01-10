package com.example.studylockapp.ui

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import com.example.studylockapp.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton

class GradeBottomSheet(
    private val onGradeSelected: (String) -> Unit
) : BottomSheetDialogFragment() {

    // ▼▼▼ モダン化のための重要な設定 ▼▼▼
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as BottomSheetDialog
            // BottomSheetのコンテナ自体の背景を透明にする。
            // これをしないと、CardViewの角丸の外側に白い四角い背景が見えてしまう。
            bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                ?.setBackgroundColor(Color.TRANSPARENT)
        }
        return dialog
    }

    override fun onStart() {
        super.onStart()
        // 背景の暗さ（Dim）を調整する。
        // 0.0f(透明) 〜 1.0f(真っ黒)。標準は0.6くらい。0.3くらいがモダンで軽やか。
        dialog?.window?.setDimAmount(0.5f)
    }
    // ▲▲▲ 設定ここまで ▲▲▲


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_grade_picker, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 以前のハックは不要になったので削除しました

        val gradeMap = mapOf(
            R.id.button_grade_5 to "5",
            R.id.button_grade_4 to "4",
            R.id.button_grade_3 to "3",
            R.id.button_grade_25 to "2.5",
            R.id.button_grade_2 to "2",
            R.id.button_grade_15 to "1.5",
            R.id.button_grade_1 to "1"
        )

        gradeMap.forEach { (id, gradeKey) ->
            view.findViewById<MaterialButton>(id)?.setOnClickListener {
                onGradeSelected(gradeKey)
                dismiss()
            }
        }
    }
}
