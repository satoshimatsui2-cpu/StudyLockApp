package com.example.studylockapp.ui

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.studylockapp.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton

class GradeBottomSheet(
    private val onGradeSelected: (String) -> Unit
) : BottomSheetDialogFragment() {

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
        return inflater.inflate(R.layout.dialog_grade_picker, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // IDと内部値(1-7)の対応マップ
        // 1:5級, 2:4級, 3:3級, 4:準2級, 5:2級, 6:準1級, 7:1級
        val gradeMap = mapOf(
            R.id.button_grade_5 to "1",
            R.id.button_grade_4 to "2",
            R.id.button_grade_3 to "3",
            R.id.button_grade_25 to "4",
            R.id.button_grade_2 to "5",
            R.id.button_grade_15 to "6",
            R.id.button_grade_1 to "7"
        )

        gradeMap.forEach { (id, gradeKey) ->
            view.findViewById<MaterialButton>(id)?.setOnClickListener {
                onGradeSelected(gradeKey)
                dismiss()
            }
        }
    }
}
