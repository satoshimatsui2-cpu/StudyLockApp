package com.example.studylockapp

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.studylockapp.data.AppDatabase
import com.example.studylockapp.data.AppSettings
import com.example.studylockapp.data.PointHistoryEntity
import com.example.studylockapp.data.PointManager
import com.example.studylockapp.data.ProgressCalculator
import com.example.studylockapp.data.WordEntity
import com.example.studylockapp.data.WordProgressEntity
import com.example.studylockapp.data.WordStudyLogEntity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs

// リスニング問題用データクラス
data class ListeningQuestion(
    val id: Int,
    val grade: String,
    val script: String,
    val question: String,
    val options: List<String>,
    val correctIndex: Int, // 0-based
    val explanation: String
)

class LearningActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    companion object {
        const val MODE_MEANING = "meaning"
        const val MODE_LISTENING = "listening"
        const val MODE_LISTENING_JP = "listening_jp"
        const val MODE_JA_TO_EN = "japanese_to_english"
        const val MODE_EN_EN_1 = "english_english_1"
        const val MODE_EN_EN_2 = "english_english_2"

        const val MODE_TEST_FILL_BLANK = "test_fill_blank"
        const val MODE_TEST_SORT = "test_sort"
        const val MODE_TEST_LISTEN_Q1 = "test_listen_q1"
        const val MODE_TEST_LISTEN_Q2 = "test_listen_q2" // 会話リスニング
    }

    private val viewModel: LearningViewModel by viewModels()

    private var currentMode = MODE_MEANING
    private var gradeFilter: String = "All"
    private var includeOtherGradesReview: Boolean = false

    // 単語学習用データ
    private var currentWord: WordEntity? = null
    private var allWords: List<WordEntity> = emptyList()
    private var allWordsFull: List<WordEntity> = emptyList()

    // リスニング学習用データ
    private var listeningQuestions: List<ListeningQuestion> = emptyList()
    private var currentListeningQuestion: ListeningQuestion? = null

    private var tts: TextToSpeech? = null
    // 会話用TTS
    private var conversationTts: ConversationTtsManager? = null

    private lateinit var settings: AppSettings
    private var currentCorrectIndex: Int = -1
    private lateinit var defaultChoiceTints: List<ColorStateList?>

    private val greenTint by lazy { ColorStateList.valueOf(ContextCompat.getColor(this, R.color.choice_correct)) }
    private val redTint by lazy { ColorStateList.valueOf(ContextCompat.getColor(this, R.color.choice_wrong)) }

    private var soundPool: SoundPool? = null
    private var seCorrectId: Int = 0
    private var seWrongId: Int = 0
    private var currentSnackbar: Snackbar? = null

    private lateinit var textQuestionTitle: TextView
    private lateinit var textQuestionBody: TextView
    private lateinit var textPoints: TextView
    private lateinit var textPointStats: TextView
    private var textCurrentGrade: TextView? = null
    private var textTotalWords: TextView? = null
    private lateinit var textFeedback: TextView

    // ★追加: スクリプト表示用と次へボタン
    private lateinit var textScriptDisplay: TextView
    private lateinit var buttonNextQuestion: Button

    private lateinit var layoutModeSelector: View
    private lateinit var selectorIconMode: ImageView
    private lateinit var selectorTextTitle: TextView
    private lateinit var selectorTextReview: TextView
    private lateinit var selectorTextNew: TextView
    private lateinit var selectorTextMaster: TextView

    private var currentStats: Map<String, ModeStats> = emptyMap()

    private lateinit var choiceButtons: List<Button>
    private lateinit var buttonPlayAudio: ImageButton
    private lateinit var buttonSoundSettings: ImageButton
    private var checkIncludeOtherGrades: CheckBox? = null
    private var checkboxAutoPlayAudio: CheckBox? = null

    private data class ModeStats(
        val review: Int,
        val newCount: Int,
        val total: Int,
        val mastered: Int
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_learning)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root_layout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        settings = AppSettings(this)
        gradeFilter = intent.getStringExtra("gradeFilter") ?: "All"

        initViews()
        observeViewModel()

        checkIncludeOtherGrades = findViewById<CheckBox?>(R.id.checkbox_include_other_grades)?.apply {
            isChecked = true
            includeOtherGradesReview = true
            setOnCheckedChangeListener { _, isChecked ->
                loadNextQuestion()
            }
        }

        checkboxAutoPlayAudio = findViewById<CheckBox?>(R.id.checkbox_auto_play_audio)?.apply {
            isChecked = true
        }

        textFeedback.visibility = View.GONE
        defaultChoiceTints = choiceButtons.map { ViewCompat.getBackgroundTintList(it) }

        tts = TextToSpeech(this, this)
        // 会話TTS初期化
        conversationTts = ConversationTtsManager(this)

        initSoundPool()

        layoutModeSelector.setOnClickListener {
            showModeSelectionSheet()
        }

        choiceButtons.forEachIndexed { index, btn ->
            btn.setOnClickListener { onChoiceSelected(index) }
        }

        buttonPlayAudio.setOnClickListener { speakCurrentWord() }

        buttonSoundSettings.setOnClickListener {
            try {
                startActivity(Intent().apply {
                    setClassName(this@LearningActivity, "com.example.studylockapp.SoundSettingsActivity")
                })
            } catch (e: Exception) {
                Toast.makeText(this, "起動に失敗", Toast.LENGTH_SHORT).show()
            }
        }

        // ★追加: 「次へ」ボタンの動作設定
        buttonNextQuestion.setOnClickListener {
            loadNextQuestion()
        }

        updatePointView()

        lifecycleScope.launch {
            val imported = withContext(Dispatchers.IO) {
                if (gradeFilter != "All") importMissingWordsForGrade(gradeFilter) else 0
            }
            if (imported > 0) {
                Snackbar.make(
                    findViewById(android.R.id.content),
                    getString(R.string.imported_count_message, imported),
                    Snackbar.LENGTH_SHORT
                ).show()
            }

            // リスニングCSV読み込み
            listeningQuestions = loadListeningQuestionsFromCsv()

            loadAllWordsThenQuestion()
        }
    }

    private fun initViews() {
        textQuestionTitle = findViewById(R.id.text_question_title)
        textQuestionBody = findViewById(R.id.text_question_body)
        textPoints = findViewById(R.id.text_points)
        textPointStats = findViewById(R.id.text_point_stats)
        textCurrentGrade = findViewById(R.id.text_current_grade)
        textTotalWords = findViewById(R.id.text_total_words)
        textFeedback = findViewById(R.id.text_feedback)

        // ★追加: 新しいView要素
        textScriptDisplay = findViewById(R.id.text_script_display)
        buttonNextQuestion = findViewById(R.id.button_next_question)

        layoutModeSelector = findViewById(R.id.layout_mode_selector)
        selectorIconMode = findViewById(R.id.selector_icon_mode)
        selectorTextTitle = findViewById(R.id.selector_text_title)
        selectorTextReview = findViewById(R.id.selector_text_review)
        selectorTextNew = findViewById(R.id.selector_text_new)
        selectorTextMaster = findViewById(R.id.selector_text_master)

        buttonPlayAudio = findViewById(R.id.button_play_audio)
        buttonSoundSettings = findViewById(R.id.button_sound_settings)

        choiceButtons = listOf(
            findViewById(R.id.button_choice_1),
            findViewById(R.id.button_choice_2),
            findViewById(R.id.button_choice_3),
            findViewById(R.id.button_choice_4),
            findViewById(R.id.button_choice_5),
            findViewById(R.id.button_choice_6)
        )
    }

    private fun showModeSelectionSheet() {
        val dialog = BottomSheetDialog(this)
        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))

        val view = layoutInflater.inflate(R.layout.layout_mode_selection_sheet, null)
        dialog.setContentView(view)

        fun setupRow(rowId: Int, modeKey: String, title: String, iconRes: Int, colorRes: Int, isTestMode: Boolean = false) {
            val card = view.findViewById<MaterialCardView>(rowId) ?: return
            val iconContainer = card.findViewById<View>(R.id.icon_container)
            val icon = card.findViewById<ImageView>(R.id.icon_mode)
            val textTitle = card.findViewById<TextView>(R.id.text_mode_title)

            val textReview = card.findViewById<TextView>(R.id.text_stat_review)
            val textNew = card.findViewById<TextView>(R.id.text_stat_new)
            val textMaster = card.findViewById<TextView>(R.id.text_stat_master)

            textTitle.text = title
            icon.setImageResource(iconRes)

            val color = ContextCompat.getColor(this, colorRes)
            ViewCompat.setBackgroundTintList(iconContainer, ColorStateList.valueOf(adjustAlpha(color, 0.15f)))
            icon.setColorFilter(color)

            if (isTestMode && modeKey != MODE_TEST_LISTEN_Q2) {
                textReview.text = "-"
                textNew.text = "-"
                textMaster.text = "-"
                card.alpha = 0.7f
                card.setOnClickListener { Toast.makeText(this, "開発中機能です", Toast.LENGTH_SHORT).show() }
            } else {
                val stats = currentStats[modeKey]
                if (stats != null) {
                    textReview.text = "${stats.review}"
                    textNew.text = "${stats.newCount}"
                    val rate = if (stats.total > 0) (stats.mastered * 100 / stats.total) else 0
                    textMaster.text = "${rate}%"
                    textReview.setTextColor(if (stats.review > 0) ContextCompat.getColor(this, R.color.choice_wrong) else 0xFF9CA3AF.toInt())
                } else if (modeKey == MODE_TEST_LISTEN_Q2) {
                    textReview.text = "-"
                    textNew.text = "-"
                    textMaster.text = "-"
                }

                if (currentMode == modeKey) {
                    card.strokeColor = color
                    card.strokeWidth = (2 * resources.displayMetrics.density).toInt()
                    card.setCardBackgroundColor(adjustAlpha(color, 0.05f))
                }

                card.setOnClickListener {
                    if (currentMode != modeKey) {
                        currentMode = modeKey
                        updateStudyStatsView()
                        loadNextQuestion()
                    }
                    dialog.dismiss()
                }
            }
        }

        setupRow(R.id.row_meaning, MODE_MEANING, getString(R.string.mode_meaning), R.drawable.ic_flash_cards_24, R.color.mode_purple)
        setupRow(R.id.row_listening, MODE_LISTENING, getString(R.string.mode_listening), R.drawable.ic_outline_cards_stack_24, R.color.mode_teal)
        setupRow(R.id.row_listening_jp, MODE_LISTENING_JP, getString(R.string.mode_listening_jp), R.drawable.ic_outline_cards_stack_24, R.color.mode_teal)
        setupRow(R.id.row_ja_to_en, MODE_JA_TO_EN, getString(R.string.mode_japanese_to_english), R.drawable.ic_outline_cards_stack_24, R.color.mode_indigo)
        setupRow(R.id.row_en_en_1, MODE_EN_EN_1, getString(R.string.mode_english_english_1), R.drawable.ic_outline_cards_stack_24, R.color.mode_orange)
        setupRow(R.id.row_en_en_2, MODE_EN_EN_2, getString(R.string.mode_english_english_2), R.drawable.ic_outline_cards_stack_24, R.color.mode_orange)

        setupRow(R.id.row_test_fill, MODE_TEST_FILL_BLANK, "穴埋め", R.drawable.ic_edit_24, R.color.mode_pink, true)
        setupRow(R.id.row_test_sort, MODE_TEST_SORT, "並び替え", R.drawable.ic_sort_24, R.color.mode_pink, true)
        setupRow(R.id.row_test_listen_q1, MODE_TEST_LISTEN_Q1, "リスニング質問", R.drawable.ic_forum_24, R.color.mode_blue, true)
        setupRow(R.id.row_test_listen_q2, MODE_TEST_LISTEN_Q2, "会話文リスニング", R.drawable.ic_forum_24, R.color.mode_blue)

        dialog.show()
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = (255 * factor).toInt()
        return (color and 0x00FFFFFF) or (alpha shl 24)
    }

    private fun observeViewModel() {
        viewModel.gradeName.observe(this) { gradeName ->
            textCurrentGrade?.text = gradeName
        }
        viewModel.wordCount.observe(this) { count ->
            textTotalWords?.text = getString(R.string.label_word_count, count)
        }
    }

    private fun initSoundPool() {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(attrs)
            .build()
        seCorrectId = loadSeIfExists("se_correct")
        seWrongId = loadSeIfExists("se_wrong")
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onResume() {
        super.onResume()
        applyTtsParams()
        updatePointView()
    }

    override fun onPause() {
        super.onPause()
        conversationTts?.stop()
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        conversationTts?.shutdown()

        soundPool?.release()
        soundPool = null
        currentSnackbar?.dismiss()
        super.onDestroy()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            tts?.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            applyTtsParams()
        }
    }

    private fun applyTtsParams() {
        val rate = settings.getTtsSpeed()
        val pitch = settings.getTtsPitch()
        tts?.setSpeechRate(rate)
        tts?.setPitch(pitch)
    }

    private fun loadSeIfExists(rawName: String): Int {
        val resId = resources.getIdentifier(rawName, "raw", packageName)
        return if (resId != 0) soundPool?.load(this, resId, 1) ?: 0 else 0
    }

    private fun flashCorrectBackground() {
        val root = findViewById<View>(android.R.id.content)
        val flashColor = ContextCompat.getColor(this, R.color.correct_flash)
        if (root.width == 0 || root.height == 0) return

        val drawable = ColorDrawable(flashColor)
        drawable.setBounds(0, 0, root.width, root.height)
        drawable.alpha = 0
        root.overlay.add(drawable)

        ValueAnimator.ofInt(0, 90, 0).apply {
            duration = 160L
            addUpdateListener { drawable.alpha = it.animatedValue as Int }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    root.overlay.remove(drawable)
                }
            })
            start()
        }
    }

    private fun playCorrectEffect() {
        flashCorrectBackground()
        val vol = settings.seCorrectVolume
        if (seCorrectId != 0) soundPool?.play(seCorrectId, vol, vol, 1, 0, 1f)

        val idx = currentCorrectIndex
        val v = choiceButtons.getOrNull(idx) ?: return
        v.animate().cancel()
        v.scaleX = 1f; v.scaleY = 1f
        v.animate().scaleX(1.12f).scaleY(1.12f).setDuration(120).withEndAction {
            v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
        }.start()
    }

    private fun playWrongEffect() {
        val vol = settings.seWrongVolume
        if (seWrongId != 0) soundPool?.play(seWrongId, vol, vol, 1, 0, 1f)
    }

    private fun nowEpochSec(): Long = System.currentTimeMillis() / 1000L

    private fun calcNextDueAtSec(isCorrect: Boolean, currentLevel: Int, nowSec: Long): Pair<Int, Long> {
        val newLevel = if (isCorrect) currentLevel + 1 else maxOf(0, currentLevel - 2)
        val zone = settings.getAppZoneId()
        val result: Pair<Int, Long> = when {
            !isCorrect -> newLevel to (nowSec + settings.wrongRetrySec)
            newLevel == 1 -> newLevel to (nowSec + settings.level1RetrySec)
            else -> {
                val days = when (newLevel) {
                    2 -> 1; 3 -> 3; 4 -> 7; 5 -> 14; 6 -> 30; 7 -> 60; else -> 90
                }
                val baseDate = Instant.ofEpochSecond(nowSec).atZone(zone).toLocalDate()
                val dueDate = baseDate.plusDays(days.toLong())
                val dueAtSec = dueDate.atStartOfDay(zone).toEpochSecond()
                newLevel to dueAtSec
            }
        }
        return result
    }

    private suspend fun importMissingWordsForGrade(grade: String): Int = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(this@LearningActivity)
        val wordDao = db.wordDao()
        val csvWords = readCsvWords().filter { it.grade == grade }
        if (csvWords.isEmpty()) return@withContext 0
        val existing = wordDao.getAll().filter { it.grade == grade }.associateBy { it.word }
        val missing = csvWords.filter { existing[it.word] == null }
        if (missing.isNotEmpty()) wordDao.insertAll(missing)
        missing.size
    }

    private fun readCsvWords(): List<WordEntity> {
        val result = mutableListOf<WordEntity>()
        try {
            resources.openRawResource(R.raw.words).use { input ->
                BufferedReader(InputStreamReader(input)).useLines { lines ->
                    lines.drop(1).forEach { line ->
                        val cols = line.split(",")
                        if (cols.size >= 7) {
                            result.add(WordEntity(
                                no = cols[0].toIntOrNull() ?: 0,
                                grade = cols[1],
                                word = cols[2],
                                japanese = cols[3],
                                description = cols[4],
                                smallTopicId = cols[5],
                                mediumCategoryId = cols[6]
                            ))
                        }
                    }
                }
            }
        } catch (e: Exception) { Log.e("LearningActivity", "Error reading CSV", e) }
        return result
    }

    private suspend fun loadListeningQuestionsFromCsv(): List<ListeningQuestion> = withContext(Dispatchers.IO) {
        val result = mutableListOf<ListeningQuestion>()
        try {
            val resId = resources.getIdentifier("listening2", "raw", packageName)
            if (resId == 0) return@withContext emptyList()

            resources.openRawResource(resId).use { input ->
                BufferedReader(InputStreamReader(input)).useLines { lines ->
                    lines.drop(1).forEach { line ->
                        val cols = parseCsvLine(line)
                        if (cols.size >= 11) {
                            val options = listOf(cols[5], cols[6], cols[7], cols[8])
                            val correctIdx = (cols[9].toIntOrNull() ?: 1) - 1
                            result.add(ListeningQuestion(
                                id = cols[0].toIntOrNull() ?: 0,
                                grade = cols[1],
                                script = cols[3].replace("\\n", "\n"),
                                question = cols[4],
                                options = options,
                                correctIndex = correctIdx,
                                explanation = cols[10]
                            ))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("LearningActivity", "Error reading listening CSV", e)
        }
        return@withContext result
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        for (char in line) {
            when {
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current = StringBuilder()
                }
                else -> current.append(char)
            }
        }
        result.add(current.toString())
        return result
    }

    private fun loadAllWordsThenQuestion() {
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(this@LearningActivity)
            val words = db.wordDao().getAll()
            allWordsFull = words
            allWords = if (gradeFilter == "All") words else words.filter { it.grade == gradeFilter }

            viewModel.setGradeInfo(gradeFilter, allWords)

            updateStudyStatsView()
            loadNextQuestion()
        }
    }

    private suspend fun computeModeStats(wordIdSet: Set<Int>, mode: String, nowSec: Long): ModeStats {
        val db = AppDatabase.getInstance(this@LearningActivity)
        val progressDao = db.wordProgressDao()

        val progresses = progressDao.getAllProgressForMode(mode)

        val targetProgresses = progresses.filter { it.wordId in wordIdSet }
        val dueCount = progressDao.getDueWordIdsOrdered(mode, nowSec).count { it in wordIdSet }
        val startedCount = targetProgresses.size

        return ModeStats(
            review = dueCount,
            newCount = wordIdSet.size - startedCount,
            total = wordIdSet.size,
            mastered = targetProgresses.count { it.level >= 6 }
        )
    }

    private fun updateStudyStatsView() {
        lifecycleScope.launch(Dispatchers.IO) {
            if (allWords.isEmpty()) return@launch
            val wordIdSet: Set<Int> = allWords.map { it.no }.toSet()
            val nowSec = nowEpochSec()

            val mStats = computeModeStats(wordIdSet, MODE_MEANING, nowSec)
            val lStats = computeModeStats(wordIdSet, MODE_LISTENING, nowSec)
            val lJpStats = computeModeStats(wordIdSet, MODE_LISTENING_JP, nowSec)
            val jeStats = computeModeStats(wordIdSet, MODE_JA_TO_EN, nowSec)
            val ee1Stats = computeModeStats(wordIdSet, MODE_EN_EN_1, nowSec)
            val ee2Stats = computeModeStats(wordIdSet, MODE_EN_EN_2, nowSec)

            currentStats = mapOf(
                MODE_MEANING to mStats,
                MODE_LISTENING to lStats,
                MODE_LISTENING_JP to lJpStats,
                MODE_JA_TO_EN to jeStats,
                MODE_EN_EN_1 to ee1Stats,
                MODE_EN_EN_2 to ee2Stats
            )

            withContext(Dispatchers.Main) {
                val currentStat = currentStats[currentMode]
                val modeName = when(currentMode) {
                    MODE_MEANING -> getString(R.string.mode_meaning)
                    MODE_LISTENING -> getString(R.string.mode_listening)
                    MODE_LISTENING_JP -> getString(R.string.mode_listening_jp)
                    MODE_JA_TO_EN -> getString(R.string.mode_japanese_to_english)
                    MODE_EN_EN_1 -> getString(R.string.mode_english_english_1)
                    MODE_EN_EN_2 -> getString(R.string.mode_english_english_2)
                    MODE_TEST_LISTEN_Q2 -> "会話文リスニング"
                    else -> "選択中"
                }

                val iconRes = when(currentMode) {
                    MODE_MEANING -> R.drawable.ic_flash_cards_24
                    MODE_TEST_LISTEN_Q2 -> R.drawable.ic_forum_24
                    else -> R.drawable.ic_outline_cards_stack_24
                }

                selectorIconMode.setImageResource(iconRes)
                selectorTextTitle.text = modeName

                if (currentMode == MODE_TEST_LISTEN_Q2) {
                    selectorTextReview.text = "-"
                    selectorTextNew.text = "-"
                    selectorTextMaster.text = "-"
                } else if (currentStat != null) {
                    selectorTextReview.text = "${currentStat.review}"
                    selectorTextNew.text = "${currentStat.newCount}"
                    val rate = if (currentStat.total > 0) (currentStat.mastered * 100 / currentStat.total) else 0
                    selectorTextMaster.text = "${rate}%"
                }
            }
        }
    }

    private fun resetChoiceButtons() {
        choiceButtons.forEachIndexed { i, btn ->
            btn.isClickable = true
            btn.isEnabled = true
            btn.alpha = 1f
            btn.visibility = View.VISIBLE
            ViewCompat.setBackgroundTintList(btn, defaultChoiceTints[i])
        }
        currentCorrectIndex = -1
    }

    private fun loadNextQuestion() {
        lifecycleScope.launch {
            conversationTts?.stop()
            tts?.stop()

            // ★追加: 画面状態のリセット
            textScriptDisplay.visibility = View.GONE
            textFeedback.visibility = View.GONE
            buttonNextQuestion.visibility = View.GONE
            textQuestionBody.visibility = View.VISIBLE // 基本は表示

            val db = AppDatabase.getInstance(this@LearningActivity)
            val progressDao = db.wordProgressDao()
            val nowSec = nowEpochSec()

            resetChoiceButtons()

            // ▼▼▼ 会話リスニングモードの分岐処理 ▼▼▼
            if (currentMode == MODE_TEST_LISTEN_Q2) {
                if (listeningQuestions.isEmpty()) {
                    showNoQuestion()
                    return@launch
                }

                val question = listeningQuestions.random()
                currentListeningQuestion = question
                currentWord = null

                // ★修正: 要望1 対応 (質問文を隠す)
                textQuestionTitle.text = "会話を聞いて質問に答えてください"
                textQuestionBody.text = question.question
                textQuestionBody.visibility = View.GONE // ここで非表示

                choiceButtons.forEachIndexed { index, btn ->
                    if (index < question.options.size) {
                        btn.text = question.options[index]
                        btn.visibility = View.VISIBLE
                        btn.textSize = 14f
                    } else {
                        btn.visibility = View.INVISIBLE
                    }
                }
                currentCorrectIndex = question.correctIndex

                if (checkboxAutoPlayAudio?.isChecked == true) {
                    delay(500)
                    conversationTts?.playScript(question.script)
                }

                checkboxAutoPlayAudio?.visibility = View.VISIBLE
                buttonPlayAudio.visibility = View.VISIBLE

                return@launch
            }
            // ▲▲▲

            if (allWordsFull.isEmpty()) {
                showNoQuestion()
                return@launch
            }

            val wordMapFiltered = allWords.associateBy { it.no }
            val wordMapAll = allWordsFull.associateBy { it.no }
            val dueIdsOrdered = progressDao.getDueWordIdsOrdered(currentMode, nowSec)

            val dueWords = if (includeOtherGradesReview && gradeFilter != "All") {
                dueIdsOrdered.mapNotNull { wordMapAll[it] }
            } else {
                dueIdsOrdered.mapNotNull { wordMapFiltered[it] }
            }

            val progressedIds = progressDao.getProgressIds(currentMode).toSet()
            val newWords = if (gradeFilter == "All") {
                allWordsFull.filter { it.no !in progressedIds }
            } else {
                allWords.filter { it.no !in progressedIds }
            }

            val nextWord = when {
                dueWords.isNotEmpty() -> dueWords.first()
                newWords.isNotEmpty() -> newWords.random()
                else -> null
            }

            if (nextWord == null) {
                showNoQuestion()
                return@launch
            }

            currentWord = nextWord
            val choicePool = if (includeOtherGradesReview && gradeFilter != "All") allWordsFull else {
                if (gradeFilter == "All") allWordsFull else allWords
            }

            val choices = buildChoices(nextWord, choicePool, 6)
            val (title, body, options) = formatQuestionAndOptions(nextWord, choices, currentMode)

            textQuestionTitle.text = title
            textQuestionBody.text = body
            textQuestionBody.visibility = if (body.isEmpty()) View.GONE else View.VISIBLE

            choiceButtons.forEach { it.textSize = if (currentMode == MODE_EN_EN_1) 12f else 14f }

            choiceButtons.zip(options).forEach { (btn, txt) -> btn.text = txt }

            val correctStr = when (currentMode) {
                MODE_MEANING -> nextWord.japanese ?: ""
                MODE_LISTENING -> nextWord.word
                MODE_LISTENING_JP -> nextWord.japanese ?: ""
                MODE_JA_TO_EN -> nextWord.word
                MODE_EN_EN_1 -> nextWord.description ?: ""
                MODE_EN_EN_2 -> nextWord.word
                else -> nextWord.japanese ?: ""
            }
            currentCorrectIndex = options.indexOf(correctStr)

            when (currentMode) {
                MODE_JA_TO_EN -> {
                    checkboxAutoPlayAudio?.visibility = View.GONE
                    buttonPlayAudio.visibility = View.GONE
                }
                MODE_MEANING, MODE_EN_EN_1, MODE_EN_EN_2 -> {
                    checkboxAutoPlayAudio?.visibility = View.VISIBLE
                    buttonPlayAudio.visibility = View.VISIBLE
                }
                else -> {
                    checkboxAutoPlayAudio?.visibility = View.GONE
                    buttonPlayAudio.visibility = View.VISIBLE
                }
            }

            if (currentMode == MODE_LISTENING || currentMode == MODE_LISTENING_JP) {
                speakCurrentWord()
            } else if (checkboxAutoPlayAudio?.isChecked == true && checkboxAutoPlayAudio?.visibility == View.VISIBLE) {
                if (currentMode == MODE_EN_EN_2) {
                    val textToSpeak = nextWord.description ?: ""
                    speakText(textToSpeak)
                } else {
                    speakCurrentWord()
                }
            }
        }
    }

    private fun showNoQuestion() {
        textQuestionTitle.text = getString(R.string.no_question_available)
        textQuestionBody.text = ""
        textQuestionBody.visibility = View.GONE
        currentWord = null
        choiceButtons.forEach { it.text = "----"; it.isEnabled = false }
    }

    private fun buildChoices(correct: WordEntity, pool: List<WordEntity>, count: Int): List<WordEntity> {
        if (pool.isEmpty()) return listOf(correct)
        val candidates = pool.filter { it.no != correct.no }

        val sameGradeCategoryHeadLen = candidates.filter {
            it.grade == correct.grade &&
                    it.mediumCategoryId == correct.mediumCategoryId &&
                    it.word.take(1).equals(correct.word.take(1), ignoreCase = true) &&
                    abs(it.word.length - correct.word.length) <= 1
        }
        val sameGradeCategory = candidates.filter { it.grade == correct.grade && it.mediumCategoryId == correct.mediumCategoryId }
        val sameGrade = candidates.filter { it.grade == correct.grade }
        val sameCategory = candidates.filter { it.mediumCategoryId == correct.mediumCategoryId }
        val sameHead = candidates.filter { it.word.take(1).equals(correct.word.take(1), ignoreCase = true) }
        val lenNear = candidates.filter { abs(it.word.length - correct.word.length) <= 2 }

        val merged = (sameGradeCategoryHeadLen + sameGradeCategory + sameGrade + sameCategory + sameHead + lenNear + candidates).distinct()
        val distractors = merged.shuffled().take(count - 1)
        return (distractors + correct).shuffled()
    }

    private fun formatQuestionAndOptions(
        correct: WordEntity,
        choices: List<WordEntity>,
        mode: String
    ): Triple<String, String, List<String>> {
        return when (mode) {
            MODE_MEANING -> {
                Triple("この英単語の意味は？", correct.word, choices.map { it.japanese ?: "" })
            }
            MODE_LISTENING -> {
                Triple("音声を聞いて正しい英単語を選んでください", "", choices.map { it.word })
            }
            MODE_LISTENING_JP -> {
                Triple("音声を聞いて正しい意味を選んでください", "", choices.map { it.japanese ?: "" })
            }
            MODE_JA_TO_EN -> {
                Triple("この日本語に対応する英単語は？", correct.japanese ?: "", choices.map { it.word })
            }
            MODE_EN_EN_1 -> {
                Triple("この単語の意味(定義)は？", correct.word, choices.map { it.description ?: "" })
            }
            MODE_EN_EN_2 -> {
                Triple("この意味(定義)に対応する単語は？", correct.description ?: "", choices.map { it.word })
            }
            else -> Triple("", "", emptyList())
        }
    }

    private fun onChoiceSelected(selectedIndex: Int) {
        // ▼▼▼ 会話リスニングモードの正誤判定 ▼▼▼
        if (currentMode == MODE_TEST_LISTEN_Q2) {
            val isCorrect = (selectedIndex == currentCorrectIndex)

            choiceButtons.forEach { it.isClickable = false }
            if (currentCorrectIndex in choiceButtons.indices) {
                ViewCompat.setBackgroundTintList(choiceButtons[currentCorrectIndex], greenTint)
            }
            if (!isCorrect && selectedIndex in choiceButtons.indices) {
                ViewCompat.setBackgroundTintList(choiceButtons[selectedIndex], redTint)
            }

            if (isCorrect) playCorrectEffect() else playWrongEffect()

            // ★修正: 要望2 対応 (スクリプトと質問を表示)
            val q = currentListeningQuestion
            if (q != null) {
                val scriptText = q.script
                    .replace("Question:", "\n[Question]")
                    .replace("Narrator:", "\n[Narrator]")

                textScriptDisplay.text = "$scriptText\n\nQ: ${q.question}"
                textScriptDisplay.visibility = View.VISIBLE
            }

            textFeedback.text = currentListeningQuestion?.explanation ?: ""
            textFeedback.visibility = View.VISIBLE

            showFeedbackSnackbar(isCorrect, 10)

            // ★修正: 要望3 対応 (テストモードは常に手動遷移)
            showNextButton()
            return
        }
        // ▲▲▲

        val cw = currentWord ?: return
        val selectedText = choiceButtons.getOrNull(selectedIndex)?.text?.toString() ?: return

        val isCorrect = when (currentMode) {
            MODE_MEANING -> selectedText == (cw.japanese ?: "")
            MODE_LISTENING -> selectedText == cw.word
            MODE_LISTENING_JP -> selectedText == (cw.japanese ?: "")
            MODE_JA_TO_EN -> selectedText == cw.word
            MODE_EN_EN_1 -> selectedText == (cw.description ?: "")
            MODE_EN_EN_2 -> selectedText == cw.word
            else -> false
        }

        choiceButtons.forEach { it.isClickable = false }
        if (currentCorrectIndex in choiceButtons.indices) {
            ViewCompat.setBackgroundTintList(choiceButtons[currentCorrectIndex], greenTint)
        }
        if (!isCorrect && selectedIndex != currentCorrectIndex && selectedIndex in choiceButtons.indices) {
            ViewCompat.setBackgroundTintList(choiceButtons[selectedIndex], redTint)
        }

        if (isCorrect) playCorrectEffect() else playWrongEffect()
        onAnsweredInternal(cw.no, isCorrect)
    }

    private fun onAnsweredInternal(wordId: Int, isCorrect: Boolean) {
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(this@LearningActivity)
            val progressDao = db.wordProgressDao()
            val pointManager = PointManager(this@LearningActivity)

            val nowSec = nowEpochSec()
            val current = progressDao.getProgress(wordId, currentMode)
            val currentLevel = current?.level ?: 0
            val newCount = (current?.studyCount ?: 0) + 1

            val (newLevel, nextDueAtSec) = calcNextDueAtSec(isCorrect, currentLevel, nowSec)
            val addPoint = ProgressCalculator.calcPoint(isCorrect, currentLevel)
            pointManager.add(addPoint)

            if (addPoint > 0) {
                db.pointHistoryDao().insert(
                    PointHistoryEntity(
                        mode = currentMode,
                        dateEpochDay = LocalDate.now(settings.getAppZoneId()).toEpochDay(),
                        delta = addPoint
                    )
                )
            }

            val currentTimestamp = System.currentTimeMillis()

            val log = WordStudyLogEntity(
                wordId = wordId,
                mode = currentMode,
                learnedAt = currentTimestamp
            )
            db.studyLogDao().insert(log)

            progressDao.upsert(
                WordProgressEntity(
                    wordId = wordId,
                    mode = currentMode,
                    level = newLevel,
                    nextDueAtSec = nextDueAtSec,
                    lastAnsweredAt = currentTimestamp,
                    studyCount = newCount
                )
            )

            updateStudyStatsView()
            showFeedbackSnackbar(isCorrect, addPoint)
            updatePointView()

            // ★修正: 要望3 対応 (学習モードでも間違えたら手動遷移)
            if (!isCorrect) {
                showNextButton()
            } else {
                delay(settings.answerIntervalMs)
                loadNextQuestion()
            }
        }
    }

    // ★追加: 「次へ」ボタンを表示するヘルパー
    private fun showNextButton() {
        buttonNextQuestion.visibility = View.VISIBLE
    }

    private fun showFeedbackSnackbar(isCorrect: Boolean, addPoint: Int) {
        val bgColor = ContextCompat.getColor(
            this,
            if (isCorrect) R.color.snackbar_correct_bg else R.color.snackbar_wrong_bg
        )
        val msg = if (isCorrect) {
            val praise = listOf("すごい！", "その調子！", "天才！", "完璧！", "いいね！", "ナイス！").random()
            "$praise +${addPoint}pt"
        } else "不正解…"

        currentSnackbar?.dismiss()
        val root = findViewById<View>(android.R.id.content)
        currentSnackbar = Snackbar.make(root, msg, Snackbar.LENGTH_SHORT).apply {
            setBackgroundTint(bgColor)
            setTextColor(android.graphics.Color.WHITE)
            duration = settings.answerIntervalMs.toInt().coerceIn(600, 4000)
            show()
        }
    }

    private fun updatePointView() {
        val total = PointManager(this).getTotal()
        textPoints.text = "保有ポイント: $total"
        lifecycleScope.launch { updatePointStats() }
    }

    private suspend fun updatePointStats() {
        val db = AppDatabase.getInstance(this@LearningActivity)
        val histDao = db.pointHistoryDao()
        val zone = settings.getAppZoneId()
        val today = LocalDate.now(zone).toEpochDay()
        val yesterday = today - 1
        val todaySum = histDao.getSumByDate(today)
        val yesterdaySum = histDao.getSumByDate(yesterday)
        val diff = todaySum - yesterdaySum
        val diffSign = if (diff >= 0) "+" else "-"
        textPointStats.text = "今日: $todaySum / 前日比: $diffSign${abs(diff)}"
    }

    private fun speakCurrentWord() {
        if (currentMode == MODE_TEST_LISTEN_Q2) {
            currentListeningQuestion?.let { q ->
                conversationTts?.playScript(q.script)
            }
            return
        }

        val cw = currentWord ?: return
        speakText(cw.word)
    }

    private fun speakText(text: String) {
        if (text.isEmpty()) return
        applyTtsParams()
        val rawVol = settings.ttsVolume
        val vol = if (rawVol > 1f) (rawVol / 100f).coerceIn(0f, 1f) else rawVol.coerceIn(0f, 1f)
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, vol)
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "tts-${System.currentTimeMillis()}")
    }
}