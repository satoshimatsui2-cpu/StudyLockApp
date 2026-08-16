package com.stulab.studylockapp.learning

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.*
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.stulab.studylockapp.R
import com.stulab.studylockapp.databinding.FragmentReviewModalBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 回答後レビューをモーダル形式で表示するFragment。
 */
class ReviewModalFragment : DialogFragment() {

    private var _binding: FragmentReviewModalBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LearningViewModel by lazy {
        ViewModelProvider(requireActivity()).get(LearningViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ダイアログのスタイル設定（全画面、背景暗転）
        setStyle(STYLE_NORMAL, R.style.App_ReviewModalStyle)
        isCancelable = false // 背景タップで閉じない
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReviewModalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // システムバーの Insets を取得してモーダルの位置とサイズを調整
        ViewCompat.setOnApplyWindowInsetsListener(binding.modalRootContainer) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            
            val left = Math.max(bars.left, cutout.left)
            val top = Math.max(bars.top, cutout.top)
            val right = Math.max(bars.right, cutout.right)
            val bottom = Math.max(bars.bottom, cutout.bottom)

            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels

            // 利用可能な純粋なコンテンツ領域（システムバー除外）
            val availableWidth = screenWidth - left - right
            val availableHeight = screenHeight - top - bottom

            // 目標サイズ (利用可能領域の 95%)
            val targetWidth = (availableWidth * 0.95f).toInt()
            val targetHeight = (availableHeight * 0.95f).toInt()

            // マージンの計算 (上部はステータスバー + 16dp)
            val marginHorizontal = (availableWidth - targetWidth) / 2
            val marginTop = top + dpToPx(16)
            
            val params = binding.rootReviewCardContainer.layoutParams as FrameLayout.LayoutParams
            params.width = targetWidth
            params.height = targetHeight - dpToPx(16) // 上を下げた分だけ高さを引く
            params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            params.topMargin = marginTop
            params.leftMargin = left + marginHorizontal
            params.rightMargin = right + marginHorizontal
            binding.rootReviewCardContainer.layoutParams = params

            insets
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { state ->
                    if (state.currentWord != null) {
                        ReviewCardBinder.bind(
                            binding.layoutReviewContent,
                            ReviewCardMapper.map(state),
                            onPlayUserAnswer = { text -> viewModel.requestAudioPlayback(text) },
                            onPlayCorrectAnswer = { text -> viewModel.requestAudioPlayback(text) },
                            onPlaySentence = { text -> viewModel.requestAudioPlayback(text) },
                            onFavoriteClick = {
                                state.currentWord.no.let { wordId ->
                                    viewModel.toggleFavorite(wordId)
                                }
                            }
                        )
                    }
                }
            }
        }

        binding.layoutReviewContent.buttonNextQuestion.setOnClickListener {
            // モーダルを閉じ、次の問題へ
            viewModel.onNextAfterReview()
            dismissAllowingStateLoss()
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            // アニメーションを完全に無効化
            setWindowAnimations(0)
            
            // Window自体は全画面透明にする（内側の CardView でサイズと枠を制御）
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            
            // 明示的に DIM を設定
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply {
                dimAmount = 0.5f
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "ReviewModalFragment"
    }
}
