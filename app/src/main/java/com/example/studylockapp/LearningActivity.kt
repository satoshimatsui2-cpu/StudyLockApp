package com.example.studylockapp

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.lifecycle.lifecycleScope
import com.example.studylockapp.data.AppDatabase
import com.example.studylockapp.data.AppSettings
import com.example.studylockapp.data.CsvImporter
import com.example.studylockapp.data.PointHistoryEntity
import com.example.studylockapp.data.PointManager
import com.example.studylockapp.data.ProgressCalculator
import com.example.studylockapp.data.WordEntity
import com.example.studylockapp.data.WordProgressEntity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.math.abs

class LearningActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private var currentMode = "meaning"
    private var gradeFilter: String = "All"   // TOPで選んだ級（DBのgradeと一致する値 例:"5"）
    private var currentWord: WordEntity? = null
    private var allWords: List<WordEntity> = emptyList()
    private var tts: TextToSpeech? = null

    // 設定（回答間隔/音量など）
    private lateinit var settings: AppSettings

    // 今回の問題で「正解が入っているボタンのindex」
    private var currentCorrectIndex: Int = -1

    // ボタンの元の色に戻すため
    private lateinit var defaultChoiceTints: List<ColorStateList?>

    private val greenTint by lazy {
        ColorStateList.valueOf(ContextCompat.getColor(this, R.color.choice_correct))
    }
    private val redTint by lazy {
        ColorStateList.valueOf(ContextCompat.getColor(this, R.color.choice_wrong))
    }

    // SoundPool
    private var soundPool: SoundPool? = null
    private var seCorrectId: Int = 0
    private var seWrongId: Int = 0

    // 褒め言葉
    private val praiseMessages = listOf(
        "すごい！", "その調子！", "天才！", "完璧！", "いいね！", "ナイス！"
    )

    // Snackbarが重ならないように保持
    private var currentSnackbar: Snackbar? = null

    private lateinit var textQuestionTitle: TextView
    private lateinit var textQuestionBody: TextView
    private lateinit var textPoints: TextView
    private lateinit var textPointStats: TextView

    // 互換のため残す（XMLでは gone）
    private lateinit var textStudyStats: TextView
    private lateinit var textFeedback: TextView

    // ★追加：Chip（意/聴）
    private lateinit var chipMeaningReview: Chip
    private lateinit var chipMeaningNew: Chip
    private lateinit var chipListeningReview: Chip
    private lateinit var chipListeningNew: Chip

    private lateinit var radioGroup: RadioGroup
    private lateinit var radioMeaning: RadioButton
    private lateinit var radioListening: RadioButton
    private lateinit var choiceButtons: List<Button>
    private lateinit var buttonPlayAudio: Button

    // --- モード別件数保持用データクラス ---
    private data class ModeStats(
        val review: Int,   // nextDueAtSec <= now の件数
        val newCount: Int, // そのモードの未学習件数（progress なし）
        val total: Int     // grade 内の総件数
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_learning)

        settings = AppSettings(this)
        gradeFilter = intent.getStringExtra("gradeFilter") ?: "All"

        // Toolbar（戻る矢印）
        findViewById<MaterialToolbar>(R.id.toolbar_learning)?.let { tb ->
            setSupportActionBar(tb)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
        }

        textQuestionTitle = findViewById(R.id.text_question_title)
        textQuestionBody = findViewById(R.id.text_question_body)
        textPoints = findViewById(R.id.text_points)
        textPointStats = findViewById(R.id.text_point_stats)

        textStudyStats = findViewById(R.id.text_study_stats)
        textFeedback = findViewById(R.id.text_feedback)

        chipMeaningReview = findViewById(R.id.chip_meaning_review)
        chipMeaningNew = findViewById(R.id.chip_meaning_new)
        chipListeningReview = findViewById(R.id.chip_listening_review)
        chipListeningNew = findViewById(R.id.chip_listening_new)

        radioGroup = findViewById(R.id.radio_group_mode)
        radioMeaning = findViewById(R.id.radio_meaning)
        radioListening = findViewById(R.id.radio_listening)

        buttonPlayAudio = findViewById(R.id.button_play_audio)

        choiceButtons = listOf(
            findViewById(R.id.button_choice_1),
            findViewById(R.id.button_choice_2),
            findViewById(R.id.button_choice_3),
            findViewById(R.id.button_choice_4),
            findViewById(R.id.button_choice_5),
            findViewById(R.id.button_choice_6)
        )

        // フィードバックTextViewは使わないので隠す
        textFeedback.visibility = View.GONE

        // デフォルトの背景Tintを保存（次の問題で戻す用）
        defaultChoiceTints = choiceButtons.map { ViewCompat.getBackgroundTintList(it) }

        tts = TextToSpeech(this, this)

        // SoundPool 初期化
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

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            currentMode = if (checkedId == radioMeaning.id) "meaning" else "listening"
            updateStudyStatsView()
            loadNextQuestion()
        }

        choiceButtons.forEachIndexed { index, btn ->
            btn.setOnClickListener { onChoiceSelected(index) }
        }

        buttonPlayAudio.setOnClickListener { speakCurrentWord() }

        updatePointView()

        // ★選択グレードのみ差分インポートしてから出題
        lifecycleScope.launch {
            val imported = withContext(Dispatchers.IO) {
                if (gradeFilter != "All") {
                    CsvImporter.importGradeIfNeeded(this@LearningActivity, gradeFilter)
                } else {
                    0
                }
            }
            if (imported > 0) {
                Toast.makeText(
                    this@LearningActivity,
                    "${imported}件データインポートしました。",
                    Toast.LENGTH_SHORT
                ).show()
            }
            loadAllWordsThenQuestion()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onResume() {
        super.onResume()
        updatePointView()
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()

        soundPool?.release()
        soundPool = null

        currentSnackbar?.dismiss()
        currentSnackbar = null

        super.onDestroy()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts?.language = Locale.US
    }

    private fun loadSeIfExists(rawName: String): Int {
        val resId = resources.getIdentifier(rawName, "raw", packageName)
        if (resId == 0) {
            Log.w("SoundPool", "raw/$rawName が見つかりません（効果音なしで続行）")
            return 0
        }
        return soundPool?.load(this, resId, 1) ?: 0
    }

    private fun flashCorrectBackground() {
        val root = findViewById<View>(android.R.id.content)
        val flashColor = ContextCompat.getColor(this, R.color.correct_flash)

        fun runFlash() {
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

        if (root.width == 0 || root.height == 0) root.post { runFlash() } else runFlash()
    }

    private fun playCorrectEffect() {
        flashCorrectBackground()

        val vol = settings.seCorrectVolume
        if (seCorrectId != 0) soundPool?.play(seCorrectId, vol, vol, 1, 0, 1f)

        val idx = currentCorrectIndex
        val v = choiceButtons.getOrNull(idx) ?: return
        v.animate().cancel()
        v.scaleX = 1f
        v.scaleY = 1f
        v.animate()
            .scaleX(1.12f)
            .scaleY(1.12f)
            .setDuration(120)
            .withEndAction {
                v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }
            .start()
    }

    private fun playWrongEffect() {
        val vol = settings.seWrongVolume
        if (seWrongId != 0) soundPool?.play(seWrongId, vol, vol, 1, 0, 1f)
    }

    private fun nowEpochSec(): Long = System.currentTimeMillis() / 1000L

    /**
     * Due計算（秒精度）
     * - 誤答：now + wrongRetrySec
     * - 正解して newLevel==1：now + level1RetrySec
     * - 翌日以降：0時固定（アプリ設定のタイムゾーン）
     */
    private fun calcNextDueAtSec(isCorrect: Boolean, currentLevel: Int, nowSec: Long): Pair<Int, Long> {
        val newLevel = if (isCorrect) currentLevel + 1 else maxOf(0, currentLevel - 2)
        val zone = settings.getAppZoneId()

        fun zdt(sec: Long): ZonedDateTime = Instant.ofEpochSecond(sec).atZone(zone)

        val result: Pair<Int, Long> = when {
            !isCorrect -> newLevel to (nowSec + settings.wrongRetrySec)
            newLevel == 1 -> newLevel to (nowSec + settings.level1RetrySec)
            else -> {
                val days = when (newLevel) {
                    2 -> 1
                    3 -> 3
                    4 -> 7
                    5 -> 14
                    6 -> 30
                    7 -> 60
                    else -> 90
                }
                val baseDate = Instant.ofEpochSecond(nowSec).atZone(zone).toLocalDate()
                val dueDate = baseDate.plusDays(days.toLong())
                val dueAtSec = dueDate.atStartOfDay(zone).toEpochSecond()
                newLevel to dueAtSec
            }
        }

        val nextDueAtSec = result.second
        Log.d(
            "DUE_CALC",
            "isCorrect=$isCorrect currentLevel=$currentLevel newLevel=$newLevel " +
                    "nowSec=$nowSec(${zdt(nowSec)}) nextDueAtSec=$nextDueAtSec(${zdt(nextDueAtSec)})"
        )
        return result
    }

    private fun loadAllWordsThenQuestion() {
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(this@LearningActivity)
            val words = db.wordDao().getAll()

            allWords = if (gradeFilter == "All") words else words.filter { it.grade == gradeFilter }

            updateStudyStatsView()
            loadNextQuestion()
        }
    }

    // --- モード別集計ヘルパ ---
    private suspend fun computeModeStats(wordIdSet: Set<Int>, mode: String, nowSec: Long): ModeStats {
        val db = AppDatabase.getInstance(this@LearningActivity)
        val progressDao = db.wordProgressDao()

        val dueCount = progressDao.getDueWordIdsOrdered(mode, nowSec)
            .count { it in wordIdSet }

        val startedIds = progressDao.getProgressIds(mode).toSet()
        val newCount = wordIdSet.count { it !in startedIds }

        return ModeStats(
            review = dueCount,
            newCount = newCount,
            total = wordIdSet.size
        )
    }

    /**
     * 表示仕様（学習画面）：
     * 意：復=meaningのDue数 / 新=meaningの未学習数/総数
     * 聴：復=listeningのDue数 / 新=listeningの未学習数/総数
     */
    private fun updateStudyStatsView() {
        lifecycleScope.launch(Dispatchers.IO) {
            val total = allWords.size
            if (total == 0) {
                withContext(Dispatchers.Main) {
                    chipMeaningReview.text = "復 0"
                    chipMeaningNew.text = "新 0/0"
                    chipListeningReview.text = "復 0"
                    chipListeningNew.text = "新 0/0"
                    textStudyStats.text = "意[復:0,新:0/0]\n聴[復:0,新:0/0]"
                }
                return@launch
            }

            val wordIdSet: Set<Int> = allWords.map { it.no }.toSet()
            val nowSec = nowEpochSec()

            val meaningStats = computeModeStats(wordIdSet, "meaning", nowSec)
            val listeningStats = computeModeStats(wordIdSet, "listening", nowSec)

            withContext(Dispatchers.Main) {
                chipMeaningReview.text = "復 ${meaningStats.review}"
                chipMeaningNew.text = "新 ${meaningStats.newCount}/${meaningStats.total}"
                chipListeningReview.text = "復 ${listeningStats.review}"
                chipListeningNew.text = "新 ${listeningStats.newCount}/${listeningStats.total}"

                // 互換用（見えないがログ/デバッグで確認したい時用）
                textStudyStats.text =
                    "意[復:${meaningStats.review},新:${meaningStats.newCount}/${meaningStats.total}]\n" +
                            "聴[復:${listeningStats.review},新:${listeningStats.newCount}/${listeningStats.total}]"
            }
        }
    }

    private fun resetChoiceButtons() {
        choiceButtons.forEachIndexed { i, btn ->
            btn.isClickable = true
            btn.isEnabled = true
            btn.alpha = 1f
            ViewCompat.setBackgroundTintList(btn, defaultChoiceTints[i])
        }
        currentCorrectIndex = -1
    }

    /**
     * 出題優先順位
     * 1) nextDueAtSec <= NOW を古い順（最小nextDueAtSec）から
     * 2) progress無し（未学習）
     */
    private fun loadNextQuestion() {
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(this@LearningActivity)
            val progressDao = db.wordProgressDao()
            val nowSec = nowEpochSec()

            resetChoiceButtons()

            if (allWords.isEmpty()) {
                textQuestionTitle.text = "出題する単語がありません"
                textQuestionBody.text = ""
                textQuestionBody.visibility = View.GONE
                currentWord = null
                choiceButtons.forEach {
                    it.text = "----"
                    it.isEnabled = false
                }
                return@launch
            }

            val dueIdsOrdered = progressDao.getDueWordIdsOrdered(currentMode, nowSec)
            val wordMap = allWords.associateBy { it.no }
            val dueWord: WordEntity? = dueIdsOrdered.firstNotNullOfOrNull { wordMap[it] }

            val progressedIds = progressDao.getProgressIds(currentMode).toSet()
            val untouched = allWords.filter { it.no !in progressedIds }

            val next = when {
                dueWord != null -> dueWord
                untouched.isNotEmpty() -> untouched.random()
                else -> allWords.random()
            }

            currentWord = next

            val choices = if (currentMode == "meaning") {
                buildChoicesMeaning(next, allWords, count = 6)
            } else {
                buildChoicesListening(next, allWords, count = 6)
            }

            val (title, body, options) = formatQuestionAndOptions(next, choices, currentMode)
            textQuestionTitle.text = title
            textQuestionBody.text = body
            textQuestionBody.visibility = if (body.isEmpty()) View.GONE else View.VISIBLE
            choiceButtons.zip(options).forEach { (btn, txt) -> btn.text = txt }

            val correctText = if (currentMode == "meaning") next.japanese else next.word
            currentCorrectIndex = options.indexOf(correctText)

            if (currentMode == "listening") speakCurrentWord()
        }
    }

    private fun buildChoicesMeaning(correct: WordEntity, pool: List<WordEntity>, count: Int): List<WordEntity> {
        if (pool.isEmpty()) return listOf(correct)
        val candidates = pool.filter { it.no != correct.no }

        val sameGradePosHeadLen = candidates.filter {
            it.grade == correct.grade &&
                    it.pos == correct.pos &&
                    it.word.take(1).equals(correct.word.take(1), ignoreCase = true) &&
                    abs(it.word.length - correct.word.length) <= 1
        }
        val sameGradePos = candidates.filter { it.grade == correct.grade && it.pos == correct.pos }
        val sameGrade = candidates.filter { it.grade == correct.grade }
        val samePos = candidates.filter { it.pos == correct.pos }
        val sameHead = candidates.filter { it.word.take(1).equals(correct.word.take(1), ignoreCase = true) }
        val lenNear = candidates.filter { abs(it.word.length - correct.word.length) <= 2 }

        val merged = (sameGradePosHeadLen + sameGradePos + sameGrade + samePos + sameHead + lenNear + candidates).distinct()
        val distractors = merged.shuffled().take(count - 1)
        return (distractors + correct).shuffled()
    }

    private fun buildChoicesListening(correct: WordEntity, pool: List<WordEntity>, count: Int): List<WordEntity> {
        if (pool.isEmpty()) return listOf(correct)
        val candidates = pool.filter { it.no != correct.no }

        val sameGradeHeadLen = candidates.filter {
            it.grade == correct.grade &&
                    it.word.take(1).equals(correct.word.take(1), ignoreCase = true) &&
                    abs(it.word.length - correct.word.length) <= 1
        }
        val sameGrade = candidates.filter { it.grade == correct.grade }
        val sameHead = candidates.filter { it.word.take(1).equals(correct.word.take(1), ignoreCase = true) }
        val lenNear = candidates.filter { abs(it.word.length - correct.word.length) <= 2 }

        val merged = (sameGradeHeadLen + sameGrade + sameHead + lenNear + candidates).distinct()
        val distractors = merged.shuffled().take(count - 1)
        return (distractors + correct).shuffled()
    }

    private fun formatQuestionAndOptions(
        correct: WordEntity,
        choices: List<WordEntity>,
        mode: String
    ): Triple<String, String, List<String>> {
        return if (mode == "meaning") {
            Triple("この英単語の意味は？", correct.word, choices.map { it.japanese })
        } else {
            Triple("音声を聞いて正しい英単語を選んでください", "", choices.map { it.word })
        }
    }

    private fun onChoiceSelected(selectedIndex: Int) {
        val cw = currentWord ?: return

        val selectedText = choiceButtons.getOrNull(selectedIndex)?.text?.toString() ?: return
        val isCorrect = if (currentMode == "meaning") selectedText == cw.japanese else selectedText == cw.word

        // 連打防止
        choiceButtons.forEach { it.isClickable = false }

        // 正解は必ず緑
        if (currentCorrectIndex in choiceButtons.indices) {
            ViewCompat.setBackgroundTintList(choiceButtons[currentCorrectIndex], greenTint)
        }
        // 不正解なら押したものを赤
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
            val zone = settings.getAppZoneId()
            val todayEpochDay = LocalDate.now(zone).toEpochDay()

            val current = progressDao.getProgress(wordId, currentMode)
            val currentLevel = current?.level ?: 0
            val newCount = (current?.studyCount ?: 0) + 1  // ★学習回数を+1

            val (newLevel, nextDueAtSec) = calcNextDueAtSec(isCorrect, currentLevel, nowSec)
            Log.d("DUE_CALC", "wordId=$wordId mode=$currentMode")

            val addPoint = ProgressCalculator.calcPoint(isCorrect, currentLevel)
            pointManager.add(addPoint)

            if (addPoint > 0) {
                db.pointHistoryDao().insert(
                    PointHistoryEntity(
                        mode = currentMode,
                        dateEpochDay = todayEpochDay,
                        delta = addPoint
                    )
                )
            }

            val updated = WordProgressEntity(
                wordId = wordId,
                mode = currentMode,
                level = newLevel,
                nextDueAtSec = nextDueAtSec,
                lastAnsweredAt = System.currentTimeMillis(),
                studyCount = newCount                    // ★学習回数を保存
            )
            progressDao.upsert(updated)

            updateStudyStatsView()
            showFeedbackSnackbar(isCorrect, addPoint)

            Log.d(
                "ANSWER_TEST",
                "wordId=$wordId isCorrect=$isCorrect addPoint=$addPoint newLevel=$newLevel nextDueAtSec=$nextDueAtSec totalPoint=${pointManager.getTotal()} studyCount=$newCount"
            )

            updatePointView()

            delay(settings.answerIntervalMs)
            loadNextQuestion()
        }
    }

    private fun showFeedbackSnackbar(isCorrect: Boolean, addPoint: Int) {
        val bgColor = ContextCompat.getColor(
            this,
            if (isCorrect) R.color.snackbar_correct_bg else R.color.snackbar_wrong_bg
        )

        val msg = if (isCorrect) {
            val praise = praiseMessages.random()
            "$praise +${addPoint}pt"
        } else {
            "不正解…"
        }

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
        textPoints.text = "ポイント: $total"
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
        val diffAbs = kotlin.math.abs(diff)

        textPointStats.text = "今日: $todaySum / 前日比: $diffSign$diffAbs"
    }

    private fun speakCurrentWord() {
        val cw = currentWord ?: return
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, settings.ttsVolume) // 0.0..1.0
        }
        tts?.speak(cw.word, TextToSpeech.QUEUE_FLUSH, params, "tts_id")
    }
}