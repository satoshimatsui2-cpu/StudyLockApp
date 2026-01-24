package com.example.studylockapp

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.style.ReplacementSpan

/**
 * 学習画面で使う便利ツールやヘルパークラス
 */

// 級の表記をDB用に変換するマップ
private val gradeUiToDbMap = mapOf(
    "1級" to "1", "準1級" to "1.5",
    "2級" to "2", "準2級" to "2.5",
    "3級" to "3", "4級" to "4", "5級" to "5"
)

// UIの表記（"準2級"など）をDBの値（"2.5"など）に変換する関数
fun normalizeGrade(gradeUi: String): String {
    return gradeUiToDbMap[gradeUi] ?: gradeUi
}

/**
 * テキストにマーカー（ハイライト）を引くためのSpanクラス
 */
class MarkerSpan(private val color: Int) : ReplacementSpan() {
    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        if (text == null) return 0
        return paint.measureText(text, start, end).toInt()
    }

    override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        if (text != null) {
            val originalColor = paint.color
            val width = paint.measureText(text, start, end)

            paint.color = color

            val paddingY = (bottom - top) * 0.15f
            val rect = RectF(x, top + paddingY, x + width, bottom - paddingY)

            canvas.drawRoundRect(rect, 12f, 12f, paint)

            paint.color = originalColor
            canvas.drawText(text, start, end, x, y.toFloat(), paint)
        }
    }
}