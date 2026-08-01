package com.stulab.studylockapp.ui.wordbook

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.stulab.studylockapp.R
import com.stulab.studylockapp.databinding.ActivityMyWordBookBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MyWordBookActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMyWordBookBinding
    private val viewModel: MyWordBookViewModel by viewModels {
        MyWordBookViewModelFactory(this)
    }

    private lateinit var adapter: MyWordBookSlotAdapter
    private var pendingImportGrade: Int? = null

    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val grade = pendingImportGrade
        if (uri != null && grade != null) {
            val fileName = getFileName(uri)
            val initialName = fileName?.substringBeforeLast(".")?.take(30)
            viewModel.importTsv(this, uri, grade, initialName)
        }
        pendingImportGrade = null
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
            cursor.use { c ->
                if (c != null && c.moveToFirst()) {
                    val index = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) result = c.getString(index)
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMyWordBookBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Status bar & Display cutout handling
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupToolbar()
        setupListeners()
        setupRecyclerView()
        observeViewModel()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupListeners() {
        binding.buttonFormatGuide.setOnClickListener {
            showFormatGuideDialog()
        }
    }

    private fun showFormatGuideDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_my_word_book_guide, null)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.my_word_book_format_guide_title)
            .setView(view)
            .create()

        view.findViewById<View>(R.id.button_copy_header).setOnClickListener {
            copyToClipboard("no\tgrade\tword\tmeaning\tdefinition\texample\texampleMeaning\tpartOfSpeech\tdifficulty\tfrequency\tchoicesEnJa\tchoicesJaEn\tchoicesListening\tsynonyms\tantonyms")
        }

        view.findViewById<View>(R.id.button_copy_prompt).setOnClickListener {
            copyToClipboard(getString(R.string.my_word_book_prompt_content))
        }

        view.findViewById<View>(R.id.button_guide_close).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("MyWordBook", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "コピーしました", Toast.LENGTH_SHORT).show()
    }

    private fun setupRecyclerView() {
        adapter = MyWordBookSlotAdapter(
            onImportClick = { slot ->
                if (slot.isRegistered) {
                    showReplaceConfirmDialog(slot)
                } else {
                    startFilePicker(slot.grade)
                }
            },
            onDeleteClick = { slot ->
                showDeleteConfirmDialog(slot)
            },
            onRenameClick = { slot ->
                showRenameDialog(slot)
            }
        )
        binding.recyclerSlots.layoutManager = LinearLayoutManager(this)
        binding.recyclerSlots.adapter = adapter
    }

    private fun showRenameDialog(slot: WordBookSlot) {
        val padding = (24 * resources.displayMetrics.density).toInt()
        val inputLayout = TextInputLayout(this).apply {
            setPadding(padding, (8 * resources.displayMetrics.density).toInt(), padding, 0)
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            hint = "単語帳名"
            // ダイアログの背景色に合わせる
            boxBackgroundColor = ContextCompat.getColor(context, R.color.dialog_surface)
        }
        val editText = TextInputEditText(inputLayout.context).apply {
            setText(slot.customName ?: slot.displayName)
            setSelection(text?.length ?: 0)
            filters = arrayOf(android.text.InputFilter.LengthFilter(30))
            maxLines = 1
            isSingleLine = true
            // テキスト色を明示的に設定
            setTextColor(ContextCompat.getColor(context, R.color.text_main))
        }
        inputLayout.addView(editText)

        MaterialAlertDialogBuilder(this)
            .setTitle("単語帳名の変更")
            .setView(inputLayout)
            .setPositiveButton("保存") { _, _ ->
                viewModel.renameWordBook(slot.grade, editText.text.toString())
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun startFilePicker(grade: Int) {
        pendingImportGrade = grade
        openDocumentLauncher.launch(
            arrayOf(
                "text/tab-separated-values",
                "text/plain",
                "application/octet-stream"
            )
        )
    }

    private fun showReplaceConfirmDialog(slot: WordBookSlot) {
        MaterialAlertDialogBuilder(this)
            .setTitle("「${slot.displayName}」を入れ替えますか？")
            .setMessage(com.stulab.studylockapp.R.string.my_word_book_replace_confirm_message)
            .setPositiveButton("入れ替える") { _, _ -> startFilePicker(slot.grade) }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun showDeleteConfirmDialog(slot: WordBookSlot) {
        MaterialAlertDialogBuilder(this)
            .setTitle("「${slot.displayName}」を削除しますか？")
            .setMessage(com.stulab.studylockapp.R.string.my_word_book_delete_confirm_message)
            .setPositiveButton("削除する") { _, _ -> viewModel.deleteWordBook(slot.grade) }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { state ->
                    adapter.submitList(state.slots)
                    adapter.setInteractionEnabled(state.processingGrade == null && !state.isLoading)
                    
                    binding.layoutLoading.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                    
                    state.successMessage?.let {
                        Toast.makeText(this@MyWordBookActivity, it, Toast.LENGTH_SHORT).show()
                        viewModel.clearMessages()
                    }
                    
                    state.errorMessage?.let {
                        val message = if (state.validationErrors.isNotEmpty()) {
                            val list = if (state.validationErrors.size > 10) {
                                state.validationErrors.take(10) + "...残り${state.validationErrors.size - 10}件"
                            } else {
                                state.validationErrors
                            }
                            it + "\n\n" + list.joinToString("\n")
                        } else {
                            it
                        }

                        MaterialAlertDialogBuilder(this@MyWordBookActivity)
                            .setTitle("エラー")
                            .setMessage(message)
                            .setPositiveButton("OK", null)
                            .show()
                        viewModel.clearMessages()
                    }
                }
            }
        }
    }
}
