package com.example.studylockapp.data.notification

/**
 * キャラクターの感情パターン定義
 */
enum class CharacterEmotion(val id: String) {
    JOY("joy"),           // 喜
    ANGER("anger"),       // 怒
    SORROW("sorrow"),     // 哀
    PLEASURE("pleasure"), // 楽
    SURPRISE("surprise"), // 驚き
    BLUSH("blush"),       // 照れ
    PANIC("panic"),       // 焦り
    INSPIRE("inspire")    // 鼓舞
}
