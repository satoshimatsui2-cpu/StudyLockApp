package com.example.studylockapp.data

import android.content.Context
import android.util.Log
import com.example.studylockapp.R   // ★ これを追加
import java.io.BufferedReader
import java.io.InputStreamReader

object CsvImporter {
    suspend fun importIfNeeded(context: Context) {
        val db = AppDatabase.getInstance(context)
        val dao = db.wordDao()

        // 空でなければスキップ
        val existing = dao.getAll().size
        if (existing > 0) {
            Log.d("CSV_IMPORT", "already imported ($existing rows), skip")
            return
        }

        val input = context.resources.openRawResource(R.raw.words)
        val reader = BufferedReader(InputStreamReader(input))
        val list = mutableListOf<WordEntity>()
        var line: String?
        var isFirst = true
        while (reader.readLine().also { line = it } != null) {
            val row = line!!.trim()
            if (row.isEmpty()) continue
            if (isFirst) { isFirst = false; continue } // ヘッダスキップ

            val cols = row.split(",")
            if (cols.size < 6) {
                Log.w("CSV_IMPORT", "skip row (col size < 6): $row")
                continue
            }
            list.add(
                WordEntity(
                    no = cols[0].toInt(),
                    grade = cols[1],
                    word = cols[2],
                    japanese = cols[3],
                    pos = cols[4],
                    category = cols[5]
                )
            )
        }
        reader.close()

        dao.insertAll(list)
        Log.d("CSV_IMPORT", "Imported ${list.size} rows from CSV")
    }
}

