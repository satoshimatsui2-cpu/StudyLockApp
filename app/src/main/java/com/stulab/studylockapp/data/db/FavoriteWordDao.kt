package com.stulab.studylockapp.data.db

import androidx.room.*
import com.stulab.studylockapp.data.WordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteWordDao {

    /**
     * お気に入りに追加。
     * すでに登録済みの場合は何もしない（createdAtを維持する）。
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(favorite: FavoriteWordEntity)

    /**
     * お気に入りから削除。
     */
    @Query("DELETE FROM favorite_words WHERE wordId = :wordId")
    suspend fun delete(wordId: Int)

    /**
     * 指定された単語がお気に入りかどうかを確認。
     */
    @Query("SELECT EXISTS(SELECT 1 FROM favorite_words WHERE wordId = :wordId)")
    suspend fun isFavorite(wordId: Int): Boolean

    /**
     * お気に入り登録された単語の一覧を、登録が新しい順に取得。
     * INNER JOINにより、wordsテーブルに存在する単語のみを対象とする。
     */
    @Query("""
        SELECT w.* FROM words w
        INNER JOIN favorite_words f ON w.no = f.wordId
        ORDER BY f.createdAt DESC
    """)
    fun getFavoriteWordsFlow(): Flow<List<WordEntity>>

    /**
     * お気に入りの合計件数を取得。
     * wordsテーブルに存在する（表示可能な）単語のみをカウントする。
     */
    @Query("""
        SELECT COUNT(*) FROM favorite_words f
        INNER JOIN words w ON w.no = f.wordId
    """)
    fun getFavoriteCountFlow(): Flow<Int>
}
