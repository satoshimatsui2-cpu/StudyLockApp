package com.stulab.studylockapp.learning

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import com.stulab.studylockapp.R
import com.stulab.studylockapp.databinding.ViewMasteryProgressBinding

class MasteryProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    // <merge> タグを使用したレイアウトのインフレート。
    // 引数は (inflater, this) の2つになる。
    private val binding: ViewMasteryProgressBinding =
        ViewMasteryProgressBinding.inflate(LayoutInflater.from(context), this)

    private val nodes: List<View> by lazy {
        listOf(binding.node1, binding.node2, binding.node3, binding.node4, binding.nodeGoal)
    }
    private val lines: List<View> by lazy {
        listOf(binding.line1, binding.line2, binding.line3, binding.line4)
    }

    private var isLongTermMode: Boolean = false

    fun setProgress(level: Int, animate: Boolean = true) {
        val newIsLongTerm = level > 5
        val relativeLevel = if (newIsLongTerm) (level - 5).coerceAtLeast(1) else level.coerceAtLeast(1)
        
        if (isLongTermMode != newIsLongTerm) {
            isLongTermMode = newIsLongTerm
            updateGoalIcon()
        }

        updateUi(relativeLevel, level, animate)
    }

    private fun updateGoalIcon() {
        val icon = if (isLongTermMode) R.drawable.ic_mastery_diamond_24 else R.drawable.ic_round_stars_24
        binding.nodeGoal.setImageResource(icon)
    }

    private fun updateUi(relativeLevel: Int, absoluteLevel: Int, animate: Boolean) {
        val activeColor = if (isLongTermMode) R.color.navy_primary else R.color.mustard_accent
        val inactiveColor = R.color.app_outline
        
        binding.textLevelChip.text = "LV$absoluteLevel"
        binding.cardLevelIndicator.setCardBackgroundColor(ContextCompat.getColor(context, activeColor))

        nodes.forEachIndexed { i, node ->
            val isActive = i < relativeLevel
            val color = if (isActive) activeColor else inactiveColor
            setNodeColor(node, color)
        }
        lines.forEachIndexed { i, line ->
            val isActive = i < relativeLevel - 1
            line.setBackgroundColor(ContextCompat.getColor(context, if (isActive) activeColor else inactiveColor))
        }

        if (relativeLevel in 1..5) {
            moveLevelChip(relativeLevel - 1, animate)
        }
    }

    private fun setNodeColor(node: View, colorRes: Int) {
        val color = ContextCompat.getColor(context, colorRes)
        if (node is ImageView) {
            node.imageTintList = android.content.res.ColorStateList.valueOf(color)
        } else {
            node.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
        }
    }

    private fun moveLevelChip(nodeIndex: Int, animate: Boolean) {
        val targetNode = nodes[nodeIndex]
        binding.layoutMasteryRoot.post {
            val constraintSet = ConstraintSet()
            constraintSet.clone(binding.layoutMasteryRoot)
            
            constraintSet.connect(R.id.card_level_indicator, ConstraintSet.START, targetNode.id, ConstraintSet.START)
            constraintSet.connect(R.id.card_level_indicator, ConstraintSet.END, targetNode.id, ConstraintSet.END)
            
            if (animate) {
                android.transition.TransitionManager.beginDelayedTransition(binding.layoutMasteryRoot)
            }
            constraintSet.applyTo(binding.layoutMasteryRoot)
        }
    }
}
