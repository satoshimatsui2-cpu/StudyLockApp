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

        // すでにデータが入っていればスキップ
        val count = dao.getAll().size
        if (count > 0) return

        // res/raw/words.csv を読む
        val input = context.resources.openRawResource(R.raw.words)
        val reader = BufferedReader(InputStreamReader(input))
        val list = mutableListOf<WordEntity>()
        var line: String?
        var isFirst = true
        while (reader.readLine().also { line = it } != null) {
            val row = line!!.trim()
            if (row.isEmpty()) continue
            if (isFirst) { // ヘッダ行をスキップ
                isFirst = false
                continue
            }
            val cols = row.split(",")
            if (cols.size < 6) continue
            // CSV列: no, grade, word, japanese, pos, category
            val entity = WordEntity(
                no = cols[0].toInt(),
                grade = cols[1],
                word = cols[2],
                japanese = cols[3],
                pos = cols[4],
                category = cols[5]
            )
            list.add(entity)
        }
        reader.close()

        dao.insertAll(list)
        Log.d("CSV_IMPORT", "Imported ${list.size} rows from CSV")
    }
}

