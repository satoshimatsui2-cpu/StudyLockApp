package com.example.studylockapp.ui.restricted

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.studylockapp.R

class RestrictedAccessActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_restricted_access)

        // 閉じるボタン（手動で押した場合もホームへ）
        findViewById<Button>(R.id.button_go_back)?.setOnClickListener {
            goHomeAndFinish()
        }

        // ★調整: 0.6秒 (600ms) だけ表示して、ホームへ飛ばす
        // これなら「警告画面が出た！」と認識でき、かつ操作はできません。
        Handler(Looper.getMainLooper()).postDelayed({
            goHomeAndFinish()
        }, 800)
    }

    private fun goHomeAndFinish() {
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

    override fun onBackPressed() {
        super.onBackPressed()
        goHomeAndFinish() // 戻るボタンでもホームへ飛ばす
    }
}