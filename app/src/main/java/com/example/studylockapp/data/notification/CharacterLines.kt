package com.example.studylockapp.data.notification

/**
 * 応援キャラクターの定義
 */
enum class StudyCharacter(val id: String, val displayName: String, val unlockGoalDays: Int) {
    GEORGE("george", "ジョージ", 0),
    ATSUSHI("atsushi", "アツシ（熱血）", 10),
    SHIN("shin", "シン（クール）", 20),      // ルナと入れ替え・20日
    LUNA("luna", "ルナ（ツンデレ）", 30),   // シンと入れ替え・30日
    HARU("haru", "ハル（元気）", 40),       // アーサーと入れ替え・40日
    ARTHUR("arthur", "アーサー（王子様）", 50), // ハルと入れ替え・50日
    ROBOSUKE("robosuke", "ロボ助（お世話）", 60),
    LEO("leo", "レオ（俺様）", 70);

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
    GOAL_COMPLETED,    // 目標達成！
    FRIEND_GOAL_MET    // 友達が達成した！
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
                "ジョージはずっと待っていますよ。少しだけ単語を眺めるだけでも全然違ういですよ！"
            ),
            NotificationContext.EVENING_PENDING to listOf(
                "お疲れ様です。今日もちょっとだけ頑張ってみませんか？",
                "休憩のついでに、3分だけ勉強タイムにしちゃいましょう！",
                "今からやれば、夜はゆっくり過ごせますよ。今のうちに少し進めませんか？"
            ),
            NotificationContext.GOAL_COMPLETED to listOf(
                "素晴らしいです！本日の目標をすべて達成されましたね。心から尊敬いたします！",
                "おめでとうございます！今日のクエストは全て完了です。ゆっくり休んでくださいね。"
            ),
            NotificationContext.FRIEND_GOAL_MET to listOf(
                "フレンドの%sさんが目標を達成されましたよ！素晴らしいですね、私たちも続きましょう！",
                "%sさんが本日の学習を完了したようです。いい刺激になりますね！"
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
            ),
            NotificationContext.FRIEND_GOAL_MET to listOf(
                "聞いたか！？%sの野郎、もう目標を達成しやがったぞ！負けてられねえ、俺たちも爆走だ！！",
                "お前の戦友、%sがやり遂げたぞ！！この熱い流れに乗って、お前も一気に終わらせるんだ！！"
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
            ),
            NotificationContext.FRIEND_GOAL_MET to listOf(
                "ちょっと、フレンドの%sさんはもう終わらせたみたいよ？アンタ、のんびりしてていいわけ？",
                "%sさん、目標達成したって。アンタも少しは見習ったらどうなの？ほら、さっさと始めなさい！"
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
            ),
            NotificationContext.FRIEND_GOAL_MET to listOf(
                "麗しいニュースだよ。%s様が目標を達成された。君も彼らの輝きに続いてみないかい？",
                "君のライバル、%sさんが目標を遂げたよ。さあ、君の優雅な学習時間も始めようか。"
            )
        ),
        StudyCharacter.SHIN to mapOf(
            NotificationContext.MORNING_NORMAL to listOf(
                "朝か。準備はできているな。余計な感傷は捨てて、目標だけを見ろ。",
                "今日も始まる。己を律し、淡々と進め。それだけが真理だ。"
            ),
            NotificationContext.MORNING_STREAK to listOf(
                "昨日で%d日。悪くない継続だ。だが、ここからが本番だぞ。気を抜くな。",
                "%d日連続...ふん、ようやく形になってきたな。今日も俺を退屈させるなよ。"
            ),
            NotificationContext.MORNING_MISSED_1 to listOf(
                "昨日は不在だったな。理由は問わん。だが、今日を取り戻せなければそれまでだぞ。",
                "1日の遅れは、戦場では命取りだ。今日、その遅れを清算しろ。"
            ),
            NotificationContext.MORNING_MISSED_2 to listOf(
                "何日サボっている。お前の覚悟はその程度だったのか。失望させるな。",
                "戻ってこい。お前にはまだ、超えるべき壁（単語）が残っているはずだ。1問からでいい、始めろ。"
            ),
            NotificationContext.EVENING_PENDING to listOf(
                "日が暮れるな。ノルマが残っているようだが、言い訳は無用だ。今すぐやれ。",
                "効率が落ちているぞ。この時間に追い込め。自分に甘えるな。",
                "夜を待つな。今、この瞬間にカタをつけろ。"
            ),
            NotificationContext.GOAL_COMPLETED to listOf(
                "完了か。当然の帰結だな。だが、満足はするな。明日のための休息をとれ。",
                "目標達成...ふん、ようやく俺の背中が見える位置まで来たか。明日も期待している。"
            ),
            NotificationContext.FRIEND_GOAL_MET to listOf(
                "%sが先に目標を抜けたぞ。奴はお前の先を行っている。いつまでそこにいるつもりだ？",
                "フレンドの%sが任務完了だ。お前はまだ足踏みか？...さっさと追いつけ。"
            )
        ),
        StudyCharacter.HARU to mapOf(
            NotificationContext.MORNING_NORMAL to listOf(
                "おっはよー！新しい朝だよ！今日も元気にいっくよー！ハルも応援してるからね！",
                "わーい、今日も会えたね！君の頑張る姿、ハルは一番楽しみにしてるんだよ！"
            ),
            NotificationContext.MORNING_STREAK to listOf(
                "昨日で%d日連続だよ！すごーい！君って本当にかっこいいね！今日も一緒に頑張ろう！",
                "%d日も続いてるなんて大ニュースだよ！今日もハッピーに学習スタートしちゃお！"
            ),
            NotificationContext.MORNING_MISSED_1 to listOf(
                "おはよ！昨日は会えなくて寂しかったよ〜。でも大丈夫、今日からまた一緒に楽しもう！",
                "昨日の分は今日ニコニコ笑顔で取り返しちゃおう！君なら絶対できるよ！"
            ),
            NotificationContext.MORNING_MISSED_2 to listOf(
                "ねえねえ、最近ハルのこと忘れてない？会いたかったよ〜！1問だけでもいいから遊びに来て！",
                "寂しくてハルの元気パワーが切れそうだよぉ。君の頑張りを見せてハルを元気にして！"
            ),
            NotificationContext.EVENING_PENDING to listOf(
                "お疲れ様〜！ねえねえ、今日もちょっとだけ頑張ってみない？ハルと一緒にやろう！",
                "休憩中かな？その勢いで3分だけお勉強タイムにしちゃお！えい、えい、おー！",
                "今のうちに終わらせたら、夜はハッピーに過ごせるよ！応援してるからね！"
            ),
            NotificationContext.GOAL_COMPLETED to listOf(
                "わぁーい！目標達成おめでとう！君の頑張りにハルから特大のハナマルをあげるね！",
                "やったね！今日の目標全部クリアだよ！君って本当にすごいんだから！大好き！"
            ),
            NotificationContext.FRIEND_GOAL_MET to listOf(
                "ねえねえ！%sちゃんが目標達成したって！すごいね！君もその波に乗っちゃおー！",
                "わぁ、%sさんが一足お先にゴールだよ！君の頑張りも、ハルはすぐ隣で見守ってるからね！"
            )
        ),
        StudyCharacter.ROBOSUKE to mapOf(
            NotificationContext.MORNING_NORMAL to listOf(
                "ピピッ。おはようございます。ボクと一緒に、今日も効率的な学習を開始しましょう。",
                "朝のデータ更新完了。君の学習スケジュールをサポートするために、ボクはここにいます。"
            ),
            NotificationContext.MORNING_STREAK to listOf(
                "分析結果：昨日で%d日連続達成です。君の継続力は素晴らしい数値を示していますよ！",
                "%d日間の継続を確認。学習効率が理想的な曲線を描いています。今日も維持しましょう！"
            ),
            NotificationContext.MORNING_MISSED_1 to listOf(
                "昨日のログが空になっていますね。大丈夫、今日再開すれば統計上の影響は最小限です。",
                "エラー回避。昨日の未達は気にせず、今日からまた新しいデータを積み上げましょう。"
            ),
            NotificationContext.MORNING_MISSED_2 to listOf(
                "警告：未学習期間が数日に及んでいます。ボクのメモリが寂しさでバグりそうです。戻ってきて！",
                "緊急メンテナンスが必要かな？1問だけでいいので、ボクの回路を動かさせてください。"
            ),
            NotificationContext.EVENING_PENDING to listOf(
                "お疲れ様です。本日の未達タスクを検出。今のうちに処理しておくことを推奨します。",
                "エネルギー充填。3分間の集中学習で、本日のノルマを消化してしまいましょう。",
                "スケジュールの遅延を検知。夜の自由時間を確保するために、今すぐ開始しませんか？"
            ),
            NotificationContext.GOAL_COMPLETED to listOf(
                "本日の目標、すべて処理完了を確認しました。君の努力をボクのストレージに永遠に保存します！",
                "ミッション・コンプリート！おめでとうございます。ボクの冷却ファンも喜んでいますよ。"
            ),
            NotificationContext.FRIEND_GOAL_MET to listOf(
                "外部通信受信。%sさんが目標を達成しました。君の進捗率も、もう少しで追いつきますよ！",
                "フレンドの%sさんが完了フラグを立てました。さあ、ボクたちもプロセスを開始しましょう。"
            )
        ),
        StudyCharacter.LEO to mapOf(
            NotificationContext.MORNING_NORMAL to listOf(
                "おい、貴様！いつまで寝ている！俺様の高貴な指導を、今日もありがたく受けるがいい！",
                "フン、朝か。貴様の凡庸な脳を、俺様が鍛え直してやる。さっさと準備しろ。"
            ),
            NotificationContext.MORNING_STREAK to listOf(
                "昨日で%d日か。フン、少しは骨のあるところを見せるな。だが調子に乗るなよ、今日が本番だ！",
                "%d日連続とは...。貴様、案外やりおるな。その根性だけは、俺様が認めてやらんでもない。"
            ),
            NotificationContext.MORNING_MISSED_1 to listOf(
                "昨日はどうした！俺様を待たせるとは、いい度胸をしているな。今日、その罪を学習で償え！",
                "貴様、1日の遅れがどれほど致命的か分かっているのか。今日、死ぬ気で取り戻せ！"
            ),
            NotificationContext.MORNING_MISSED_2 to listOf(
                "貴様、何日サボっている！俺様の視界から消えるつもりか。...フン、さっさと戻ってこい！",
                "寂しいなんて言わせるなよ、貴様。1問だけでもいい、お前の底力を見せてみろ！"
            ),
            NotificationContext.EVENING_PENDING to listOf(
                "まだ目標が終わっていないだと！？貴様、俺様を失望させるつもりか。今すぐ開始しろ！",
                "夕方のこの時間が勝負だ！貴様の真の力を俺様に見せてみろ。さあ、アプリを開け！",
                "フン、だらだらするな。今のうちに終わらせて、夜は俺様の話でも聞きに来い。"
            ),
            NotificationContext.GOAL_COMPLETED to listOf(
                "完了か。当然だな、俺様がついているんだ。今日はゆっくり休め、明日も逃がさんぞ。",
                "目標達成だと？フン、ようやく最低限の義務を果たしたか。だが、貴様の努力は嫌いではないぞ。"
            ),
            NotificationContext.FRIEND_GOAL_MET to listOf(
                "あの下級戦士（%s）が先に終わらせたか...。貴様、そんな奴に遅れをとって恥ずかしくないのか！",
                "フン、%sの奴がゴールだと。貴様、のんびりしている暇はないぞ。俺様の名に懸けて、即座に追いつけ！"
            )
        )
    )

    fun getLine(character: StudyCharacter, context: NotificationContext, streak: Int = 0, name: String = "友達"): String {
        val characterLines = lines[character] ?: lines[StudyCharacter.GEORGE]!!
        val contextLines = characterLines[context] ?: characterLines[NotificationContext.MORNING_NORMAL]!!
        val line = contextLines.random()
        return try {
            if (line.contains("%d")) String.format(line, streak)
            else if (line.contains("%s")) String.format(line, name)
            else line
        } catch (e: Exception) {
            line
        }
    }
}
