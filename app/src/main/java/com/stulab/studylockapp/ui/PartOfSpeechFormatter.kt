package com.stulab.studylockapp.ui

import android.content.Context
import com.stulab.studylockapp.R

object PartOfSpeechFormatter {

    fun toJapanese(context: Context, raw: String?): String? {
        val normalized = raw?.trim()?.lowercase() ?: return null
        if (normalized.isEmpty()) return null

        val resId = when (normalized) {
            "noun", "n.", "n" -> R.string.pos_noun
            "verb", "v.", "v" -> R.string.pos_verb
            "adjective", "adj.", "adj" -> R.string.pos_adjective
            "adverb", "adv.", "adv" -> R.string.pos_adverb
            "pronoun", "pron." -> R.string.pos_pronoun
            "preposition", "prep." -> R.string.pos_preposition
            "conjunction", "conj." -> R.string.pos_conjunction
            "interjection" -> R.string.pos_interjection
            "article" -> R.string.pos_article
            "determiner" -> R.string.pos_determiner
            "auxiliary", "auxiliary verb", "aux." -> R.string.pos_auxiliary
            "modal verb" -> R.string.pos_modal_verb
            "proper noun" -> R.string.pos_proper_noun
            "numeral" -> R.string.pos_numeral
            "phrase", "verb phrase" -> R.string.pos_phrase
            "idiom" -> R.string.pos_idiom
            "phrasal verb" -> R.string.pos_phrasal_verb
            "prepositional phrase" -> R.string.pos_prepositional_phrase
            "sentence" -> R.string.pos_sentence
            else -> null
        }

        return if (resId != null) {
            context.getString(resId)
        } else {
            // 未知の品詞の場合は「その他」を表示
            context.getString(R.string.pos_other)
        }
    }
}
