package com.stulab.studylockapp.data

/**
 * TSV行をパースするためのクラス。
 * 引用符で囲まれたフィールドや、内部の二重引用符エスケープ ("") を処理します。
 */
object TsvParser {
    fun parseLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val fields = line.split('\t')
        
        for (rawField in fields) {
            var field = rawField.trim()
            
            // Excel-style quoting: If it starts and ends with double quotes
            if (field.startsWith("\"") && field.endsWith("\"") && field.length >= 2) {
                // Strip outer quotes
                field = field.substring(1, field.length - 1)
                // Replace escaped double quotes "" with a single "
                field = field.replace("\"\"", "\"")
            }
            
            result.add(field)
        }

        return result
    }
}
