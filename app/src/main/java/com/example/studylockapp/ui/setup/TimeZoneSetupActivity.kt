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

    private companion object {
        private const val DEFAULT_ZONE_ID = "Asia/Tokyo"
    }

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

        val storedId = settings.appTimeZoneId
        val initialIndex = if (storedId.isNullOrBlank()) {
            // 未設定なら Tokyo を初期選択
            TimeZoneOptions.indexOfOrZero(DEFAULT_ZONE_ID)
        } else {
            TimeZoneOptions.indexOfOrZero(storedId)
        }
        spinner.setSelection(initialIndex)

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