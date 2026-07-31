package com.stulab.studylockapp.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * お気に入り（My単語帳）単語を管理するエンティティ。
 * 単語マスタ（wordsテーブル）の削除に影響されないよう、外部キー制約は持たせない。
 */
@Entity(tableName = "favorite_words")
data class FavoriteWordEntity(
    @PrimaryKey val wordId: Int,
    val createdAt: Long
)
