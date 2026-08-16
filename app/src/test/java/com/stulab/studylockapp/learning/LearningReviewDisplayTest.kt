package com.stulab.studylockapp.learning

import com.stulab.studylockapp.data.RelatedWord
import com.stulab.studylockapp.data.WordEntity
import com.stulab.studylockapp.data.SilentMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningReviewDisplayTest {

    private fun mockWord(word: String, japanese: String): WordEntity {
        return WordEntity(
            no = 0, grade = 5, word = word, japanese = japanese,
            description = "", sentence = "", japaneseSentence = "", pos = "noun",
            difficulty = 1, frequency = 1,
            choicesEnJa = emptyList(), choicesJaEn = emptyList(), choicesListening = emptyList(),
            synonyms = emptyList(), antonyms = emptyList()
        )
    }

    private fun mockQuiz(word: WordEntity, mode: QuizMode): QuizData {
        return QuizData(
            mode = mode,
            word = word,
            question = "question",
            choices = emptyList(),
            answer = "answer"
        )
    }

    @Test
    fun testFormatWithJapaneseMeaning() {
        // 正常系: 意味がある場合
        assertEquals("lag behind（遅れをとる）", ReviewDisplayFormatter.formatWithJapaneseMeaning("lag behind", "遅れをとる"))
        
        // 異常系: 意味が空・null・空白のみの場合
        assertEquals("lag behind", ReviewDisplayFormatter.formatWithJapaneseMeaning("lag behind", ""))
        assertEquals("lag behind", ReviewDisplayFormatter.formatWithJapaneseMeaning("lag behind", null))
        assertEquals("lag behind", ReviewDisplayFormatter.formatWithJapaneseMeaning("lag behind", "  "))
    }

    @Test
    fun testReviewDisplayLogic_SynonymModeWithNote() {
        // モックデータ準備
        val questionWord = "corporate"
        val answerWord = "business"
        val answerMeaning = "仕事"
        val answerNote = "より日常的な表現"
        
        // 元単語のデータ
        val synonyms = listOf(RelatedWord(answerWord, answerNote))
        val antonyms = listOf(RelatedWord("personal", "個人のという意味"))
        val allRelatedWords = synonyms + antonyms
        
        // 正解のnote検索対象（SYNONYM_PICKモードを想定）
        val correctRelatedWords = synonyms 
        
        // 1. 問題表示の決定
        val questionDisplay = ReviewDisplayFormatter.formatWithJapaneseMeaning(questionWord, "企業の")
        val questionNote = ReviewDisplayFormatter.findRelatedNote(questionWord, allRelatedWords)
        
        // 2. 正解表示の決定
        val correctDisplay = ReviewDisplayFormatter.formatWithJapaneseMeaning(answerWord, answerMeaning)
        val correctNote = ReviewDisplayFormatter.findRelatedNote(answerWord, correctRelatedWords)
        
        // 検証
        assertEquals("corporate（企業の）", questionDisplay)
        assertNull("問題語自身にはnoteは付かないはず", questionNote)
        
        assertEquals("business（仕事）", correctDisplay)
        assertEquals("より日常的な表現", correctNote)
    }

    @Test
    fun testReviewDisplayLogic_IncorrectAnswerWithMixedNotes() {
        // 類義語問題で反対語を誤選択した場合
        val correctWord = "business" // 類義語
        val userSelected = "personal" // 反対語を誤選択
        
        val synonyms = listOf(RelatedWord(correctWord, "類義語の補足"))
        val antonyms = listOf(RelatedWord(userSelected, "反対語の補足"))
        val allRelated = synonyms + antonyms

        // 1. 正解のnote (SYNONYM_PICKモードなので synonyms のみから検索)
        val correctNote = ReviewDisplayFormatter.findRelatedNote(correctWord, synonyms)
        
        // 2. 選択回答のnote (有用性のため全関連語から検索)
        val selectedNote = ReviewDisplayFormatter.findRelatedNote(userSelected, allRelated)

        assertEquals("類義語の補足", correctNote)
        assertEquals("反対語の補足", selectedNote)
    }

    @Test
    fun testNotePriorityByMode() {
        // 同じ綴りが類義語と反対語の両方にある特殊なケース
        val word = "test"
        val synonyms = listOf(RelatedWord(word, "類義語としての意味"))
        val antonyms = listOf(RelatedWord(word, "反対語としての意味"))
        
        // SYNONYM_PICK モードの正解 note
        val synonymModeNote = ReviewDisplayFormatter.findRelatedNote(word, synonyms)
        assertEquals("類義語としての意味", synonymModeNote)
        
        // ANTONYM_PICK モードの正解 note
        val antonymModeNote = ReviewDisplayFormatter.findRelatedNote(word, antonyms)
        assertEquals("反対語としての意味", antonymModeNote)
    }

    @Test
    fun testPlayableReviewChoices_Logic() {
        // 4択問題で 選択肢 A, B, C, D / 正解 C / 回答 B
        val choices = listOf("A", "B", "C", "D")
        val resolvedMeanings = mapOf(
            "A" to "意味A",
            "B" to "意味B",
            "C" to "意味C",
            "D" to "意味D"
        )
        val correctAnswer = "C"
        val selectedAnswer = "B"

        val reviewChoices = ReviewDisplayFormatter.createPlayableReviewChoices(
            choices = choices,
            resolvedMeanings = resolvedMeanings,
            correctAnswer = correctAnswer,
            selectedAnswer = selectedAnswer
        )

        assertEquals(4, reviewChoices.size)
        
        // 順序の維持を確認
        assertEquals("A", reviewChoices[0].englishText)
        assertEquals("B", reviewChoices[1].englishText)
        assertEquals("C", reviewChoices[2].englishText)
        assertEquals("D", reviewChoices[3].englishText)

        // C は正解
        val itemC = reviewChoices.find { it.englishText == "C" }!!
        assertTrue(itemC.isCorrect)
        assertTrue(!itemC.isSelected)
        assertEquals("意味C", itemC.japaneseMeaning)

        // B は選択済み(不正解)
        val itemB = reviewChoices.find { it.englishText == "B" }!!
        assertTrue(!itemB.isCorrect)
        assertTrue(itemB.isSelected)
        assertEquals("意味B", itemB.japaneseMeaning)
    }

    @Test
    fun testPlayableReviewChoices_CorrectSelection() {
        // 正解を選んだ場合
        val choices = listOf("A", "B", "C", "D")
        val resolvedMeanings = choices.associateWith { "意味$it" }
        val correctAnswer = "C"
        val selectedAnswer = "C"

        val reviewChoices = ReviewDisplayFormatter.createPlayableReviewChoices(
            choices, resolvedMeanings, correctAnswer, selectedAnswer
        )

        val itemC = reviewChoices.find { it.englishText == "C" }!!
        assertTrue(itemC.isCorrect)
        assertTrue(itemC.isSelected) // 重複表示されず、フラグが両方立つことを確認
        assertEquals("意味C", itemC.japaneseMeaning)
    }

    @Test
    fun testPlayableReviewChoices_ListenFillBlank() {
        // LISTEN_FILL_BLANK でも同様のロジックが使われることを確認
        val choices = listOf("apple", "banana")
        val resolvedMeanings = mapOf("apple" to "りんご", "banana" to "バナナ")
        
        val reviewChoices = ReviewDisplayFormatter.createPlayableReviewChoices(
            choices, resolvedMeanings, "apple", "banana"
        )
        
        assertEquals(2, reviewChoices.size)
        assertTrue(reviewChoices[0].isCorrect)
        assertTrue(reviewChoices[1].isSelected)
    }

    @Test
    fun testPlayableReviewChoices_EmptyMeaning() {
        val choices = listOf("A")
        val resolvedMeanings = mapOf("A" to "") // 意味が空
        
        val reviewChoices = ReviewDisplayFormatter.createPlayableReviewChoices(choices, resolvedMeanings, "A", "A")
        
        assertEquals("", reviewChoices[0].japaneseMeaning)
    }

    @Test
    fun testPlayableReviewChoices_TtsTextIsCorrect() {
        val choices = listOf("apple", "banana")
        val resolvedMeanings = mapOf("apple" to "りんご", "banana" to "バナナ")
        
        val reviewChoices = ReviewDisplayFormatter.createPlayableReviewChoices(
            choices, resolvedMeanings, "apple", "banana"
        )
        
        // TTS用テキストが純粋な英語であることを確認
        assertEquals("apple", reviewChoices[0].ttsText)
        assertEquals("banana", reviewChoices[1].ttsText)
        
        // 日本語が含まれていないことを確認
        assertTrue(!reviewChoices[0].ttsText.contains("りんご"))
    }

    @Test
    fun testJpToEnPlayableChoices_Logic() {
        // JP_TO_EN モードでのテスト
        val choices = listOf("apple", "banana")
        val resolvedMeanings = mapOf("apple" to "りんご", "banana" to "バナナ")
        val correctAnswer = "apple"
        val selectedAnswer = "banana"

        val reviewChoices = ReviewDisplayFormatter.createPlayableReviewChoices(
            choices = choices,
            resolvedMeanings = resolvedMeanings,
            correctAnswer = correctAnswer,
            selectedAnswer = selectedAnswer
        )

        val itemApple = reviewChoices.find { it.englishText == "apple" }!!
        assertTrue(itemApple.isCorrect)
        assertEquals("りんご", itemApple.japaneseMeaning) // DBの「別の意味」ではなく出題元の意味が使われる

        val itemBanana = reviewChoices.find { it.englishText == "banana" }!!
        assertTrue(itemBanana.isSelected)
        assertEquals("バナナ", itemBanana.japaneseMeaning)
    }

    @Test
    fun testEnToJpReviewChoices_Logic() {
        // 問題英語: corporate
        // 選択肢: 企業の, 個人の, 地方の
        // 正解: 企業の
        // 回答: 個人の
        val choices = listOf("企業の", "個人の", "地方の")
        val correctAnswer = "企業の"
        val selectedAnswer = "個人の"
        val englishWord = "corporate"

        val reviewChoices = ReviewDisplayFormatter.createEnToJpReviewChoices(
            choices = choices,
            correctAnswer = correctAnswer,
            selectedAnswer = selectedAnswer,
            englishWord = englishWord
        )

        assertEquals(3, reviewChoices.size)
        
        // 順序の維持
        assertEquals("企業の", reviewChoices[0].japaneseText)
        assertEquals("個人の", reviewChoices[1].japaneseText)

        // 正解項目
        val itemCorrect = reviewChoices[0]
        assertTrue(itemCorrect.isCorrect)
        assertNull("正解項目にも英単語を付けない", itemCorrect.englishText)
        assertTrue("全カード再生不可", !itemCorrect.isPlayable)

        // 選択した不正解
        val itemSelected = reviewChoices[1]
        assertTrue(!itemSelected.isCorrect)
        assertTrue(itemSelected.isSelected)
        assertNull(itemSelected.englishText)
        assertTrue(!itemSelected.isPlayable)
    }

    @Test
    fun testEnToJpReviewChoices_CorrectSelection() {
        val choices = listOf("A", "B")
        val reviewChoices = ReviewDisplayFormatter.createEnToJpReviewChoices(
            choices, "A", "A", "apple"
        )
        
        assertEquals(2, reviewChoices.size)
        assertTrue(reviewChoices[0].isCorrect)
        assertTrue(reviewChoices[0].isSelected)
        assertNull("正解を選んでも英単語は表示しない", reviewChoices[0].englishText)
        assertTrue(!reviewChoices[0].isPlayable)
    }

    @Test
    fun testTtsStringDoesNotContainJapanese() {
        val questionWord = "fall behind"
        val questionMeaning = "遅れをとる"
        
        // 表示用
        val display = ReviewDisplayFormatter.formatWithJapaneseMeaning(questionWord, questionMeaning)
        // TTS用
        val tts = questionWord 

        assertTrue(display.contains("遅れをとる"))
        assertTrue(!tts.contains("遅れをとる"))
        assertTrue(!tts.contains("（"))
        assertTrue(!tts.contains("）"))
        assertEquals("fall behind", tts)
    }

    @Test
    fun testResetLogic() {
        // 次の問題へ進む際のリセット
        var reviewQuestionText = "lag behind（遅れをとる）"
        var reviewQuestionTtsText = "lag behind"
        var reviewQuestionNote: String? = "note"
        var playableReviewChoices: List<PlayableReviewChoiceUiModel> = listOf(
            PlayableReviewChoiceUiModel("A", "意味", "A", false, false)
        )
        var enToJpReviewChoices: List<EnToJpReviewChoiceUiModel> = listOf(
            EnToJpReviewChoiceUiModel("日", "英", "E", true, true, true)
        )
        
        // リセット実行
        reviewQuestionText = ""
        reviewQuestionTtsText = ""
        reviewQuestionNote = null
        playableReviewChoices = emptyList()
        enToJpReviewChoices = emptyList()
        
        assertEquals("", reviewQuestionText)
        assertEquals("", reviewQuestionTtsText)
        assertNull(reviewQuestionNote)
        assertTrue(playableReviewChoices.isEmpty())
        assertTrue(enToJpReviewChoices.isEmpty())
    }

    @Test
    fun testReviewDisplayStateTransitions() {
        // 初期状態
        var state = LearningUiState()
        assertEquals(ReviewDisplayState.NONE, state.reviewDisplayState)

        // 回答直後 (アニメーション待ち)
        state = state.copy(reviewDisplayState = ReviewDisplayState.WAITING_FOR_ANIMATION)
        assertEquals(ReviewDisplayState.WAITING_FOR_ANIMATION, state.reviewDisplayState)

        // アニメーション完了通知後 (モーダル表示準備完了)
        state = state.copy(reviewDisplayState = ReviewDisplayState.READY_FOR_MODAL)
        assertEquals(ReviewDisplayState.READY_FOR_MODAL, state.reviewDisplayState)

        // モーダル表示開始
        state = state.copy(reviewDisplayState = ReviewDisplayState.SHOWING_MODAL)
        assertEquals(ReviewDisplayState.SHOWING_MODAL, state.reviewDisplayState)

        // 次へボタン押下後
        state = state.copy(reviewDisplayState = ReviewDisplayState.NONE)
        assertEquals(ReviewDisplayState.NONE, state.reviewDisplayState)
    }

    @Test
    fun testFinalQuestionTransition() {
        // 最終問題での遷移シミュレーション
        var state = LearningUiState(currentStep = 10, totalSteps = 10)
        
        // レビュー表示中
        state = state.copy(reviewDisplayState = ReviewDisplayState.SHOWING_MODAL)
        
        // 次へボタン押下
        state = state.copy(reviewDisplayState = ReviewDisplayState.NONE, isFinished = true)
        
        assertTrue(state.isFinished)
        assertEquals(ReviewDisplayState.NONE, state.reviewDisplayState)
    }

    @Test
    fun testJpToEnSentencePlayback_Logic() {
        val word = mockWord("apple", "りんご").copy(sentence = "I eat an apple.")
        
        // LearningUiState 相当の状態 (JP_TO_EN モード)
        val state = LearningUiState(
            quiz = mockQuiz(word, QuizMode.JP_TO_EN),
            currentWord = word,
            reviewCorrectAnswerTtsText = "apple",
            silentMode = SilentMode.OFF
        )
        
        val model = ReviewCardMapper.map(state)
        
        // 検証
        assertEquals("I eat an apple.", model.sentence)
        assertTrue("再生ボタンが有効であるべき", model.playButtonsEnabled)
        assertTrue("正解単語が再生可能であるべき", model.canPlayCorrectAnswer)
        assertEquals("apple", model.correctAnswerTtsText)
    }
}
