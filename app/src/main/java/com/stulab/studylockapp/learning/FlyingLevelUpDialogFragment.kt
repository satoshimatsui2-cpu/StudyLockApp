package com.stulab.studylockapp.learning

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.stulab.studylockapp.R
import com.stulab.studylockapp.databinding.DialogFlyingLevelupBinding
import kotlinx.coroutines.launch

/**
 * 飛び級（LV6→8, LV8→10）が発生した際に表示する案内モーダル。
 * 自動更新された結果を通知するもので、選択肢はありません。
 */
class FlyingLevelUpDialogFragment : DialogFragment() {

    private var _binding: DialogFlyingLevelupBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LearningViewModel by lazy {
        ViewModelProvider(requireActivity()).get(LearningViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 背景タップや戻るボタンによるキャンセルを無効化
        isCancelable = false
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        // CardView の角丸と影を正しく表示するため、ウィンドウ背景を透明にする
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogFlyingLevelupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val model = state.flyingLevelUp
                    if (model != null) {
                        bind(model)
                    } else {
                        // 万が一データが消失した場合は閉じる
                        dismissAllowingStateLoss()
                    }
                }
            }
        }

        binding.buttonFlyingNext.setOnClickListener {
            // モーダルを閉じ、次の状態（レビュー表示可能）へ移行
            viewModel.onFlyingLevelUpDismissed()
            dismiss()
        }
        
        // 表示開始を通知
        viewModel.onFlyingLevelUpShown()
    }

    private fun bind(model: FlyingLevelUpUiModel) {
        binding.textLevelFrom.text = getString(R.string.label_level_n, model.fromLevel)
        binding.textLevelTo.text = getString(R.string.label_level_n, model.toLevel)
        binding.textFlyingMessage.text = getString(
            R.string.flying_levelup_message,
            model.skippedLevel,
            model.toLevel
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "FlyingLevelUpDialogFragment"
    }
}
