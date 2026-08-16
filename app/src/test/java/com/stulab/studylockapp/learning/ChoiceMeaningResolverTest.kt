package com.stulab.studylockapp.learning

import com.stulab.studylockapp.data.db.ChoiceMeaningEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChoiceMeaningResolverTest {

    @Test
    fun testNormalization() {
        assertEquals("apple", ReviewMeaningResolver.normalizeChoiceText(" Apple "))
        assertEquals("be good at", ReviewMeaningResolver.normalizeChoiceText("BE  GOOD  AT"))
        assertEquals("apple", ReviewMeaningResolver.normalizeChoiceText("ａｐｐｌｅ")) // NFKC
    }

    @Test
    fun testResolveFinalMeaning_Priority() {
        val source = "出題元の意味"
        val db = "既存DBの意味"
        val sup = "補完DBの意味"

        // 1. 正解かつ出題元あり -> 出題元最優先
        assertEquals(source, ReviewMeaningResolver.resolveFinalMeaning(true, source, db, sup))
        
        // 2. 正解だが出題元なし -> 既存DB
        assertEquals(db, ReviewMeaningResolver.resolveFinalMeaning(true, null, db, sup))
        
        // 3. 不正解 -> 既存DB
        assertEquals(db, ReviewMeaningResolver.resolveFinalMeaning(false, source, db, sup))
        
        // 4. 既存DBなし -> 補完DB
        assertEquals(sup, ReviewMeaningResolver.resolveFinalMeaning(false, source, null, sup))
        assertEquals(sup, ReviewMeaningResolver.resolveFinalMeaning(false, source, " ", sup))
        
        // 5. 全部なし
        assertNull(ReviewMeaningResolver.resolveFinalMeaning(false, null, null, null))
    }

    @Test
    fun testResolveFromChoiceMeanings_ContextPriority() {
        val mode = "LISTEN_EN"
        val wordId = 123L
        val normalized = "test"
        
        val fullMatch = ChoiceMeaningEntity("key1", normalized, "test", "完全一致", mode, wordId)
        val modeMatch = ChoiceMeaningEntity("key2", normalized, "test", "モード一致", mode, null)
        val commonMatch = ChoiceMeaningEntity("key3", normalized, "test", "共通", null, null)
        
        // 完全一致優先
        assertEquals("完全一致", ReviewMeaningResolver.resolveFromChoiceMeanings(normalized, mode, wordId, listOf(fullMatch, modeMatch, commonMatch)))
        
        // モード一致
        assertEquals("モード一致", ReviewMeaningResolver.resolveFromChoiceMeanings(normalized, mode, wordId, listOf(modeMatch, commonMatch)))
        
        // 共通
        assertEquals("共通", ReviewMeaningResolver.resolveFromChoiceMeanings(normalized, mode, wordId, listOf(commonMatch)))
        
        // 該当なし
        assertNull(ReviewMeaningResolver.resolveFromChoiceMeanings(normalized, mode, wordId, emptyList()))
    }

    /**
     * 既知例の解決テスト
     */
    @Test
    fun testKnownExamples() {
        // 補完対象候補
        val examples = listOf(
            "be exhausted from", "be afraid of", "be interested in", "be good at",
            "look forward to", "at once", "for example", "by myself",
            "above", "below", "ignore", "reject", "calm", "quiz"
        )
        
        // 補完DB想定データ (実際はDBから取得するが、ここではResolverのロジックのみ)
        examples.forEach { eng ->
            val normalized = ReviewMeaningResolver.normalizeChoiceText(eng)
            val supMeaning = when (normalized) {
                "be exhausted from" -> "～で疲れ果てている"
                "be afraid of" -> "～を恐れている"
                "be interested in" -> "～に興味がある"
                "be good at" -> "～が得意だ"
                "look forward to" -> "～を楽しみに待つ"
                "at once" -> "すぐに"
                "for example" -> "例えば"
                "by myself" -> "自分で"
                "above" -> "～の上に"
                "below" -> "～の下に"
                "ignore" -> "～を無視する"
                "reject" -> "～を拒絶する"
                "calm" -> "穏やかな"
                "quiz" -> "小テスト"
                else -> null
            }
            
            val final = ReviewMeaningResolver.resolveFinalMeaning(
                isCorrect = false,
                sourceWordJapanese = null,
                wordEntityMeaning = null,
                supplementaryMeaning = supMeaning
            )
            assertEquals("解決失敗: $eng", supMeaning, final)
        }
    }
}
