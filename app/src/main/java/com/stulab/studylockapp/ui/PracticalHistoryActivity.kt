package com.stulab.studylockapp.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.stulab.studylockapp.GradeLabelFormatter
import com.stulab.studylockapp.R
import com.stulab.studylockapp.data.db.PracticalHistoryEntity
import com.stulab.studylockapp.databinding.ActivityPracticalHistoryBinding
import com.stulab.studylockapp.databinding.DialogPracticalHistoryDetailBinding
import com.stulab.studylockapp.learning.practical.PracticalListeningTtsController
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 実践テストの解答履歴を一覧表示し、カスタムビューのダイアログで
 * 詳細を確認（音声再生含む）できるActivity
 */
class PracticalHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPracticalHistoryBinding
    private val viewModel: PracticalHistoryViewModel by viewModels()
    private lateinit var adapter: PracticalHistoryAdapter

    private var ttsController: PracticalListeningTtsController? = null
    private var currentPeriod: Int = 0 // 0: Daily, 1: Weekly

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityPracticalHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentPeriod = intent.getIntExtra("EXTRA_PERIOD", 0)

        // Status bar & Display cutout handling
        val initialAppBarTopPadding = binding.appBar.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            binding.appBar.updatePadding(top = initialAppBarTopPadding + bars.top)
            insets
        }

        // TTSコントローラの初期化
        ttsController = PracticalListeningTtsController(this)

        setupNavigationTabs()
        setupPeriodButtons()
        setupFilters()
        setupRecyclerView()
        observeViewModel()

        viewModel.loadHistory("ALL")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val period = intent.getIntExtra("EXTRA_PERIOD", currentPeriod)
        if (period != currentPeriod) {
            currentPeriod = period
            updatePeriodUI()
        }
    }

    override fun onDestroy() {
        ttsController?.shutdown()
        super.onDestroy()
    }

    private fun setupNavigationTabs() {
        binding.layoutTabs.buttonSwitchHistory.setOnClickListener {
            Log.d("HistoryTabs", "Switch to Word clicked")
            val intent = Intent(this, LearningHistoryActivity::class.java).apply {
                putExtra("EXTRA_PERIOD", currentPeriod)
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
            startActivity(intent)
            overridePendingTransition(0, 0)
        }

        binding.layoutTabs.textSwitchLabel.text = getString(R.string.history_label_word)
        binding.layoutTabs.buttonSwitchHistory.contentDescription = getString(R.string.history_cd_switch_word)
    }

    private fun setupPeriodButtons() {
        updatePeriodUI()

        binding.layoutTabs.buttonDaily.setOnClickListener {
            if (currentPeriod != 0) {
                currentPeriod = 0
                updatePeriodUI()
                // 実践テスト側で期間フィルタが必要な場合はここでViewModelを呼ぶ
            }
        }

        binding.layoutTabs.buttonWeekly.setOnClickListener {
            if (currentPeriod != 1) {
                currentPeriod = 1
                updatePeriodUI()
                // 実践テスト側で期間フィルタが必要な場合はここでViewModelを呼ぶ
            }
        }
    }

    private fun updatePeriodUI() {
        val selectedBg = R.drawable.bg_badge_navy_soft
        val unselectedBg = R.drawable.sl_button_bg
        val selectedTextColor = ContextCompat.getColor(this, R.color.navy_primary)
        val unselectedTextColor = ContextCompat.getColor(this, R.color.text_sub)

        binding.layoutTabs.buttonDaily.apply {
            setBackgroundResource(if (currentPeriod == 0) selectedBg else unselectedBg)
            setTextColor(if (currentPeriod == 0) selectedTextColor else unselectedTextColor)
            isSelected = currentPeriod == 0
        }

        binding.layoutTabs.buttonWeekly.apply {
            setBackgroundResource(if (currentPeriod == 1) selectedBg else unselectedBg)
            setTextColor(if (currentPeriod == 1) selectedTextColor else unselectedTextColor)
            isSelected = currentPeriod == 1
        }
    }

    private fun setupFilters() {
        binding.filterChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val filter = when (checkedIds.firstOrNull()) {
                R.id.chip_filter_wrong -> "WRONG"
                R.id.chip_filter_unscored -> "UNSCORED"
                else -> "ALL"
            }
            viewModel.loadHistory(filter)
        }
    }

    private fun setupRecyclerView() {
        adapter = PracticalHistoryAdapter { item ->
            // タップ時に詳細ダイアログを表示
            showHistoryDetailDialog(item)
        }
        binding.recyclerPracticalHistory.apply {
            layoutManager = LinearLayoutManager(this@PracticalHistoryActivity)
            adapter = this@PracticalHistoryActivity.adapter
        }
    }

    /**
     * カスタムビューを使用した詳細ダイアログを表示します。
     */
    private fun showHistoryDetailDialog(item: PracticalHistoryEntity) {
        val dialogBinding = DialogPracticalHistoryDetailBinding.inflate(layoutInflater)
        val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
        val typeName = if (item.questionType == "LISTENING") "リスニング" else "穴埋め"
        val gradeLabel = GradeLabelFormatter.format(item.grade)

        Log.d("PracticalHistoryActivity", "detail questionType=${item.questionType}, no=${item.questionNo}, snapshot=${!item.ttsScriptSnapshot.isNullOrBlank()}")

        // メタ情報と判定表示
        dialogBinding.textDetailMeta.text = "${typeName}問題 ($gradeLabel) / ${dateFormat.format(Date(item.answeredAt))}"
        
        val statusText = when (item.resultStatus) {
            "CORRECT" -> "正解"
            "WRONG" -> "不正解"
            else -> "採点対象外"
        }
        dialogBinding.textDetailStatus.text = "判定: $statusText (${if (item.points > 0) "+" else ""}${item.points} pt)"

        // 問題内容: textDetailQuestion には設問文(questionText)を表示
        dialogBinding.textDetailQuestion.text = item.questionText

        dialogBinding.textDetailYourAnswer.text = "あなたの答え: ${item.selectedAnswer}"
        dialogBinding.textDetailCorrectAnswer.text = "正解: ${item.correctAnswer}"
        dialogBinding.textDetailExplanation.text = item.explanation

        // スクリプトの解決 (スナップショット優先、なければTSVからフォールバック)
        val script = resolveListeningScript(item)

        // リスニング専用UIの構築（スクリプト表示と再生ボタン）
        if (script != null) {
            Log.d("PracticalHistoryActivity", "show listening script area no=${item.questionNo}")
            dialogBinding.layoutListeningScript.visibility = View.VISIBLE
            
            val container = dialogBinding.containerHistoryScriptLines
            container.removeAllViews()
            
            // スクリプト行の生成（1行ずつ TextView として追加）
            val segments = ttsController?.parseScript(script) ?: emptyList()
            segments.forEach { segment ->
                if (segment.displayText.isBlank()) return@forEach
                // 同じID（Question分割など）は一度だけ表示
                if (container.findViewWithTag<View>(segment.id) != null) return@forEach
                
                val tv = TextView(this).apply {
                    tag = segment.id
                    text = segment.displayText
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    // ダークモード対策：文字色を黒系に固定
                    setTextColor(Color.parseColor("#212121"))
                    setPadding(dp(8), dp(4), dp(8), dp(4))
                }
                container.addView(tv)
            }
            
            // TTSハイライト制御の登録
            ttsController?.onSegmentStart = { id ->
                for (i in 0 until container.childCount) {
                    val v = container.getChildAt(i) as? TextView ?: continue
                    if (v.tag == id) {
                        v.setBackgroundResource(R.drawable.bg_badge_navy_soft)
                        v.setTypeface(null, Typeface.BOLD)
                    } else {
                        v.setBackgroundColor(Color.TRANSPARENT)
                        v.setTypeface(null, Typeface.NORMAL)
                    }
                }
            }
            
            ttsController?.onComplete = {
                // 再生完了時にハイライトをクリア
                for (i in 0 until container.childCount) {
                    val v = container.getChildAt(i) as? TextView ?: continue
                    v.setBackgroundColor(Color.TRANSPARENT)
                    v.setTypeface(null, Typeface.NORMAL)
                }
            }

            // 音声再生ボタンの動作設定 (復習用)
            dialogBinding.buttonPlayHistoryScript.setOnClickListener {
                ttsController?.play(script, item.grade)
            }
        } else {
            Log.d("PracticalHistoryActivity", "hide listening script area no=${item.questionNo}, type=${item.questionType}, hasSnapshot=${!item.ttsScriptSnapshot.isNullOrBlank()}")
            dialogBinding.layoutListeningScript.visibility = View.GONE
        }

        // ダイアログの生成
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setPositiveButton("閉じる", null)
            .setOnDismissListener { 
                // ダイアログを閉じたら再生を停止
                ttsController?.stop() 
            }
            .create()

        // 端末テーマに関わらず背景を白に固定
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(android.R.color.white)
        }
        
        dialog.show()
    }

    /**
     * リスニング台本を特定します。
     * スナップショットがない場合はアセット内のTSVから検索します。
     */
    private fun resolveListeningScript(item: PracticalHistoryEntity): String? {
        if (!item.ttsScriptSnapshot.isNullOrBlank()) return item.ttsScriptSnapshot
        if (item.questionType != "LISTENING") return null
        return findListeningScriptFromAssets(item.questionNo)
    }

    private fun findListeningScriptFromAssets(questionNo: String): String? {
        try {
            assets.open("practical/practical_listening_questions.tsv").use { inputStream ->
                val content = inputStream.bufferedReader().readText()
                val rows = parseTsv(content)
                // ヘッダーを飛ばして検索
                for (i in 1 until rows.size) {
                    val columns = rows[i]
                    // id(0), grade(1), part(2), tts_script(3)
                    if (columns.isNotEmpty() && columns[0].trim() == questionNo.trim()) {
                        if (columns.size > 3) {
                            val script = columns[3].replace("\\n", "\n").trim().removeSurrounding("\"")
                            Log.d("PracticalHistoryActivity", "fallback script loaded from TSV no=$questionNo")
                            return script
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PracticalHistoryActivity", "Error reading TSV fallback", e)
        }
        Log.d("PracticalHistoryActivity", "fallback script not found no=$questionNo")
        return null
    }

    /**
     * 簡易TSVパーサ。クォート内の改行やエスケープを扱います。
     */
    private fun parseTsv(content: String): List<List<String>> {
        val result = mutableListOf<List<String>>()
        var currentLine = mutableListOf<String>()
        var currentCell = StringBuilder()
        var inQuotes = false
        var i = 0
        
        while (i < content.length) {
            val c = content[i]
            val nextC = if (i + 1 < content.length) content[i + 1] else null

            when {
                c == '"' && inQuotes && nextC == '"' -> {
                    currentCell.append('"')
                    i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == '\t' && !inQuotes -> {
                    currentLine.add(currentCell.toString())
                    currentCell = StringBuilder()
                }
                (c == '\n' || c == '\r') && !inQuotes -> {
                    if (c == '\r' && nextC == '\n') i++
                    currentLine.add(currentCell.toString())
                    result.add(currentLine.toList())
                    currentLine = mutableListOf()
                    currentCell = StringBuilder()
                }
                else -> currentCell.append(c)
            }
            i++
        }
        
        if (currentLine.isNotEmpty() || currentCell.isNotEmpty()) {
            currentLine.add(currentCell.toString())
            result.add(currentLine.toList())
        }
        
        return result
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.historyItems.collectLatest { items ->
                        adapter.submitList(items)
                        binding.textEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.isLoading.collectLatest { isLoading ->
                        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
                    }
                }
            }
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
