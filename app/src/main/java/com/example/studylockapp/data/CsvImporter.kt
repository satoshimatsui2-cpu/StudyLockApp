package com.example.studylockapp.data

import android.content.Context
import android.util.Log
import com.example.studylockapp.R
import java.io.BufferedReader
import java.io.InputStreamReader

object CsvImporter {

    /**
     * 互換用: 全件インポート（DB が空のときのみ）
     */
    suspend fun importIfNeeded(context: Context) {
        val db = AppDatabase.getInstance(context)
        val dao = db.wordDao()

        // 空でなければスキップ
        val existing = dao.getAll().size
        if (existing > 0) {
            Log.d("CSV_IMPORT", "already imported ($existing rows), skip")
            return
        }

        val list = readCsv(context)
        dao.insertAll(list)
        Log.d("CSV_IMPORT", "Imported ${list.size} rows from CSV (all grades)")
    }

    /**
     * 指定グレードの行をまとめて挿入（既にある場合は PK=word で無視される想定）
     * @return 追加で挿入を試みた件数（重複は無視される可能性あり）
     */
    suspend fun importGradeIfNeeded(context: Context, grade: String): Int {
        if (grade == "All") return 0
        val db = AppDatabase.getInstance(context)
        val dao = db.wordDao()

        val csvRows = readCsv(context).filter { it.grade == grade }
        if (csvRows.isEmpty()) {
            Log.w("CSV_IMPORT", "grade=$grade に該当する行が CSV にありません")
            return 0
        }

        dao.insertAll(csvRows)
        Log.d("CSV_IMPORT", "grade=$grade inserted ${csvRows.size} rows (duplicates ignored by PK)")
        return csvRows.size
    }

    /**
     * CSV 全件読み込み（ヘッダ1行スキップ）
     */
    private fun readCsv(context: Context): List<WordEntity> {
        val list = mutableListOf<WordEntity>()
        context.resources.openRawResource(R.raw.words).use { input ->
            BufferedReader(InputStreamReader(input)).use { reader ->
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
            }
        }
        return list
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