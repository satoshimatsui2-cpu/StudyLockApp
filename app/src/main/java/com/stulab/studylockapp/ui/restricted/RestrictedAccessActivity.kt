package com.stulab.studylockapp.ui.restricted

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.stulab.studylockapp.R

class RestrictedAccessActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var homeRunnable: Runnable? = null
    private var isFinishingHome = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0) // ★アニメ無し
        setContentView(R.layout.activity_restricted_access)

        // 閉じるボタン（手動で押した場合もホームへ）
        findViewById<Button>(R.id.button_go_back)?.setOnClickListener {
            goHomeAndFinish()
        }

        // ★調整: 0.8秒 (800ms) だけ表示して、ホームへ飛ばす
        homeRunnable = Runnable { goHomeAndFinish() }
        homeRunnable?.let { handler.postDelayed(it, 800) }
    }

    private fun goHomeAndFinish() {
        if (isFinishingHome) return
        isFinishingHome = true

        // 予約済みの実行があればキャンセル
        homeRunnable?.let { handler.removeCallbacks(it) }

        // 1. ホーム画面（ランチャー）を呼び出す
        // これにより、裏にある「設定画面」からフォーカスを外します（無限ループ防止）
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)

        // 2. この警告画面を閉じる
        finish()
    }

    override fun onDestroy() {
        homeRunnable?.let { handler.removeCallbacks(it) }
        super.onDestroy()
    }

    override fun onBackPressed() {
        // super.onBackPressed() は呼ばずにホームへ飛ばす
        goHomeAndFinish()
    }
}