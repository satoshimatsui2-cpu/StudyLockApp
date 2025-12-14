package com.example.studylockapp.ui.setup

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import com.example.studylockapp.R
import com.example.studylockapp.data.AppSettings
import com.example.studylockapp.ui.settings.TimeZoneOptions

class TimeZoneSetupActivity : AppCompatActivity() {

    private lateinit var settings: AppSettings
    private lateinit var spinner: Spinner
    private lateinit var buttonOk: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_time_zone_setup)

        settings = AppSettings(this)
        spinner = findViewById(R.id.spinner_time_zone)
        buttonOk = findViewById(R.id.button_time_zone_ok)

        // 候補は TimeZoneOptions に一本化
        spinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            TimeZoneOptions.displayList
        )

        // 現在値を反映（null/空なら 0番=端末の設定）
        spinner.setSelection(TimeZoneOptions.indexOfOrZero(settings.appTimeZoneId))

        buttonOk.setOnClickListener {
            val sel = spinner.selectedItem.toString()
            settings.setTimeZone(TimeZoneOptions.toZoneIdOrNull(sel)) // nullでも選択済みになる想定
            finish()
        }
    }

    override fun onBackPressed() {
        // 初回強制なら無効化（必要に応じて）
        // do nothing
    }
}