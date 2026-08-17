package com.stulab.studylockapp.learning

import com.stulab.studylockapp.data.db.WordMasteryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class MasterySchedulerTest {

    private val timingSettings = ReviewTimingSettings(
        wrongSameDayDelayMillis = 60000,
        correctSameDayDelayMillis = 60000,
        unknownSameDayDelayMillis = 60000
    )

    @Test
    fun testFlyingLevelUp_LV6_to_LV8() {
        val mastery = WordMasteryEntity(
            wordId = 1,
            level = 6,
            currentStreak = 2, // 次の正解で 3
            challengeCount = 4, // 次の正解で 5
            lastCorrectTime = 0L // 1時間以上経過
        )

        val result = MasteryScheduler.onCorrect(
            state = mastery,
            actualMode = QuizMode.LISTEN_FILL_BLANK,
            isAudioRestricted = false,
            timingSettings = timingSettings
        )

        assertTrue("飛び級が発生すること", result.isFlyingLevelUp)
        assertEquals("元レベルは6", 6, result.oldLevel)
        assertEquals("新レベルは8", 8, result.newLevel)
        assertEquals("ボーナスレベルは1", 1, result.bonusLevel)
        assertEquals("スキップされたレベルは7", 7, result.skippedLevel)
        assertEquals("Entityのレベルも8に更新されていること", 8, mastery.level)
    }

    @Test
    fun testFlyingLevelUp_LV8_to_LV10() {
        val mastery = WordMasteryEntity(
            wordId = 1,
            level = 8,
            currentStreak = 3,
            challengeCount = 5,
            lastCorrectTime = 0L
        )

        val result = MasteryScheduler.onCorrect(
            state = mastery,
            actualMode = QuizMode.ANTONYM_PICK,
            isAudioRestricted = false,
            timingSettings = timingSettings
        )

        assertTrue("飛び級が発生すること", result.isFlyingLevelUp)
        assertEquals("元レベルは8", 8, result.oldLevel)
        assertEquals("新レベルは10", 10, result.newLevel)
        assertEquals("スキップされたレベルは9", 9, result.skippedLevel)
        assertEquals("Entityのレベルも10に更新されていること", 10, mastery.level)
    }

    @Test
    fun testNormalLevelUp_LV1_to_LV2() {
        val mastery = WordMasteryEntity(
            wordId = 1,
            level = 1,
            currentStreak = 10,
            challengeCount = 10,
            lastCorrectTime = 0L
        )

        val result = MasteryScheduler.onCorrect(
            state = mastery,
            actualMode = QuizMode.EN_TO_JP,
            isAudioRestricted = false,
            timingSettings = timingSettings
        )

        assertFalse("LV1では飛び級が発生しないこと", result.isFlyingLevelUp)
        assertEquals("元レベルは1", 1, result.oldLevel)
        assertEquals("新レベルは2", 2, result.newLevel)
        assertNull("スキップされたレベルはない", result.skippedLevel)
    }

    @Test
    fun testNoFlyingLevelUp_ShortStreak() {
        val mastery = WordMasteryEntity(
            wordId = 1,
            level = 6,
            currentStreak = 1, // 次で 2 (3未満)
            challengeCount = 10,
            lastCorrectTime = 0L
        )

        val result = MasteryScheduler.onCorrect(
            state = mastery,
            actualMode = QuizMode.LISTEN_FILL_BLANK,
            isAudioRestricted = false,
            timingSettings = timingSettings
        )

        assertFalse("ストリーク不足で飛び級しないこと", result.isFlyingLevelUp)
        assertEquals(7, result.newLevel)
    }

    @Test
    fun testNoFlyingLevelUp_TooSoon() {
        val mastery = WordMasteryEntity(
            wordId = 1,
            level = 6,
            currentStreak = 5,
            challengeCount = 10,
            lastCorrectTime = System.currentTimeMillis() // 直前
        )

        val result = MasteryScheduler.onCorrect(
            state = mastery,
            actualMode = QuizMode.LISTEN_FILL_BLANK,
            isAudioRestricted = false,
            timingSettings = timingSettings
        )

        assertFalse("時間経過不足で飛び級しないこと", result.isFlyingLevelUp)
        assertEquals(7, result.newLevel)
    }
}
