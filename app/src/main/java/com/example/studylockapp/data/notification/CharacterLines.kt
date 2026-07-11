package com.example.studylockapp.data.notification

/**
 * 応援キャラクターの定義
 */
enum class StudyCharacter(val id: String, val displayName: String, val unlockGoalDays: Int) {
    GEORGE("george", "ジョージ", 0),
    ATSUSHI("atsushi", "アツシ（熱血）", 10),
    LUNA("luna", "ルナ（ツンデレ）", 20),
    ARTHUR("arthur", "アーサー（王子様）", 30);

    companion object {
        fun fromId(id: String): StudyCharacter = values().find { it.id == id } ?: GEORGE
    }
}

/**
 * 通知の状況
 */
enum class NotificationContext {
    MORNING_NORMAL,    // 朝：通常
    MORNING_STREAK,    // 朝：継続中
    MORNING_MISSED_1,  // 朝：1日サボり
    MORNING_MISSED_2,  // 朝：数日サボり
    EVENING_PENDING,   // 夕方：未達成
    GOAL_COMPLETED     // ★目標達成！
}

/**
 * キャラクターごとのセリフ集
 */
object CharacterLines {

    private val lines = mapOf(
        StudyCharacter.GEORGE to mapOf(
            NotificationContext.MORNING_NORMAL to listOf(
                "おはようございます！今日は目標に向かって一緒に頑張りませんか？",
                "新しい1日が始まりましたね。今日も一歩ずつ進んでみましょう。"
            ),
            NotificationContext.MORNING_STREAK to listOf(
                "昨日で目標達成%d日連続です！すごすぎます...！今日もこの調子でいきましょう！",
                "%d日も続いているなんて尊敬します。今日もジョージと一緒に頑張りましょう！"
            ),
            NotificationContext.MORNING_MISSED_1 to listOf(
                "おはようございます。昨日はお忙しかったですか？今日はまた心機一転、始めてみませんか？",
                "昨日できなかった分は、今日少しずつ取り戻せば大丈夫ですよ。応援しています！"
            ),
            NotificationContext.MORNING_MISSED_2 to listOf(
                "しばらくお顔を見ていないので寂しいです...。1問だけでもいいので、遊びに来てくださいね。",
                "ジョージはずっと待っていますよ。少しだけ単語を眺めるだけでも全然違いますよ！"
            ),
            NotificationContext.EVENING_PENDING to listOf(
                "お疲れ様です。今日もちょっとだけ頑張ってみませんか？",
                "休憩のついでに、3分だけ勉強タイムにしちゃいましょう！",
                "今からやれば、夜はゆっくり過ごせますよ。今のうちに少し進めませんか？"
            ),
            NotificationContext.GOAL_COMPLETED to listOf(
                "素晴らしいです！本日の目標をすべて達成されましたね。心から尊敬いたします！",
                "おめでとうございます！今日のクエストは全て完了です。ゆっくり休んでくださいね。"
            )
        ),
        StudyCharacter.ATSUSHI to mapOf(
            NotificationContext.MORNING_NORMAL to listOf(
                "おい！！魂燃やしてるか！？今日という日は今しかないんだぞ！！",
                "起きろ！！最高の1日の始まりだ！目標に向かって突き進むぞ！！"
            ),
            NotificationContext.MORNING_STREAK to listOf(
                "昨日で%d日連続か！お前の根性、本物だな！！今日も限界を突破するぞ！！",
                "熱い、熱すぎるぞお前！！%d日連続達成だ！このまま突っ走るぞ！！"
            ),
            NotificationContext.MORNING_MISSED_1 to listOf(
                "昨日はどうした！？立ち止まってる暇はないぞ！今日からまた爆走開始だ！！",
                "1日の遅れなど気にするな！今日のお前なら昨日の分までやれるはずだ！！"
            ),
            NotificationContext.MORNING_MISSED_2 to listOf(
                "いつまで寝てるんだ！お前の夢はそんなもんか！？今すぐ戻ってこい！！",
                "寂しいなんて言わせるな！お前の情熱が必要なんだ！1問でいい、魂をぶつけろ！！"
            ),
            NotificationContext.EVENING_PENDING to listOf(
                "まだ目標が終わってないだと！？今すぐエンジン全開で取り掛かるぞ！！",
                "夕方のこの時間が勝負だ！！お前の底力を見せてみろ！！",
                "やるか、やるかだ！！迷わずアプリを開け！！"
            ),
            NotificationContext.GOAL_COMPLETED to listOf(
                "やり遂げたな！！お前の努力、俺は一生忘れねえぞ！！最高に熱い一日だったな！！",
                "目標達成だぁぁ！！お前の根性に乾杯だ！！明日もこの調子で魂燃やしていこうぜ！！"
            )
        ),
        StudyCharacter.LUNA to mapOf(
            NotificationContext.MORNING_NORMAL to listOf(
                "おはよう。別にアンタが今日サボってもいいけど、目標くらいは意識しなさいよね。",
                "ほら、朝よ。さっさと準備して勉強始めなさいよ。見ててあげるから。"
            ),
            NotificationContext.MORNING_STREAK to listOf(
                "昨日で%d日連続？ふん、まあまあってところね。今日も続けなさいよ、アンタならできるでしょ。",
                "%d日も続いてるの？...ちょっとは見直したわよ。今日も期待してるんだから。"
            ),
            NotificationContext.MORNING_MISSED_1 to listOf(
                "昨日は何してたのよ。別に心配してたわけじゃないけど...今日はちゃんとやりなさいよね！",
                "1日休んだくらいで諦める気？そんなのルナが許さないんだから。ほら、再開よ！"
            ),
            NotificationContext.MORNING_MISSED_2 to listOf(
                "ちょっと、何日サボってるのよ！アンタがいないと...その、退屈じゃない。早く戻ってきなさいよね！",
                "1問だけでいいから顔見せなさいよ。別に、寂しいわけじゃないんだからね！"
            ),
            NotificationContext.EVENING_PENDING to listOf(
                "まだ目標終わってないの？アンタ、本当に手がかかるわね。ほら、今のうちに終わらせなさい！",
                "ちょっと休憩？その前に少しは勉強したら？効率悪いわよ。",
                "夜に慌てたくないでしょ？今のうちにやりなさいよね、もう。"
            ),
            NotificationContext.GOAL_COMPLETED to listOf(
                "ふん、やるじゃない。まあ、アンタならこれくらい当然よね。...明日も、ちゃんとやりなさいよ！",
                "目標達成おめでとう。別に、アンタが頑張ってて嬉しいわけじゃないんだからね！勘違いしないでよ！"
            )
        ),
        StudyCharacter.ARTHUR to mapOf(
            NotificationContext.MORNING_NORMAL to listOf(
                "おはよう、愛しき学習者よ。君の知的な姿を今日も見せてくれるかな？",
                "新しい朝だ。君の美しい努力が実を結ぶよう、今日も寄り添わせてもらいたい。"
            ),
            NotificationContext.MORNING_STREAK to listOf(
                "昨日で目標達成%d日連続か。君の気高さには、月も恥じらうだろう。今日も共に歩もう。",
                "%d日もの間、君の輝きを見せてもらえるなんて光栄だ。今日も素晴らしい1日にしよう。"
            ),
            NotificationContext.MORNING_MISSED_1 to listOf(
                "昨日は姿が見えなかったけれど、体調でも崩したのかい？今日はまた君の笑顔を見せておくれ。",
                "昨日のことは気にしなくていい。今日という新しいページに、君の努力を刻もう。"
            ),
            NotificationContext.MORNING_MISSED_2 to listOf(
                "君のいない時間は、まるで光を失った世界のようだ。一目だけでも、君の頑張りを見せてほしいな。",
                "ずっと待っていたよ。君が戻ってきてくれるだけで、僕の心は満たされる。1問だけでもいいんだ。"
            ),
            NotificationContext.EVENING_PENDING to listOf(
                "お疲れ様、僕のプリンス/プリンセス。今日も少しだけ、君の情熱を分けてくれないか？",
                "忙しいかい？ほんの数分でいい、君の知性に触れる時間をくれないかな。",
                "今から始めれば、安らかな夜が約束されるだろう。さあ、一緒に進もうか。"
            ),
            NotificationContext.GOAL_COMPLETED to listOf(
                "完璧だ...。君のひたむきな姿に、心から敬意を表するよ。今夜は良い夢が見られそうだね。",
                "目標達成、おめでとう。君の努力という名の宝石が、また一つ輝きを増したね。美しいよ。"
            )
        )
    )

    fun getLine(character: StudyCharacter, context: NotificationContext, streak: Int = 0): String {
        val characterLines = lines[character] ?: lines[StudyCharacter.GEORGE]!!
        val contextLines = characterLines[context] ?: characterLines[NotificationContext.MORNING_NORMAL]!!
        val line = contextLines.random()
        return if (line.contains("%d")) {
            String.format(line, streak)
        } else {
            line
        }
    }
}
