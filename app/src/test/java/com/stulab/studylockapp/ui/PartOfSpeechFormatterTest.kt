package com.stulab.studylockapp.ui

import android.content.Context
import com.stulab.studylockapp.R
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class PartOfSpeechFormatterTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = mockk()
        // Mock all POS strings
        every { context.getString(R.string.pos_noun) } returns "名詞"
        every { context.getString(R.string.pos_verb) } returns "動詞"
        every { context.getString(R.string.pos_adjective) } returns "形容詞"
        every { context.getString(R.string.pos_adverb) } returns "副詞"
        every { context.getString(R.string.pos_pronoun) } returns "代名詞"
        every { context.getString(R.string.pos_preposition) } returns "前置詞"
        every { context.getString(R.string.pos_conjunction) } returns "接続詞"
        every { context.getString(R.string.pos_interjection) } returns "間投詞"
        every { context.getString(R.string.pos_article) } returns "冠詞"
        every { context.getString(R.string.pos_determiner) } returns "限定詞"
        every { context.getString(R.string.pos_auxiliary) } returns "助動詞"
        every { context.getString(R.string.pos_modal_verb) } returns "法助動詞"
        every { context.getString(R.string.pos_proper_noun) } returns "固有名詞"
        every { context.getString(R.string.pos_numeral) } returns "数詞"
        every { context.getString(R.string.pos_phrase) } returns "熟語・句"
        every { context.getString(R.string.pos_idiom) } returns "慣用句"
        every { context.getString(R.string.pos_phrasal_verb) } returns "句動詞"
        every { context.getString(R.string.pos_prepositional_phrase) } returns "前置詞句"
        every { context.getString(R.string.pos_sentence) } returns "文"
        every { context.getString(R.string.pos_other) } returns "その他"
    }

    @Test
    fun `toJapanese returns correct Japanese for known POS`() {
        assertEquals("名詞", PartOfSpeechFormatter.toJapanese(context, "noun"))
        assertEquals("動詞", PartOfSpeechFormatter.toJapanese(context, "verb"))
        assertEquals("形容詞", PartOfSpeechFormatter.toJapanese(context, "adjective"))
        assertEquals("副詞", PartOfSpeechFormatter.toJapanese(context, "adverb"))
    }

    @Test
    fun `toJapanese handles normalization`() {
        assertEquals("動詞", PartOfSpeechFormatter.toJapanese(context, " VERB "))
        assertEquals("形容詞", PartOfSpeechFormatter.toJapanese(context, "adj."))
        assertEquals("副詞", PartOfSpeechFormatter.toJapanese(context, "adv"))
    }

    @Test
    fun `toJapanese returns null for null or empty`() {
        assertNull(PartOfSpeechFormatter.toJapanese(context, null))
        assertNull(PartOfSpeechFormatter.toJapanese(context, ""))
        assertNull(PartOfSpeechFormatter.toJapanese(context, "   "))
    }

    @Test
    fun `toJapanese returns Other for unknown POS`() {
        assertEquals("その他", PartOfSpeechFormatter.toJapanese(context, "unknown_pos"))
    }

    @Test
    fun `toJapanese handles specific phrases`() {
        assertEquals("熟語・句", PartOfSpeechFormatter.toJapanese(context, "verb phrase"))
        assertEquals("熟語・句", PartOfSpeechFormatter.toJapanese(context, "phrase"))
        assertEquals("助動詞", PartOfSpeechFormatter.toJapanese(context, "auxiliary verb"))
        assertEquals("法助動詞", PartOfSpeechFormatter.toJapanese(context, "modal verb"))
        assertEquals("文", PartOfSpeechFormatter.toJapanese(context, "sentence"))
    }
}
