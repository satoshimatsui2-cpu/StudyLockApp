package com.example.studylockapp.data

import android.content.Context
import android.util.Log
import com.example.studylockapp.R
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

            // ヘッダスキップ
            if (isFirst) {
                isFirst = false
                continue
            }

            val cols = parseCsvLine(row)
            if (cols.size < 8) {
                Log.w("CSV_IMPORT", "skip row (col size < 8): size=${cols.size} row=$row")
                continue
            }

            try {
                list.add(
                    WordEntity(
                        no = cols[0].toInt(),
                        grade = cols[1],
                        word = cols[2],
                        japanese = cols[3],
                        english = cols[4],
                        pos = cols[5],
                        category = cols[6],
                        actors = cols[7]
                    )
                )
            } catch (e: Exception) {
                Log.w("CSV_IMPORT", "skip row (parse error): row=$row", e)
            }
        }

        reader.close()

        dao.insertAll(list)
        Log.d("CSV_IMPORT", "Imported ${list.size} rows from CSV")
    }

    /**
     * ダブルクォート対応の簡易CSVパーサ
     * - "..." で囲まれた中のカンマは区切りとして扱わない
     * - "" は " を意味する
     */
    private fun parseCsvLine(line: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < line.length) {
            val c = line[i]
            when (c) {
                '"' -> {
                    // "" は " を表す
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ',' -> {
                    if (inQuotes) {
                        sb.append(c)
                    } else {
                        out.add(sb.toString().trim())
                        sb.setLength(0)
                    }
                }
                else -> sb.append(c)
            }
            i++
        }
        out.add(sb.toString().trim())
        return out
    }
}