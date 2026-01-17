package com.example.studylockapp.ui.restricted

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.studylockapp.R
import com.google.android.material.button.MaterialButton

class RestrictedAccessActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_restricted_access)

        findViewById<MaterialButton>(R.id.button_go_back).setOnClickListener {
            goHome()
        }
    }

    override fun onBackPressed() {
        goHome()
    }

    private fun goHome() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }
}
