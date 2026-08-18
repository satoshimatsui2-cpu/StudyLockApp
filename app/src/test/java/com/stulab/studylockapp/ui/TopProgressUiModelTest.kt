package com.stulab.studylockapp.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TopProgressUiModelTest {

    @Test
    fun `ProgressMetricUiModel percentage calculation is correct`() {
        val metric = ProgressMetricUiModel("Test", 0, 50, 100)
        assertEquals(50, metric.percentage)
        assertEquals("50（50%）", metric.displayText)
    }

    @Test
    fun `ProgressMetricUiModel handles zero denominator`() {
        val metric = ProgressMetricUiModel("Test", 0, 0, 0)
        assertEquals(0, metric.percentage)
        assertEquals("0（0%）", metric.displayText)
    }

    @Test
    fun `ProgressMetricUiModel percentage is capped at 100`() {
        val metric = ProgressMetricUiModel("Test", 0, 120, 100)
        assertEquals(100, metric.percentage)
        assertEquals("120（100%）", metric.displayText)
    }
}
