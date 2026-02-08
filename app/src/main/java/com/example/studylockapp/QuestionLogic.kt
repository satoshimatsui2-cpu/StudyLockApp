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
        val candidates = pool.filter {
            it.no != correct.no && it.word != correct.word && it.japanese != correct.japanese
        }
        if (candidates.isEmpty()) return listOf(correct)

        val distractors = when (mode) {
            LearningModes.LISTENING, LearningModes.LISTENING_JP -> getListeningChoices(correct, candidates, count - 1)
            else -> getStandardChoices(correct, candidates, count - 1)
        }
        return (distractors + correct).shuffled()
    }

    /**
     * 通常モード用の選択肢選定（小カテゴリや中カテゴリを考慮）
     */
    private fun getStandardChoices(correct: WordEntity, candidates: List<WordEntity>, count: Int): List<WordEntity> {
        val sameGradePool = candidates.filter { it.grade == correct.grade }
        // 同じ級がなければ全体から選ぶ
        if (sameGradePool.isEmpty()) return candidates.shuffled().take(count)

        val correctSmallTopic = correct.smallTopicId
        val sameSmallTopic = if (!correctSmallTopic.isNullOrEmpty()) {
            sameGradePool.filter { !it.smallTopicId.isNullOrEmpty() && it.smallTopicId == correctSmallTopic }.shuffled()
        } else emptyList()
        val pickedIds = sameSmallTopic.map { it.no }.toSet()

        val correctMediumCategory = correct.mediumCategoryId
        val sameMediumCategory = if (!correctMediumCategory.isNullOrEmpty()) {
            sameGradePool.filter { it.no !in pickedIds && !it.mediumCategoryId.isNullOrEmpty() && it.mediumCategoryId == correctMediumCategory }.shuffled()
        } else emptyList()

        val others = sameGradePool.filter {
            it.no !in pickedIds &&
            (it.mediumCategoryId != correctMediumCategory || correctMediumCategory.isNullOrEmpty())
        }.shuffled()

        return (sameSmallTopic + sameMediumCategory + others).take(count)
    }

    /**
     * リスニングモード用の選択肢選定（発音や綴りが似ているものを選定）
     */
    private fun getListeningChoices(correct: WordEntity, candidates: List<WordEntity>, count: Int): List<WordEntity> {
        val correctGradeVal = correct.grade?.toIntOrNull() ?: 0
        val correctLen = correct.word.length
        
        // 級が近く、長さが近いものを候補にする
        val validPool = candidates.filter {
            val gVal = it.grade?.toIntOrNull() ?: 0
            gVal >= correctGradeVal && abs(it.word.length - correctLen) <= 3
        }
        val poolToUse = if (validPool.size < count) candidates else validPool

        val p2 = correct.word.take(2).lowercase()
        val p1 = correct.word.take(1).lowercase()

        // 優先度1: 最初の2文字が一致
        val priority1 = poolToUse.filter { it.word.lowercase().startsWith(p2) }.shuffled()
        val p1Ids = priority1.map { it.no }.toSet()
        
        // 優先度2: 最初の1文字が一致
        val priority2 = poolToUse.filter { it.no !in p1Ids && it.word.lowercase().startsWith(p1) }.shuffled()
        val p1p2Ids = p1Ids + priority2.map { it.no }
        
        // 優先度3: 長さが±1文字
        val priority3 = poolToUse.filter { it.no !in p1p2Ids && abs(it.word.length - correctLen) <= 1 }.shuffled()
        
        // その他
        val others = poolToUse.filter { it.no !in p1p2Ids && it.no !in priority3.map { w -> w.no } }.shuffled()

        val result = (priority1 + priority2 + priority3 + others).take(count)
        
        // 足りなければ補充
        return if (result.size < count) {
            result + candidates.filter { it.no !in result.map { w -> w.no } && it.no != correct.no }.shuffled().take(count - result.size)
        } else {
            result
        }
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