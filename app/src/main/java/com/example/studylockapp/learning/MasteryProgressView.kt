package com.example.studylockapp.learning

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.BounceInterpolator
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.example.studylockapp.R
import com.example.studylockapp.databinding.ViewMasteryProgressBinding

/**
 * 習得レベルを視覚化する進捗レール。
 * LV1-5（基礎）と LV6-10（長期）の2モードを切り替えて表示。
 */
class MasteryProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding: ViewMasteryProgressBinding =
        ViewMasteryProgressBinding.inflate(LayoutInflater.from(context), this)

    private val nodes: List<View> by lazy {
        listOf(binding.node1, binding.node2, binding.node3, binding.node4, binding.nodeGoal)
    }
    private val lines: List<View> by lazy {
        listOf(binding.line1, binding.line2, binding.line3, binding.line4)
    }

    private var currentDisplayLevel: Int = 0
    private var isLongTermMode: Boolean = false

    init {
        orientation = HORIZONTAL
    }

    /**
     * 進捗を更新する。
     * @param level 1-10 の絶対レベル
     * @param animate レベルアップ時のアニメーション有無
     */
    fun setProgress(level: Int, animate: Boolean = true) {
        val newIsLongTerm = level > 5
        val relativeLevel = if (newIsLongTerm) (level - 5).coerceAtLeast(1) else level.coerceAtLeast(1)
        
        // モード変更時のリセット
        if (isLongTermMode != newIsLongTerm) {
            isLongTermMode = newIsLongTerm
            resetUi()
        }

        if (animate && relativeLevel > currentDisplayLevel && currentDisplayLevel > 0) {
            animateToLevel(relativeLevel)
        } else {
            applyLevelImmediate(relativeLevel)
        }
        
        currentDisplayLevel = relativeLevel
    }

    private fun resetUi() {
        val goalIcon = if (isLongTermMode) R.drawable.ic_round_stars_24 else R.drawable.ic_round_stars_24 
        binding.nodeGoal.setImageResource(goalIcon)
        
        nodes.forEach { node ->
            if (node is android.widget.ImageView) {
                node.imageTintList = ContextCompat.getColorStateList(context, R.color.md_outline)
            } else {
                node.backgroundTintList = ContextCompat.getColorStateList(context, R.color.md_outline)
            }
        }
        lines.forEach { it.setBackgroundColor(ContextCompat.getColor(context, R.color.md_outline)) }
    }

    private fun applyLevelImmediate(relativeLevel: Int) {
        val activeColor = if (isLongTermMode) R.color.mastery_text_longterm else R.color.md_primary
        
        for (i in 0 until 5) {
            if (i < relativeLevel) {
                setNodeActive(nodes[i], activeColor)
                if (i > 0) {
                    lines[i - 1].setBackgroundColor(ContextCompat.getColor(context, activeColor))
                }
            }
        }
    }

    private fun animateToLevel(targetRelative: Int) {
        val activeColor = if (isLongTermMode) R.color.mastery_text_longterm else R.color.md_primary
        
        // 直前の線のアニメーション
        if (targetRelative > 1) {
            animateLine(lines[targetRelative - 2], activeColor)
        }
        
        // 到達ノードのバウンド
        val targetNode = nodes[targetRelative - 1]
        targetNode.postDelayed({
            setNodeActive(targetNode, activeColor)
            bounceNode(targetNode)
        }, 200)
    }

    private fun setNodeActive(node: View, colorRes: Int) {
        val color = ContextCompat.getColor(context, colorRes)
        if (node is android.widget.ImageView) {
            node.imageTintList = android.content.res.ColorStateList.valueOf(color)
        } else {
            node.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
        }
    }

    private fun animateLine(line: View, colorRes: Int) {
        val colorFrom = ContextCompat.getColor(context, R.color.md_outline)
        val colorTo = ContextCompat.getColor(context, colorRes)
        
        ValueAnimator.ofObject(ArgbEvaluator(), colorFrom, colorTo).apply {
            duration = 400
            addUpdateListener { animator ->
                line.setBackgroundColor(animator.animatedValue as Int)
            }
            start()
        }
    }

    private fun bounceNode(node: View) {
        node.animate()
            .scaleX(1.5f)
            .scaleY(1.5f)
            .setDuration(200)
            .setInterpolator(BounceInterpolator())
            .withEndAction {
                node.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
            }
            .start()
    }
}
