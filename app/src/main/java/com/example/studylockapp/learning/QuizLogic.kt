package com.example.studylockapp.learning

object QuizLogic {
    /**
     * 4択の選択肢を生成する（重複排除・ランダム補充）
     */
    fun createChoices(correct: String, distractors: List<String>): List<String> {
        val choices = mutableSetOf<String>()
        
        // 1. 正解を追加
        choices.add(correct)
        
        // 2. 候補リストからクリーニングして追加
        distractors
            .filter { it.isNotBlank() && it != correct }
            .distinct()
            .shuffled()
            .take(3)
            .forEach { choices.add(it) }
            
        // 3. 4つに満たない場合はさらに補充
        if (choices.size < 4) {
            distractors.filter { it != correct }.shuffled().forEach {
                if (choices.size < 4) choices.add(it)
            }
        }
        
        return choices.toList().shuffled()
    }
}
