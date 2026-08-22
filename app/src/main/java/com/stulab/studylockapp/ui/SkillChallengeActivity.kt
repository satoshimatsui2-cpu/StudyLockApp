package com.stulab.studylockapp.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.stulab.studylockapp.R
import com.stulab.studylockapp.data.AppDatabase
import com.stulab.studylockapp.data.AppSettings
import com.stulab.studylockapp.data.ChallengePointManager
import com.stulab.studylockapp.data.ChallengeRepository
import com.stulab.studylockapp.data.SilentMode
import com.stulab.studylockapp.databinding.ActivitySkillChallengeBinding
import com.stulab.studylockapp.ui.alert.AppDialogHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SkillChallengeActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySkillChallengeBinding
    private val viewModel: SkillChallengeViewModel by viewModels {
        SkillChallengeViewModelFactory(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySkillChallengeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        observeViewModel()
    }

    private fun setupListeners() {
        binding.buttonBack.setOnClickListener { finish() }

        binding.btnStartPronunciation.setOnClickListener {
            val state = viewModel.uiState.value
            if (state.hasActivePronunciation) {
                AppDialogHelper.showConfirm(
                    context = this,
                    title = "発音チャレンジを再開しますか？",
                    message = "前回のチャレンジの続きから再開します。CPは消費されません。",
                    positiveText = "再開する",
                    negativeText = "キャンセル",
                    onPositive = { executeStartPronunciation() }
                )
            } else if (state.silentMode == SilentMode.ON) {
                AppDialogHelper.showInfo(
                    context = this,
                    title = "サイレントモード中です",
                    message = "発音チェックは、声を出せる環境で利用できます。",
                    positiveText = "OK"
                )
            } else if (state.challengePoints < 10) {
                showInsufficientCPDialog("発音チャレンジ", state.challengePoints)
            } else {
                // 新規開始の候補抽出
                viewModel.startPronunciationChallenge { result ->
                    handleStartResultForPronunciation(result)
                }
            }
        }

        binding.btnStartSpelling.setOnClickListener {
            val state = viewModel.uiState.value
            Log.d("SkillChallenge", "Spelling button clicked. CP=${state.challengePoints}, hasActiveSpelling=${state.hasActiveSpelling}")

            if (state.hasActiveSpelling) {
                // 再開ダイアログ
                AppDialogHelper.showConfirm(
                    context = this,
                    title = "スペルチャレンジを再開しますか？",
                    message = "前回のチャレンジの続きから再開します。CPは消費されません。",
                    positiveText = "再開する",
                    negativeText = "キャンセル",
                    onPositive = { executeStartSpelling() }
                )
            } else if (state.challengePoints < 10) {
                showInsufficientCPDialog("スペルチャレンジ", state.challengePoints)
            } else {
                // 開始確認ダイアログ
                AppDialogHelper.showConfirm(
                    context = this,
                    title = "スペルチャレンジを開始しますか？",
                    message = "10 CPを消費して、5語のスペルチェックに挑戦します。",
                    positiveText = "10 CPを消費して挑戦",
                    negativeText = "キャンセル",
                    onPositive = { executeStartSpelling() }
                )
            }
        }
    }

    private fun showInsufficientCPDialog(title: String, currentCP: Int) {
        val lack = 10 - currentCP
        AppDialogHelper.showInfo(
            context = this,
            title = "CPが足りません",
            message = "${title}には10 CP必要です。現在のCP：${currentCP} CP\nあと${lack} CPで挑戦できます",
            positiveText = "OK"
        )
    }

    private fun handleStartResultForPronunciation(result: ChallengeStartResult) {
        when (result) {
            is ChallengeStartResult.Success -> {
                if (result.isResume) {
                    executeStartPronunciation(result.ids)
                } else {
                    showPronunciationConfirmDialog(result.ids)
                }
            }
            is ChallengeStartResult.InsufficientCP -> {
                showInsufficientCPDialog("発音チャレンジ", viewModel.uiState.value.challengePoints)
            }
            is ChallengeStartResult.NoWordsAvailable -> {
                AppDialogHelper.showInfo(
                    context = this,
                    title = "発音チャレンジを開始できません",
                    message = "発音チャレンジに必要な単語が足りません。通常学習を進めてください。",
                    positiveText = "OK"
                )
            }
            is ChallengeStartResult.Error -> {
                Toast.makeText(this, "エラーが発生しました。時間をおいて再度お試しください。", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showPronunciationConfirmDialog(ids: List<Long>) {
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(this@SkillChallengeActivity)
            val words = withContext(Dispatchers.IO) { db.wordDao().getWordsByIds(ids.map { it.toInt() }) }
            val wordListStr = words.joinToString(" / ") { it.word }

            AppDialogHelper.showConfirm(
                context = this@SkillChallengeActivity,
                title = "発音チャレンジを開始しますか？",
                message = "10 CPを消費して、次の3単語に挑戦します。\n\n$wordListStr",
                positiveText = "10 CPを消費して挑戦",
                negativeText = "キャンセル",
                onPositive = {
                    if (viewModel.confirmStartPronunciation(ids)) {
                        executeStartPronunciation(ids)
                    } else {
                        showInsufficientCPDialog("発音チャレンジ", viewModel.uiState.value.challengePoints)
                    }
                }
            )
        }
    }

    private fun executeStartPronunciation(ids: List<Long>? = null) {
        // ViewModel側で既にセットされている想定だが、引数でも渡せるように
        val intent = Intent(this, PronunciationChallengeActivity::class.java).apply {
            if (ids != null) putExtra("WORD_IDS", ids.toLongArray())
        }
        startActivity(intent)
    }

    private fun executeStartSpelling() {
        binding.btnStartSpelling.isEnabled = false
        viewModel.startSpellingChallenge { result ->
            binding.btnStartSpelling.isEnabled = true
            handleStartResult(result) { ids ->
                val intent = Intent(this, SpellingCheckActivity::class.java).apply {
                    putExtra("WORD_IDS", ids.toLongArray())
                    putExtra("IS_SESSION", true)
                }
                startActivity(intent)
            }
        }
    }

    private fun handleStartResult(result: ChallengeStartResult, onSuccess: (List<Long>) -> Unit) {
        when (result) {
            is ChallengeStartResult.Success -> onSuccess(result.ids)
            is ChallengeStartResult.InsufficientCP -> {
                showInsufficientCPDialog("スキルチャレンジ", viewModel.uiState.value.challengePoints)
            }
            is ChallengeStartResult.NoWordsAvailable -> {
                Toast.makeText(this, "現在挑戦できる単語がありません。通常学習を進めてください。", Toast.LENGTH_LONG).show()
            }
            is ChallengeStartResult.Error -> {
                Toast.makeText(this, "エラーが発生しました。時間をおいて再度お試しください。", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                binding.textCpBalance.text = getString(com.stulab.studylockapp.R.string.label_cp_balance, state.challengePoints)
                
                // Pronunciation
                binding.btnStartPronunciation.isEnabled = true
                
                binding.textPronunciationWarning.visibility = if (state.silentMode == SilentMode.ON) View.VISIBLE else View.GONE
                binding.textPronunciationCpLack.visibility = if (state.challengePoints < 10 && !state.hasActivePronunciation && state.silentMode == SilentMode.OFF) View.VISIBLE else View.GONE
                if (state.challengePoints < 10) {
                    binding.textPronunciationCpLack.text = getString(com.stulab.studylockapp.R.string.challenge_points_lack, 10 - state.challengePoints)
                }
                
                binding.textPronunciationEligible.text = getString(R.string.label_eligible_words, state.pronunciationEligible)
                binding.textPronunciationMastered.text = getString(R.string.label_mastered_words, state.pronunciationMastered)
                binding.textPronunciationZeroHint.visibility = if (state.pronunciationEligible == 0) View.VISIBLE else View.GONE

                // Spelling
                binding.btnStartSpelling.isEnabled = true
                binding.textSpellingCpLack.visibility = if (state.challengePoints < 10 && !state.hasActiveSpelling) View.VISIBLE else View.GONE
                if (state.challengePoints < 10) {
                    binding.textSpellingCpLack.text = getString(com.stulab.studylockapp.R.string.challenge_points_lack, 10 - state.challengePoints)
                }

                binding.textSpellingEligible.text = getString(R.string.label_eligible_words, state.spellingEligible)
                binding.textSpellingMastered.text = getString(R.string.label_mastered_words, state.spellingMastered)
                binding.textSpellingZeroHint.visibility = if (state.spellingEligible == 0) View.VISIBLE else View.GONE
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }
}

data class SkillChallengeUiState(
    val challengePoints: Int = 0,
    val silentMode: SilentMode = SilentMode.OFF,
    val hasActivePronunciation: Boolean = false,
    val hasActiveSpelling: Boolean = false,
    val pronunciationEligible: Int = 0,
    val pronunciationMastered: Int = 0,
    val spellingEligible: Int = 0,
    val spellingMastered: Int = 0
)

sealed class ChallengeStartResult {
    data class Success(val ids: List<Long>, val isResume: Boolean) : ChallengeStartResult()
    object InsufficientCP : ChallengeStartResult()
    object NoWordsAvailable : ChallengeStartResult()
    object Error : ChallengeStartResult()
}

class SkillChallengeViewModel(
    applicationContext: android.content.Context,
    private val cpManager: ChallengePointManager,
    private val repository: ChallengeRepository,
    private val appSettings: AppSettings
) : ViewModel() {

    private val _uiState = MutableStateFlow(SkillChallengeUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            cpManager.getCPFlow().collectLatest { cp ->
                _uiState.update { it.copy(challengePoints = cp) }
            }
        }
        viewModelScope.launch {
            // Observe silent mode or other settings
            _uiState.update { it.copy(silentMode = appSettings.silentMode) }
        }

        // Observe progress counts
        val db = AppDatabase.getInstance(applicationContext)
        val gradeInt = appSettings.safeLearningGrade.toInt()

        viewModelScope.launch {
            db.voiceCheckDao().countEligiblePronunciationWordsFlow(gradeInt).collectLatest { count ->
                _uiState.update { it.copy(pronunciationEligible = count) }
            }
        }
        viewModelScope.launch {
            db.voiceCheckDao().countPassedPronunciationWordsFlow(gradeInt).collectLatest { count ->
                _uiState.update { it.copy(pronunciationMastered = count) }
            }
        }
        viewModelScope.launch {
            db.spellingProgressDao().countEligibleSpellingWordsFlow(gradeInt).collectLatest { count ->
                _uiState.update { it.copy(spellingEligible = count) }
            }
        }
        viewModelScope.launch {
            db.spellingProgressDao().countPassedSpellingWordsFlow(gradeInt).collectLatest { count ->
                _uiState.update { it.copy(spellingMastered = count) }
            }
        }
    }

    fun refresh() {
        val activePron = appSettings.activePronunciationChallengeIds != null
        val activeSpell = appSettings.activeSpellingChallengeIds != null && appSettings.spellChallengeStatus == "IN_PROGRESS"

        _uiState.update { it.copy(
            silentMode = appSettings.silentMode,
            hasActivePronunciation = activePron,
            hasActiveSpelling = activeSpell
        ) }
    }

    fun startPronunciationChallenge(onResult: (ChallengeStartResult) -> Unit) {
        val activeIds: List<Long>? = appSettings.activePronunciationChallengeIds
        val status = appSettings.pronChallengeStatus
        val index = appSettings.pronChallengeIndex
        val currentCP = cpManager.getCP()

        Log.d("SkillChallenge", "Pron Start attempt: CP=$currentCP, Status=$status, Index=$index, ActiveIdsCount=${activeIds?.size}")

        // 再開判定 (件数3、重複なし、進行中、インデックス範囲内)
        if (activeIds != null && activeIds.size == 3 && activeIds.distinct().size == 3 &&
            status == "IN_PROGRESS" && index in 0..2) {
            Log.d("SkillChallenge", "Resuming session: IDs=$activeIds, Index=$index")
            onResult(ChallengeStartResult.Success(activeIds, isResume = true))
            return
        }

        // 不正なデータまたは終了済みの場合は掃除
        Log.d("SkillChallenge", "Clearing session for new start. oldStatus=$status")
        appSettings.clearPronunciationChallenge()

        if (currentCP < 10) {
            onResult(ChallengeStartResult.InsufficientCP)
            refresh()
            return
        }

        viewModelScope.launch {
            try {
                val words = repository.getPronunciationWords(appSettings.safeLearningGrade.toInt())
                val ids = words.map { it.no.toLong() }
                Log.d("SkillChallenge", "Selected new words: count=${ids.size}, distinct=${ids.distinct().size}, IDs=$ids")

                if (ids.size < 3) {
                    onResult(ChallengeStartResult.NoWordsAvailable)
                    refresh()
                    return@launch
                }
                
                onResult(ChallengeStartResult.Success(ids, isResume = false))
            } catch (e: Exception) {
                Log.e("SkillChallenge", "Error in startPronunciationChallenge", e)
                onResult(ChallengeStartResult.Error)
                refresh()
            }
        }
    }

    /**
     * 確認ダイアログで「確定」した後に呼ばれる、実際の消費とセッション開始処理
     */
    fun confirmStartPronunciation(ids: List<Long>): Boolean {
        if (cpManager.consumeCP(10)) {
            appSettings.activePronunciationChallengeIds = ids
            appSettings.pronChallengeStatus = "IN_PROGRESS"
            appSettings.pronChallengeIndex = 0
            refresh()
            return true
        }
        return false
    }

    fun startSpellingChallenge(onResult: (ChallengeStartResult) -> Unit) {
        val activeIds = appSettings.activeSpellingChallengeIds
        val status = appSettings.spellChallengeStatus
        val index = appSettings.spellChallengeIndex
        val currentCP = cpManager.getCP()

        Log.d("SkillChallenge", "Spelling Start attempt: CP=$currentCP, Status=$status, Index=$index, ActiveIdsCount=${activeIds?.size}")

        // 再開判定: 厳密にする
        if (activeIds != null && activeIds.size == 5 && status == "IN_PROGRESS" && index < 5) {
            Log.d("SkillChallenge", "Resuming session")
            onResult(ChallengeStartResult.Success(activeIds, isResume = true))
            return
        }

        // 不正なデータは掃除
        appSettings.clearSpellingChallenge()

        if (currentCP < 10) {
            Log.d("SkillChallenge", "Insufficient CP: $currentCP")
            onResult(ChallengeStartResult.InsufficientCP)
            refresh()
            return
        }

        viewModelScope.launch {
            try {
                val words = repository.getSpellingWords(appSettings.safeLearningGrade.toInt())
                if (words.size < 5) {
                    onResult(ChallengeStartResult.NoWordsAvailable)
                    refresh()
                    return@launch
                }
                val ids = words.map { it.no.toLong() }
                
                if (cpManager.consumeCP(10)) {
                    appSettings.activeSpellingChallengeIds = ids
                    appSettings.spellChallengeStatus = "IN_PROGRESS"
                    appSettings.spellChallengeIndex = 0
                    
                    Log.d("SkillChallenge", "Started new session: CP=${cpManager.getCP()}")
                    onResult(ChallengeStartResult.Success(ids, isResume = false))
                    refresh()
                } else {
                    onResult(ChallengeStartResult.InsufficientCP)
                    refresh()
                }
            } catch (e: Exception) {
                Log.e("SkillChallenge", "Error starting spelling", e)
                onResult(ChallengeStartResult.Error)
                refresh()
            }
        }
    }
}

class SkillChallengeViewModelFactory(private val context: android.content.Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val appContext = context.applicationContext
        val cpManager = ChallengePointManager(appContext)
        val db = AppDatabase.getInstance(appContext)
        val repository = ChallengeRepository(appContext, db)
        val appSettings = AppSettings(appContext)
        return SkillChallengeViewModel(appContext, cpManager, repository, appSettings) as T
    }
}
