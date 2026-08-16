package com.stulab.studylockapp

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.stulab.studylockapp.databinding.ActivityLearningBinding
import com.stulab.studylockapp.learning.*
import com.stulab.studylockapp.learning.practical.PracticalTestActivity
import com.stulab.studylockapp.data.SilentMode
import com.stulab.studylockapp.data.WordEntity
import com.stulab.studylockapp.ui.PronunciationCheckActivity
import com.stulab.studylockapp.ui.CharacterSelectActivity
import com.stulab.studylockapp.ui.alert.AppDialogHelper
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 学習画面のActivity
 */
class LearningActivity : AppCompatActivity(), QuizUiProvider {

    private lateinit var binding: ActivityLearningBinding
    private lateinit var animationManager: AnimationManager
    private lateinit var soundEffectManager: SoundEffectManager
    private lateinit var ttsController: LearningTtsController
    
    private var currentQuizId: String? = null
    private var currentBaseQuestionSizeSp: Float = 16f
    private var currentQuestionScale: Float = 1.0f

    // 待機画面用キャッシュ
    private var cachedWaitingMessage: String? = null
    private var cachedWaitingEmotionId: String? = null
    private var cachedWaitingCharId: String? = null

    // 選択肢表示状態管理 (Persistence は ViewModel/State 側で実施)
    private var choicesRevealedForCurrentQuiz: Boolean = true

    private val viewModel: LearningViewModel by viewModels {
        LearningViewModelFactory(this)
    }

    private val choiceButtons by lazy {
        listOf(
            binding.buttonChoice1,
            binding.buttonChoice2,
            binding.buttonChoice3,
            binding.buttonChoice4
        )
    }

    private val pronunciationLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
            viewModel.refreshPoints()
        }

    private val practicalTestLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
            // 実践テスト終了後は、自動的に次の単語学習セッションを開始する
            viewModel.startNextSessionAfterPracticalTest()
        }

    // Rendererから安全にアクセスするためのブリッジメソッド
    fun exposeBinding(): ActivityLearningBinding = binding
    fun exposeLearningViewModel(): LearningViewModel = viewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setupStatusBarIcons()

        binding = ActivityLearningBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        animationManager = AnimationManager(binding)
        soundEffectManager = SoundEffectManager(this)
        ttsController = LearningTtsController(this)

        observeViewModel()

        if (savedInstanceState == null) {
            viewModel.loadInitialQuiz()
        }

        setupListeners()
    }

    /**
     * ステータスバーのアイコン（時計、電池、電波）を濃い色に設定します。
     * 背景が明るい色や水玉模様でも視認性を確保するためです。
     */
    private fun setupStatusBarIcons() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
        }
    }

    private fun setupListeners() {
        binding.layoutJourneyHeader.layoutModePill.rootModePill.setOnClickListener {
            animationManager.playModeToggleClick(it)
            viewModel.toggleSilentMode()
        }

        // 補助操作バー: サウンド設定
        binding.buttonSoundSettingsAssist.setOnClickListener {
            val intent = Intent(this, SoundSettingsActivity::class.java)
            startActivity(intent)
        }

        binding.layoutReviewCard.buttonNextQuestion.setOnClickListener {
            viewModel.onNextAfterReview()
        }

        binding.layoutReviewCard.buttonPlayQuestionInline.setOnClickListener {
            viewModel.requestAudioPlayback()
        }

        binding.layoutReviewCard.buttonPlayReviewSentence.setOnClickListener {
            viewModel.uiState.value.quiz?.word?.sentence?.let {
                viewModel.requestAudioPlayback(it) 
            }
        }
        
        binding.layoutReviewCard.includeWrong.buttonPlay.setOnClickListener {
            viewModel.uiState.value.reviewUserAnswerTtsText.let {
                if (it.isNotEmpty()) viewModel.requestAudioPlayback(it)
            }
        }

        binding.layoutReviewCard.includeCorrect.buttonPlay.setOnClickListener {
            viewModel.uiState.value.reviewCorrectAnswerTtsText.let {
                if (it.isNotEmpty()) viewModel.requestAudioPlayback(it)
            }
        }

        // 選択肢コンテナ内の「わからない」ボタン
        binding.buttonUnknownAnswer.setOnClickListener {
            if (!viewModel.uiState.value.isAnswering && !viewModel.uiState.value.isReviewing) {
                viewModel.submitUnknownAnswer()
            }
        }

        // 補助操作バー: 他級復習トグル
        binding.buttonToggleOtherGradeReviews.setOnClickListener {
            viewModel.setIncludeOtherGradeReviews(!viewModel.uiState.value.includeOtherGradeReviews)
        }

        // 補助操作バー: 選択肢ON/OFF
        binding.buttonToggleChoices.setOnClickListener {
            toggleChoicesInitialVisibility()
        }

        // カバーカードの「回答する」ボタン
        binding.buttonRevealChoices.setOnClickListener {
            revealChoicesForCurrentQuiz()
        }

        // 補助操作バー: 再生
        binding.buttonReplayQuestionAudio.setOnClickListener {
            viewModel.requestAudioPlayback()
        }

        binding.layoutEmptyState.buttonEmptyClose.setOnClickListener {
            finish()
        }

        binding.layoutEmptyState.buttonSwitchToBalance.setOnClickListener {
            viewModel.toggleSilentMode()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collectLatest { state ->
                        updateUi(state)
                    }
                }
                launch {
                    viewModel.uiEvent.collect { event ->
                        handleEvent(event)
                    }
                }
            }
        }
    }

    private fun updateUi(state: LearningUiState) {
        ModePillBinder.bind(binding.layoutJourneyHeader.layoutModePill, ModePillMapper.map(state.silentMode))
        JourneyHeaderBinder.bind(binding.layoutJourneyHeader, JourneyHeaderMapper.map(state))

        // 設定の見た目を更新
        updateOtherGradeReviewButton(state.includeOtherGradeReviews)
        updateToggleChoicesButton(state.choicesInitiallyVisible)

        // モーダルレビュー表示制御
        if (state.reviewDisplayState == ReviewDisplayState.READY_FOR_MODAL) {
            showReviewModal()
        }

        // インライン表示は通常学習では廃止 (モーダルへ移行)
        binding.layoutReviewCard.rootReviewCardContent.visibility = View.GONE
        binding.cardQuestion.visibility = View.VISIBLE
        
        state.quiz?.let { quiz ->
            updateAssistButtonsForQuiz(quiz)
        }

        // プログレスバーの更新 (アニメーション中でない場合のみ即時反映)
        if (state.reviewDisplayState == ReviewDisplayState.NONE) {
            binding.progressHorizontal.progress = state.progress
        }
        binding.textProgressPercent.text = getString(R.string.label_progress_step, state.currentStep, state.totalSteps)

        renderQuizIfNeeded(state)
        updateChoiceCoverVisibility(state)

        // 待機画面キャッシュのクリア制御
        if (state.emptyState !is LearningEmptyState.NoReviewAvailable) {
            cachedWaitingMessage = null
            cachedWaitingEmotionId = null
            cachedWaitingCharId = null
        }

        // 空状態の表示制御
        if (state.emptyState != null) {
            binding.cardQuestion.visibility = View.GONE
            binding.layoutReviewCard.rootReviewCardContent.visibility = View.GONE
            binding.layoutAssistButtons.visibility = View.GONE
            binding.layoutEmptyState.rootEmptyCard.visibility = View.VISIBLE

            val (message, charResId) = when (state.emptyState) {
                is LearningEmptyState.DailyGoalMet -> {
                    val result = com.stulab.studylockapp.data.notification.CharacterLines.getDailyStableLineWithEmotion(
                        com.stulab.studylockapp.data.notification.StudyCharacter.fromId(state.selectedCharacterId),
                        com.stulab.studylockapp.data.notification.NotificationContext.DAILY_GOAL_MET_REST,
                        name = state.userName
                    )
                    result.text to com.stulab.studylockapp.ui.CharacterDisplayUtils.getMiniIconDrawable(this, state.selectedCharacterId, result.emotion.id)
                }
                is LearningEmptyState.NoReviewAvailable -> {
                    // キャッシュが有効か確認
                    if (cachedWaitingMessage == null || cachedWaitingCharId != state.selectedCharacterId) {
                        val result = com.stulab.studylockapp.data.notification.CharacterLines.getLineWithEmotion(
                            com.stulab.studylockapp.data.notification.StudyCharacter.fromId(state.selectedCharacterId),
                            com.stulab.studylockapp.data.notification.NotificationContext.WAITING_FOR_REVIEW,
                            name = state.userName
                        )
                        cachedWaitingMessage = result.text
                        cachedWaitingEmotionId = result.emotion.id
                        cachedWaitingCharId = state.selectedCharacterId
                    }
                    
                    cachedWaitingMessage!! to com.stulab.studylockapp.ui.CharacterDisplayUtils.getMiniIconDrawable(this, state.selectedCharacterId, cachedWaitingEmotionId!!)
                }
                is LearningEmptyState.SilentModeFinishedButNormalAvailable -> {
                    getString(R.string.empty_silent_mode_finished) to com.stulab.studylockapp.ui.CharacterDisplayUtils.getJoyDrawable(this, state.selectedCharacterId)
                }
            }
            
            binding.layoutEmptyState.textEmptyMessage.text = message
            binding.layoutEmptyState.imageEmptyCharacter.setImageResource(charResId)

            if (state.emptyState is LearningEmptyState.SilentModeFinishedButNormalAvailable) {
                binding.layoutEmptyState.buttonEmptyClose.visibility = View.GONE
                binding.layoutEmptyState.layoutSwitchMode.visibility = View.VISIBLE
            } else {
                binding.layoutEmptyState.buttonEmptyClose.visibility = View.VISIBLE
                binding.layoutEmptyState.layoutSwitchMode.visibility = View.GONE
            }

            // カウントダウンタイマーの表示更新
            if (state.countdownText != null) {
                binding.layoutEmptyState.textCountdownTimer.visibility = View.VISIBLE
                binding.layoutEmptyState.textCountdownTimer.text = state.countdownText
            } else {
                binding.layoutEmptyState.textCountdownTimer.visibility = View.GONE
            }
        } else {
            binding.layoutEmptyState.rootEmptyCard.visibility = View.GONE
        }
    }

    private fun showReviewModal() {
        if (supportFragmentManager.findFragmentByTag(ReviewModalFragment.TAG) != null) return
        
        ReviewModalFragment().show(supportFragmentManager, ReviewModalFragment.TAG)
        viewModel.onReviewModalShown()
    }

    private fun updateOtherGradeReviewButton(enabled: Boolean) {
        binding.buttonToggleOtherGradeReviews.apply {
            text = if (enabled) "他級ON" else "他級OFF"
            
            val bgColor = ContextCompat.getColor(context, if (enabled) R.color.mustard_soft else R.color.white)
            val strokeColor = ContextCompat.getColor(context, if (enabled) R.color.mustard_accent else R.color.outline)
            val textColor = ContextCompat.getColor(context, if (enabled) R.color.unknown_text else R.color.text_sub)

            backgroundTintList = ColorStateList.valueOf(bgColor)
            this.strokeColor = ColorStateList.valueOf(strokeColor)
            setTextColor(textColor)
            iconTint = ColorStateList.valueOf(textColor)
        }
    }

    private fun updateAssistButtonsForQuiz(quiz: QuizData) {
        val mode = quiz.mode
        if (mode == QuizMode.SENTENCE_SORT) {
            binding.layoutAssistButtons.visibility = View.GONE
            binding.layoutAnswerAssistButtons.visibility = View.GONE
        } else {
            binding.layoutAssistButtons.visibility = View.VISIBLE
            binding.layoutAnswerAssistButtons.visibility = View.VISIBLE
            
            updateReplayButtonState(quiz)
        }
    }

    private fun updateReplayButtonState(quiz: QuizData) {
        val state = viewModel.uiState.value
        val isSilent = state.silentMode == SilentMode.ON
        var canReplay = canReplayQuestionAudio(quiz.mode) && !isSilent
        
        val textToPlay = when (quiz.mode) {
            QuizMode.LISTEN_FILL_BLANK -> quiz.word.sentence
            else -> quiz.word.word
        }

        if (canReplay && sanitizeForTts(textToPlay).isEmpty()) {
            canReplay = false
        }

        binding.buttonReplayQuestionAudio.apply {
            isEnabled = canReplay
            isClickable = canReplay
            isFocusable = canReplay
            alpha = if (canReplay) 1.0f else 0.4f

            val strokeCol = ContextCompat.getColor(context, if (canReplay) R.color.navy_primary else R.color.assist_disabled)
            val iconCol = ContextCompat.getColor(context, if (canReplay) R.color.navy_primary else R.color.assist_disabled)
            val bgCol = ContextCompat.getColor(context, if (canReplay) R.color.white else R.color.assist_disabled_bg)

            backgroundTintList = ColorStateList.valueOf(bgCol)
            strokeColor = ColorStateList.valueOf(strokeCol)
            iconTint = ColorStateList.valueOf(iconCol)
            setTextColor(iconCol)

            contentDescription = if (isSilent) {
                "サイレントモード中"
            } else if (canReplay) {
                "$textToPlay を再生"
            } else {
                "音声再生不可"
            }
        }
    }

    private fun canReplayQuestionAudio(mode: QuizMode): Boolean {
        return when (mode) {
            QuizMode.EN_TO_JP,
            QuizMode.LISTEN_EN,
            QuizMode.LISTEN_FILL_BLANK,
            QuizMode.SYNONYM_PICK,
            QuizMode.ANTONYM_PICK -> true
            else -> false
        }
    }

    private fun renderQuizIfNeeded(state: LearningUiState) {
        state.quiz?.let { quiz ->
            if (currentQuizId != quiz.id) {
                currentQuizId = quiz.id
                resetUiForNewQuiz(state)
                RendererFactory.getRenderer(quiz.mode).render(this, quiz)
            }
        }
    }

    private fun resetUiForNewQuiz(state: LearningUiState) {
        ttsController.stop()
        resetAllChoiceButtons()
        resetAssistButtons()
        
        // 選択肢表示状態のリセット (新しい問題開始時に保存設定を読み込む)
        choicesRevealedForCurrentQuiz = state.choicesInitiallyVisible
        updateChoiceCoverVisibility(state)

        binding.choicesContainer.visibility = View.VISIBLE
        binding.layoutSortContainer.visibility = View.GONE
        binding.flexboxAnswer.removeAllViews()
        binding.flexboxCandidates.removeAllViews()
        setQuestionBodyTextScale(1.0f)
    }

    private fun resetAssistButtons() {
        binding.buttonUnknownAnswer.apply {
            isEnabled = true
            alpha = 1.0f
        }
        binding.buttonToggleChoices.apply {
            isEnabled = true
            alpha = 1.0f
        }
    }

    private fun resetChoiceButton(btn: MaterialButton) {
        btn.apply {
            backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.white)
            )
            setTextColor(
                ContextCompat.getColor(context, R.color.navy_primary)
            )
            strokeColor = ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.navy_primary)
            )

            alpha = 1.0f
            scaleX = 1.0f
            scaleY = 1.0f
            translationX = 0f
            translationY = 0f
            isEnabled = true
            isClickable = true
            clearAnimation()
        }
    }

    private fun resetAllChoiceButtons() { choiceButtons.forEach { resetChoiceButton(it) } }

    private fun handleEvent(event: LearningUiEvent) {
        when (event) {
            is LearningUiEvent.NoAvailableWords -> {
                Toast.makeText(this, "現在学習出来る単語がありません", Toast.LENGTH_LONG).show()
                finish()
            }
            is LearningUiEvent.ShowCorrect -> {
                val state = viewModel.uiState.value
                if (state.silentMode == SilentMode.OFF) {
                    soundEffectManager.playCorrect()

                    // SEと重ならないよう、少し遅らせて音声を自動再生
                    lifecycleScope.launch {
                        kotlinx.coroutines.delay(600) // 0.6秒待機
                        val audioText = when (state.quiz?.mode) {
                            QuizMode.EN_TO_JP -> state.currentWord?.word
                            QuizMode.JP_TO_EN -> state.currentWord?.word
                            QuizMode.FILL_BLANK -> state.currentWord?.sentence
                            QuizMode.SENTENCE_SORT -> state.currentWord?.sentence // 英文並び替えで文章を再生
                            QuizMode.LISTEN_FILL_BLANK -> state.currentWord?.sentence
                            else -> state.currentWord?.word
                        }
                        audioText?.let { ttsController.speak(it) }
                    }
                }
                if (state.quiz?.mode == QuizMode.SENTENCE_SORT) {
                    viewModel.notifyAnimationFinished()
                } else {
                    val correctBtn = choiceButtons.find { it.text == event.answer }
                    animationManager.playCorrectSequence(
                        button = correctBtn,
                        point = event.gainedPoints,
                        tierChanged = event.tierChanged,
                        tierLabel = state.currentTier.label,
                        targetProgress = state.progress
                    ) {
                        viewModel.notifyAnimationFinished()
                    }
                }
            }
            is LearningUiEvent.ShowWrong -> {
                val state = viewModel.uiState.value
                if (!event.isUnknown && state.silentMode == SilentMode.OFF) {
                    soundEffectManager.playWrong()

                    // 不正解時もSEの後に音声を自動再生
                    lifecycleScope.launch {
                        kotlinx.coroutines.delay(800) // 不正解SEは少し長めなので0.8秒待機
                        val audioText = when (state.quiz?.mode) {
                            QuizMode.FILL_BLANK -> state.currentWord?.sentence
                            QuizMode.SENTENCE_SORT -> state.currentWord?.sentence // 英文並び替えで文章を再生
                            QuizMode.LISTEN_FILL_BLANK -> state.currentWord?.sentence
                            else -> state.currentWord?.word
                        }
                        audioText?.let { ttsController.speak(it) }
                    }
                }
                if (state.quiz?.mode == QuizMode.SENTENCE_SORT) {
                    viewModel.notifyAnimationFinished()
                } else {
                    val selectedBtn = choiceButtons.find { it.text == event.selected }
                    val correctBtn = choiceButtons.find { it.text == event.correct }
                    
                    if (event.isUnknown) {
                        binding.buttonUnknownAnswer.isEnabled = false
                        viewModel.notifyAnimationFinished()
                    } else {
                        animationManager.playWrongSequence(selectedBtn, correctBtn) {
                            viewModel.notifyAnimationFinished()
                        }
                    }
                }
            }
            is LearningUiEvent.PlayAudio -> {
                if (viewModel.uiState.value.silentMode == SilentMode.OFF) {
                    ttsController.speak(event.text)
                }
            }
            is LearningUiEvent.QuizFinished -> finish()
            is LearningUiEvent.NavigateToPracticalTest -> {
                val intent = Intent(this, PracticalTestActivity::class.java).apply {
                    putExtra(PracticalTestActivity.EXTRA_GRADE, event.grade)
                }
                practicalTestLauncher.launch(intent)
            }
            is LearningUiEvent.ShowMasteryBadge -> animationManager.playTierUpAnimation(event.tier.label)
            is LearningUiEvent.ShowSilentModeExplanation -> showSilentModeExplanationDialog()
            is LearningUiEvent.ShowFlyingLevelUp -> animationManager.playFlyingLevelUp(event.oldLevel, event.newLevel)
            is LearningUiEvent.ShowBasicMasterCelebration -> animationManager.playMasterCelebration(isLongTerm = false)
            is LearningUiEvent.ShowLongTermMasterCelebration -> animationManager.playMasterCelebration(isLongTerm = true)
            is LearningUiEvent.ShowLevel5BonusInduction -> showLevel5BonusInductionDialog(event.word)
            
            is LearningUiEvent.ShowGrandCelebration -> {
                handleGrandCelebration(event.characterId, event.characterName, event.message)
            }
            is LearningUiEvent.ShowNewCharacterAvailable -> {
                showNewCharacterDialog(event.characterName)
            }
        }
    }

    private fun showNewCharacterDialog(characterName: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("🎉 新しいパートナー解放！")
            .setMessage("目標達成日数が節目に到達しました！\n新しいパートナー「${characterName}」がショップに登場しました。")
            .setPositiveButton("パートナー画面へ") { _, _ ->
                val intent = Intent(this, CharacterSelectActivity::class.java)
                startActivity(intent)
            }
            .setNegativeButton("あとで", null)
            .show()
    }

    private fun handleGrandCelebration(charId: String, charName: String, message: String, emotionId: String? = null) {
        animationManager.playGoalGrandCelebration()
        
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_goal_celebration, null)
        val imageChar = dialogView.findViewById<ImageView>(R.id.image_celebration_character)
        val textMsg = dialogView.findViewById<TextView>(R.id.text_celebration_message)
        val btnClose = dialogView.findViewById<View>(R.id.button_celebration_close)

        // 画像リソースの決定: emotionId があればそれを使用、なければ既定の pleasure
        val resId = if (emotionId != null) {
            com.stulab.studylockapp.ui.CharacterDisplayUtils.getNotificationIconDrawable(this, charId, emotionId)
        } else {
            com.stulab.studylockapp.ui.CharacterDisplayUtils.getPleasureDrawable(this, charId)
        }

        imageChar.setImageResource(resId)
        
        textMsg.text = message
        
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showSilentModeExplanationDialog() {
        AppDialogHelper.showInfo(
            context = this,
            title = getString(R.string.silent_mode_explanation_title),
            message = getString(R.string.silent_mode_explanation_body),
            positiveText = getString(R.string.ok)
        )
    }

    private fun showLevel5BonusInductionDialog(word: WordEntity) {
        AppDialogHelper.showConfirm(
            context = this,
            title = "🎉 LV5達成！",
            message = "発音チェックに挑戦すると、🎙 発音OKバッジと10ptがもらえます。\nあとで履歴画面から挑戦することもできます。",
            positiveText = "発音チェックする",
            negativeText = "あとで",
            onPositive = {
                val intent = Intent(this, PronunciationCheckActivity::class.java).apply {
                    putExtra("WORD_ID", word.no.toLong())
                    putExtra("WORD_TEXT", word.word)
                    putExtra("WORD_MEANING", word.japanese)
                    putExtra("WORD_SENTENCE", word.sentence)
                    putExtra("WORD_SENTENCE_JA", word.japaneseSentence)
                    putExtra("WORD_GRADE", word.grade.toString())
                    putExtra("CHECK_TYPE", "word")
                    putExtra("FROM_LEVEL5_BONUS", true)
                }
                pronunciationLauncher.launch(intent)
            }
        )
    }

    override fun getProviderString(resId: Int): String {
        return getString(resId)
    }

    override fun getProviderColor(resId: Int): Int {
        return ContextCompat.getColor(this, resId)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun showBasicQuiz(title: CharSequence, body: String, choices: List<String>) {
        binding.textQuestionTitle.text = title

        // 言語判定（日本語が含まれるか）
        val hasJapanese = body.any { it.code in 0x3040..0x309F || it.code in 0x30A0..0x30FF || it.code in 0x4E00..0x9FFF }
        currentBaseQuestionSizeSp = if (hasJapanese) 15f else 16f
        updateQuestionBodyTextSize()

        binding.textQuestionBody.text = body
        
        viewModel.uiState.value.let { state ->
            binding.textQuestionGradeBadge.text = state.wordGradeName
            binding.cardQuestionGradeBadge.visibility = View.VISIBLE
        }

        binding.choicesContainer.visibility = View.VISIBLE
        binding.layoutSortContainer.visibility = View.GONE
        
        choiceButtons.forEachIndexed { i, btn ->
            if (i < choices.size) {
                val choice = choices[i]
                btn.text = choice
                btn.visibility = View.VISIBLE
                
                // 選択肢の言語判定
                val choiceHasJa = choice.any { it.code in 0x3040..0x309F || it.code in 0x30A0..0x30FF || it.code in 0x4E00..0x9FFF }
                btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (choiceHasJa) 14f else 16f)

                btn.setOnClickListener {
                    if (!viewModel.uiState.value.isAnswering && !viewModel.uiState.value.isReviewing) {
                        viewModel.submitAnswer(btn.text.toString())
                    }
                }
            } else {
                btn.visibility = View.GONE
            }
        }
        
        // 「わからない」ボタンは常に末尾に表示（showBasicQuiz時）
        binding.buttonUnknownAnswer.visibility = View.VISIBLE
        updateChoiceCoverVisibility(viewModel.uiState.value)
    }

    override fun setQuestionBodyTextScale(scale: Float) {
        currentQuestionScale = scale
        updateQuestionBodyTextSize()
    }

    private fun updateQuestionBodyTextSize() {
        binding.textQuestionBody.setTextSize(TypedValue.COMPLEX_UNIT_SP, currentBaseQuestionSizeSp * currentQuestionScale)
    }

    override fun playAudio(text: String) {
        if (viewModel.uiState.value.silentMode == SilentMode.OFF) {
            ttsController.speak(text)
        }
    }

    // --- 選択肢カバー関連 of theメソッド ---

    private fun toggleChoicesInitialVisibility() {
        val nextValue = !viewModel.uiState.value.choicesInitiallyVisible
        viewModel.setChoicesInitiallyVisible(nextValue)
        
        // トグルした瞬間に現在のクイズの状態も同期させる
        choicesRevealedForCurrentQuiz = nextValue
        updateChoiceCoverVisibility(viewModel.uiState.value)
    }

    private fun revealChoicesForCurrentQuiz() {
        choicesRevealedForCurrentQuiz = true
        updateChoiceCoverVisibility(viewModel.uiState.value)
    }

    private fun updateChoiceCoverVisibility(state: LearningUiState) {
        val shouldShowCover = isChoiceCoverAvailableForCurrentQuiz(state)
        binding.choiceCoverCard.visibility = if (shouldShowCover) View.VISIBLE else View.GONE
        
        // カバー表示中は4択ボタンを無効化
        choiceButtons.forEach { it.isEnabled = !shouldShowCover }
    }

    private fun updateToggleChoicesButton(enabled: Boolean) {
        binding.buttonToggleChoices.apply {
            text = if (enabled) "選択肢ON" else "選択肢OFF"
            setIconResource(if (enabled) R.drawable.ic_visibility_24 else R.drawable.ic_visibility_off_24)

            val bgColor = ContextCompat.getColor(context, if (enabled) R.color.navy_soft else R.color.white)
            val strokeColor = ContextCompat.getColor(context, if (enabled) R.color.navy_primary else R.color.outline)
            val textColor = ContextCompat.getColor(context, if (enabled) R.color.navy_primary else R.color.text_sub)

            backgroundTintList = ColorStateList.valueOf(bgColor)
            this.strokeColor = ColorStateList.valueOf(strokeColor)
            setTextColor(textColor)
            iconTint = ColorStateList.valueOf(textColor)
        }
    }

    private fun isChoiceCoverAvailableForCurrentQuiz(state: LearningUiState): Boolean {
        val mode = state.quiz?.mode
        
        // カバーを表示する条件：
        // 1. 4択系問題である (SENTENCE_SORTではない、かつ選択肢が存在する)
        // 2. choicesInitiallyVisible == false
        // 3. choicesRevealedForCurrentQuiz == false
        // 4. レビュー中ではない
        // 5. 回答中ではない
        
        val isChoiceQuiz = mode != null && mode != QuizMode.SENTENCE_SORT
        return isChoiceQuiz && 
                !state.choicesInitiallyVisible &&
                !choicesRevealedForCurrentQuiz && 
                !state.isReviewing && 
                !state.isAnswering
    }

    override fun onDestroy() {
        ttsController.release()
        soundEffectManager.release()
        super.onDestroy()
    }
}
