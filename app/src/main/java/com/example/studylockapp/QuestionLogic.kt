package com.example.studylockapp

import android.content.Context
import com.example.studylockapp.data.FillBlankQuestion
import com.example.studylockapp.data.WordEntity
import kotlin.math.abs

/**
 * 問題の選択肢作成やテキスト整形を行うロジッククラス
 * 画面操作（View）は行わず、計算結果だけを返す
 */
object QuestionLogic {

    /**
     * 級の文字列を比較用のランク(整数)に変換する
     * 5級=1 〜 1級=7。数字が大きいほど上位級。
     */
    private fun gradeRank(g: String?): Int {
        return when (g) {
            "5" -> 1
            "4" -> 2
            "3" -> 3
            "2.5" -> 4
            "2" -> 5
            "1.5" -> 6
            "1" -> 7
            else -> 0
        }
    }

    /**
     * 先頭2文字が一致するか判定する
     */
    private fun samePrefix(a: String, b: String): Boolean {
        val len = minOf(2, a.length, b.length)
        if (len == 0) return false
        return a.take(len) == b.take(len)
    }

    /**
     * 穴埋め問題のデータをUIで使いやすい形式に変換する。
     * 選択肢をシャッフルし、正解のインデックスを再計算する。
     */
    fun prepareFillBlankQuestion(question: FillBlankQuestion): LegacyQuestionContext {
        val originalCorrectChoice = question.choices[question.correctIndex]
        val shuffledChoices = question.choices.shuffled()
        val newCorrectIndex = shuffledChoices.indexOf(originalCorrectChoice)

        // 正しいWordEntityのコンストラクタに合わせて修正
        val dummyWord = WordEntity(
            no = question.id,
            grade = question.grade,
            word = "fill_blank_${question.id}", // PrimaryKeyなので一意な値を設定
            japanese = question.explanation, // explanationを格納
            description = null,
            smallTopicId = null,
            mediumCategoryId = null
        )

        val title = if (question.unit == 1) {
            "（　）に入る語を選んでください"
        } else {
            "会話の続きを選んでください"
        }

        return LegacyQuestionContext(
            word = dummyWord,
            title = title,
            body = question.question.replace("\\n", "\n"), // \nを改行に変換
            options = shuffledChoices,
            correctIndex = newCorrectIndex,
            shouldAutoPlay = false,
            audioText = ""
        )
    }

    /**
     * 正解とプールから選択肢リスト（正解含む）を生成する
     */
    fun buildChoices(correct: WordEntity, pool: List<WordEntity>, count: Int, mode: String): List<WordEntity> {
        val correctDisplay = getCorrectStringForMode(correct, mode)

        // スペル(word)と表示テキストの両方でチェック（チャッピー推奨）
        val candidates = pool.filter {
            it.no != correct.no &&
            it.word != correct.word &&
            getCorrectStringForMode(it, mode) != correctDisplay
        }
        if (candidates.isEmpty()) return listOf(correct)

        val distractors = when (mode) {
            LearningModes.LISTENING, LearningModes.LISTENING_JP -> getListeningChoices(correct, candidates, count - 1, mode)
            else -> getStandardChoices(correct, candidates, count - 1, mode)
        }
        return (distractors + correct).shuffled()
    }

    /**
     * 通常モード用の選択肢選定（小カテゴリや中カテゴリを考慮）
     */
    private fun getStandardChoices(correct: WordEntity, candidates: List<WordEntity>, count: Int, mode: String): List<WordEntity> {
        val promptText = when (mode) {
            LearningModes.MEANING, LearningModes.EN_EN_1 -> correct.word
            LearningModes.JA_TO_EN -> correct.japanese ?: ""
            LearningModes.EN_EN_2 -> correct.description ?: ""
            else -> ""
        }
        val correctChoiceText = getCorrectStringForMode(correct, mode)

        fun getDisplay(w: WordEntity) = getCorrectStringForMode(w, mode)
        fun isPromptMatch(w: WordEntity) = getDisplay(w) == promptText
        fun isSamePrefix(w: WordEntity) = samePrefix(correctChoiceText, getDisplay(w))

        // 1. プロンプトと一致するものは除外
        val basePool = candidates.filter { getDisplay(it).isNotEmpty() && !isPromptMatch(it) }

        // 2. 同じ級の中から選定
        // 現在は保存値一致で同級判定（将来は gradeRank 比較へ移行予定）
        val sameGradePool = basePool.filter { it.grade == correct.grade }

        // チャッピー推奨：説明文が長い英英モードではPrefixフィルタを適用しない
        val usePrefixFilter = mode != LearningModes.EN_EN_1 && mode != LearningModes.EN_EN_2
        val strictPool = if (usePrefixFilter) {
            sameGradePool.filter { !isSamePrefix(it) }
        } else {
            sameGradePool
        }

        fun collectCategorized(pool: List<WordEntity>, targetCount: Int, existing: List<WordEntity>): List<WordEntity> {
            if (targetCount <= 0) return emptyList()
            val existingIds = existing.map { it.no }.toSet()
            val available = pool.filter { it.no !in existingIds }

            val sameSmallTopic = available.filter { !it.smallTopicId.isNullOrEmpty() && it.smallTopicId == correct.smallTopicId }.shuffled()
            val pickedIds = sameSmallTopic.map { it.no }.toSet()

            val sameMediumCategory = available.filter { it.no !in pickedIds && !it.mediumCategoryId.isNullOrEmpty() && it.mediumCategoryId == correct.mediumCategoryId }.shuffled()
            val pickedIds2 = pickedIds + sameMediumCategory.map { it.no }

            val others = available.filter { it.no !in pickedIds2 }.shuffled()

            val result = mutableListOf<WordEntity>()
            val seenDisplays = existing.map { getDisplay(it) }.toMutableSet()

            for (w in (sameSmallTopic + sameMediumCategory + others)) {
                if (result.size >= targetCount) break
                val display = getDisplay(w)
                if (display !in seenDisplays) {
                    result.add(w)
                    seenDisplays.add(display)
                }
            }
            return result
        }

        // 1. 厳格フィルタ（同級・Prefix一致なし）
        var result = collectCategorized(strictPool, count, emptyList())

        // 2. 同級補充（Prefix一致を許容）
        if (result.size < count) {
            result = result + collectCategorized(sameGradePool, count - result.size, result)
        }

        // 3. 全級補充
        if (result.size < count) {
            result = result + collectCategorized(basePool, count - result.size, result)
        }

        return result
    }

    /**
     * リスニングモード用の選択肢選定（発音や綴りが似ているものを選定）
     */
    private fun getListeningChoices(correct: WordEntity, candidates: List<WordEntity>, count: Int, mode: String): List<WordEntity> {
        val correctGradeVal = gradeRank(correct.grade)
        val correctLen = correct.word.length
        
        // 級が近く、長さが近く、表示テキストが空でないものを候補にする
        val validPool = candidates.filter {
            val gVal = gradeRank(it.grade)
            gVal >= correctGradeVal && abs(it.word.length - correctLen) <= 3 &&
            getCorrectStringForMode(it, mode).isNotEmpty()
        }
        val poolToUse = if (validPool.size < count) candidates.filter { getCorrectStringForMode(it, mode).isNotEmpty() } else validPool

        val p2 = correct.word.take(2).lowercase()
        val p1 = correct.word.take(1).lowercase()

        // 優先度1: 最初の2文字が一致（空文字ガード）
        val priority1 = if (p2.length == 2) {
            poolToUse.filter { it.word.lowercase().startsWith(p2) }.shuffled()
        } else emptyList()
        val p1Ids = priority1.map { it.no }.toSet()
        
        // 優先度2: 最初の1文字が一致（ガード付き）
        val priority2 = if (p1.isNotEmpty()) {
            poolToUse.filter { it.no !in p1Ids && it.word.lowercase().startsWith(p1) }.shuffled()
        } else emptyList()
        val p1p2Ids = p1Ids + priority2.map { it.no }
        
        // 優先度3: 長さが±1文字
        val priority3 = poolToUse.filter { it.no !in p1p2Ids && abs(it.word.length - correctLen) <= 1 }.shuffled()
        
        // その他
        val others = poolToUse.filter { it.no !in p1p2Ids && it.no !in priority3.map { w -> w.no } }.shuffled()

        val allCandidates = (priority1 + priority2 + priority3 + others)
        val result = mutableListOf<WordEntity>()
        val seenDisplays = mutableSetOf<String>()

        for (w in allCandidates) {
            if (result.size >= count) break
            val display = getCorrectStringForMode(w, mode)
            if (display !in seenDisplays) {
                result.add(w)
                seenDisplays.add(display)
            }
        }
        
        // 足りなければ補充
        if (result.size < count) {
            val currentIds = result.map { it.no }.toSet()
            val supplement = candidates.filter { it.no !in currentIds && it.no != correct.no && getCorrectStringForMode(it, mode).isNotEmpty() }.shuffled()
            for (w in supplement) {
                if (result.size >= count) break
                val display = getCorrectStringForMode(w, mode)
                if (display !in seenDisplays) {
                    result.add(w)
                    seenDisplays.add(display)
                }
            }
        }
        return result
    }

    /**
     * モードに応じたタイトル、本文、選択肢テキストのセットを返す
     */
    fun formatQuestionAndOptions(context: Context, correct: WordEntity, choices: List<WordEntity>, mode: String): Triple<String, String, List<String>> {
        return when (mode) {
            LearningModes.MEANING -> Triple(context.getString(R.string.question_title_meaning), correct.word, choices.map { it.japanese ?: "" })
            LearningModes.LISTENING -> Triple(context.getString(R.string.question_title_listening), "", choices.map { it.word })
            LearningModes.LISTENING_JP -> Triple(context.getString(R.string.question_title_listening_jp), "", choices.map { it.japanese ?: "" })
            LearningModes.JA_TO_EN -> Triple(context.getString(R.string.question_title_ja_to_en), correct.japanese ?: "", choices.map { it.word })
            LearningModes.EN_EN_1 -> Triple(context.getString(R.string.question_title_en_en_1), correct.word, choices.map { it.description ?: "" })
            LearningModes.EN_EN_2 -> Triple(context.getString(R.string.question_title_en_en_2), correct.description ?: "", choices.map { it.word })
            else -> Triple("", "", emptyList())
        }
    }

    /**
     * 正解判定に使う文字列を取得する
     */
    fun getCorrectStringForMode(word: WordEntity, mode: String): String {
        return when (mode) {
            LearningModes.MEANING, LearningModes.LISTENING_JP -> word.japanese ?: ""
            LearningModes.LISTENING, LearningModes.JA_TO_EN, LearningModes.EN_EN_2 -> word.word
            LearningModes.EN_EN_1 -> word.description ?: ""
            else -> word.japanese ?: ""
        }
    }
}