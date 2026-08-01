package com.stulab.studylockapp

import org.junit.Assert.assertEquals
import org.junit.Test

class GradeNameTest {

    @Test
    fun testGradeUtils_toDisplay_Fallback() {
        assertEquals("マイ単語帳 1", GradeUtils.toDisplay("90", null))
        assertEquals("5級", GradeUtils.toDisplay("1", null))
        assertEquals("不明", GradeUtils.toDisplay("999", null))
    }

    @Test
    fun testGradeLabelFormatter_format_Fallback() {
        assertEquals("マイ単語帳 2", GradeLabelFormatter.format(91, null))
        assertEquals("3級", GradeLabelFormatter.format(3, null))
        assertEquals("未設定", GradeLabelFormatter.format(0, null))
    }
}
