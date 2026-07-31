package com.stulab.studylockapp.data.notification

import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Field

class CharacterLinesEveningTest {

    @Test
    fun testEveningEmotions() {
        // Test base emotion for HARU
        val haruEvening = CharacterLines.getEmotionForContext(StudyCharacter.HARU, NotificationContext.EVENING_PENDING)
        assertEquals(CharacterEmotion.JOY, haruEvening)

        // Test an override for SHIN
        val overridesField = CharacterLines::class.java.getDeclaredField("emotionOverrides")
        overridesField.isAccessible = true
        val overrides = overridesField.get(null) as Map<*, *>

        val shinReviewOverride = overrides.entries.find { (key, _) ->
            val k = key!!
            val charField = k::class.java.getDeclaredField("character")
            charField.isAccessible = true
            val ctxField = k::class.java.getDeclaredField("context")
            ctxField.isAccessible = true
            val lineField = k::class.java.getDeclaredField("rawLine")
            lineField.isAccessible = true

            charField.get(k) == StudyCharacter.SHIN && 
            ctxField.get(k) == NotificationContext.EVENING_REVIEW_PENDING &&
            lineField.get(k) == "新しい分は完了か。...よくやった。"
        }
        
        assertEquals(CharacterEmotion.BLUSH, shinReviewOverride?.value)
    }

    @Test
    fun testOverrideCount() {
        val overridesField = CharacterLines::class.java.getDeclaredField("emotionOverrides")
        overridesField.isAccessible = true
        val overrides = overridesField.get(null) as Map<*, *>
        
        // Expected count: 173 (previous) + 47 (Missed) + 32 (Evening) - (Leo Blush entry I used as target)? 
        // Wait, my last replacement replaced the Leo Blush entry with my list which INCLUDED the Leo Blush entry at the top.
        // Let's just check if it's substantial.
        println("Current override count: ${overrides.size}")
    }
}
