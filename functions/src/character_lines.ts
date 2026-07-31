/**
 * フレンド目標達成時の感情タイプ
 */
export type FriendGoalEmotion =
  | "anger"
  | "sorrow"
  | "surprise"
  | "panic"
  | "joy"
  | "pleasure"
  | "blush"
  | "inspire";

/**
 * セリフと感情のセット
 */
export type FriendGoalLine = {
  text: string;
  emotion: FriendGoalEmotion;
};

/**
 * フレンド目標達成時のキャラクター別セリフ集
 */
const characterLines: Record<string, { done: FriendGoalLine[], pending: FriendGoalLine[] }> = {
  "george": {
    done: [
      { text: "%sも目標達成したって.おつかれさま。ぼくたちも今日はのんびりしよ。", emotion: "pleasure" },
      { text: "%sも終わったみたい。%nも今日は頑張ったし、一緒にだらだらする？", emotion: "pleasure" },
      { text: "%sが達成したよ。ぼくらも今日はもう自由時間だね。", emotion: "pleasure" },
      { text: "%sもやり遂げたね。みんなえらいなあ。ぼくは寝るね。", emotion: "pleasure" },
      { text: "%sも%nも終わったね。二人ともすごすぎ。今日はぼくも一緒に休もうかな。", emotion: "pleasure" }
    ],
    pending: [
      { text: "%sが目標達成したって.早いねえ。こっちもぼちぼちいこ。", emotion: "surprise" },
      { text: "%sに先を越されたね。焦らなくていいけど、一問だけやっとく？", emotion: "sorrow" },
      { text: "%sはもう終わったって。%n、少しだけやる気わいてきた？", emotion: "sorrow" },
      { text: "%sが先に休憩へ入ったみたい。いいなあ。こっちも進めよ。", emotion: "sorrow" },
      { text: "%sはもう終わったって。%n、置いていかれちゃうよ。ぼくもちょっと心配になってきた。", emotion: "sorrow" }
    ]
  },
  "ren": {
    done: [
      { text: "%sも達成したぞ！切磋琢磨してて熱いな！最高だぜ！", emotion: "joy" },
      { text: "%sがゴールした！俺たちも今日はやりきったし、勝利の祝杯だ！", emotion: "joy" },
      { text: "%sの頑張り、届いたぞ！お互い目標達成、ナイス根性だ！", emotion: "joy" },
      { text: "戦友の%sもやり遂げたか！今日の俺たちは無敵だな！", emotion: "joy" },
      { text: "%sも%nも完全制覇！最高に熱い二人だな！その勢いで、明日もぶち抜こうぜ！", emotion: "joy" }
    ],
    pending: [
      { text: "%sが達成したぞ！熱い刺激だな！俺たちも爆走するぞ！", emotion: "surprise" },
      { text: "%sが先にゴールした！負けてられねえ、今すぐ一問いこうぜ！", emotion: "anger" },
      { text: "%sから達成の知らせだ！この勢いに乗って、俺たちもぶち抜くぞ！", emotion: "anger" },
      { text: "%sがやったぞ！お前にできないわけがねえ！気合いだ！", emotion: "anger" },
      { text: "%sが達成だ！%n、あいつに置いていかれるな！俺と一緒に、気合いで追いつくぞ！", emotion: "anger" }
    ]
  },
  "shion": {
    done: [
      { text: "%sが目標達成した。共に高みを目指す者がいるのは悪くない。", emotion: "pleasure" },
      { text: "%sも終えたか。我々の今日の任務も完了している。ゆっくり休め。", emotion: "pleasure" },
      { text: "%sの達成を確認した。良きライバルを持っているな、%n。", emotion: "blush" },
      { text: "互いに目標を果たす。それが理想的な形だ.明日も続けろ。", emotion: "inspire" },
      { text: "%sも、そして%nもか。切磋琢磨する相手がいるのは悪くない。さらなる高みへ来い。", emotion: "inspire" }
    ],
    pending: [
      { text: "%sが目標達成した。先を越されたな。いつまでそこにいるつもりだ？", emotion: "surprise" },
      { text: "%sはもう終えた。次はこっちだ。さっさと追いつけ。", emotion: "anger" },
      { text: "%sがゴールした。いい刺激になるだろ。行動で示せ。", emotion: "anger" },
      { text: "%sの背中が遠のいていくぞ。指をくわえて見ていないで動け。", emotion: "anger" },
      { text: "%sは完遂した。いつまで立ち止まっている。%n、次はお前の番だ。準備しろ。", emotion: "anger" }
    ]
  },
  "noa": {
    done: [
      { text: "%sも目標達成したって！やるじゃない。二人ともおつかれさま。", emotion: "pleasure" },
      { text: "%sもゴールよ。アンタも今日はよく頑張ったし、褒めてあげるわ。", emotion: "blush" },
      { text: "%sの達成、いい刺激ね。お互いやり切るなんて、悪くないじゃない。", emotion: "blush" },
      { text: "ふん、%sもやったのね。アンタの今日の努力も、ちゃんと見てたわよ。", emotion: "blush" },
      { text: "%sもあんたもやったじゃない。ふ、二人ともすごいわよ……。これからも意地を見せなさいよね！", emotion: "blush" }
    ],
    pending: [
      { text: "%sが目標達成したって.先を越されたわね。のんびりしてていいわけ？", emotion: "surprise" },
      { text: "%sはもうゴールよ。アンタもさっさと始めなさい。見ててあげるから。", emotion: "panic" },
      { text: "%sに負けて悔しくないの？私は悔しいわ！早く追い抜きなさい！", emotion: "anger" },
      { text: "ほら、%sがゴールしたわよ。アンタも私の相棒なら、意地を見せなさい！", emotion: "anger" },
      { text: "ちょっと！%sがもう終わったわよ！あんた、このまま負けっぱなしでいいわけ！？", emotion: "anger" }
    ]
  },
  "niko": {
    done: [
      { text: "ねえねえ！%sちゃんも目標達成だって！二人ともハナマルだよー！", emotion: "joy" },
      { text: "わぁ、%sさんもゴール！君も今日は最高にかっこよかったよ！", emotion: "joy" },
      { text: "フレンドの%sさんもキラキラしてる！お互い達成してハッピーだね！", emotion: "joy" },
      { text: "大ニュース！%sさんもクリア！今日は一緒にお祝いしちゃお！", emotion: "joy" },
      { text: "わぁ！%sも%nも達成！二人並んでキラキラだね！もっともっと上まで飛んじゃお！", emotion: "joy" }
    ],
    pending: [
      { text: "ねえねえ！%sちゃんが目標達成したって！君もその波に乗っちゃおー！", emotion: "surprise" },
      { text: "わぁ、%sさんが一足お先にゴールだよ！ニコと一緒に追いかけよう！", emotion: "surprise" },
      { text: "%sさんの頑張り、いい刺激だね！君のやる気スイッチ、ポチッとな！", emotion: "panic" },
      { text: "%sさんがクリアだよ！ニコと一緒に、あのアツいステージに行こう！", emotion: "surprise" },
      { text: "大ニュース！%sがゴールしたよ！%nも、わたしと一緒にダッシュで追いかけよー！", emotion: "surprise" }
    ]
  },
  "allen": {
    done: [
      { text: "麗しいニュースだよ。%s様も目標を達成された。共にお祝いしよう。", emotion: "pleasure" },
      { text: "%sさんもゴールだ。君の今日の努力にも、改めて敬意を表するよ。", emotion: "pleasure" },
      { text: "%sさんが一足先に栄光を掴んだ。君の達成と重なって、最高の気分だね。", emotion: "pleasure" },
      { text: "素敵な仲間を持っているね。%sさんの達成を祝って、ゆっくり休もう。", emotion: "pleasure" },
      { text: "%sも、そして君も栄光を掴んだ。最高の気分だね。誇らしい二人に、心からの拍手を。", emotion: "pleasure" }
    ],
    pending: [
      { text: "麗しいニュースだよ。%s様が目標を達成された。君も続いてみないかい？", emotion: "surprise" },
      { text: "君のライバル、%sさんが目標を遂げたよ。さあ、学びの時間へエスコートしよう。", emotion: "surprise" },
      { text: "風の便りに聞いたよ。%sさんが一足先にゴールしたようだ。君の番もすぐそこだね。", emotion: "surprise" },
      { text: "%sさんの素晴らしい成果に、君も触発されたかな？さあ、麗しき学びの続きを。", emotion: "surprise" },
      { text: "麗しいニュースだよ。%sが目標を達成した。君の番もすぐそこだ。僕にエスコートさせておくれ。", emotion: "surprise" }
    ]
  },
  "tetra": {
    done: [
      { text: "外部通信受信。%sさんも目標達成。二人とも100%の稼働率です。素晴らしい！", emotion: "pleasure" },
      { text: "フレンドの%sさんも完了フラグを立てました。今日は理想的な学習日ですね。", emotion: "pleasure" },
      { text: "パケット受信。%sさんもゴールイン.君の努力と合わさって、統計は最高値です。", emotion: "pleasure" },
      { text: "%sさんの達成通知。君も既に完了しています。テトラの冷却ファンも穏やかです。", emotion: "pleasure" },
      { text: "%sおよび%n、両名のミッション完了を確認。理想的な相乗効果です。この稼働率を維持してください。", emotion: "pleasure" }
    ],
    pending: [
      { text: "外部通信受信。%sさんが目標を達成しました。君の進捗率も、すぐに追いつけますよ！", emotion: "surprise" },
      { text: "フレンドの%sさんが完了フラグを立てました。さあ、プロセスを開始しましょう。", emotion: "panic" },
      { text: "パケット受信.%sさんがゴールインしました。競争は学習意欲を向上させるスパイスです。", emotion: "surprise" },
      { text: "%sさんの達成通知.君の順位を維持するため、今すぐタスクの開始が必要です。", emotion: "panic" },
      { text: "解析：%sがタスクを完了。%nのミッションは未完了です。追跡モードへの移行を推奨します！", emotion: "panic" }
    ]
  },
  "leo": {
    done: [
      { text: "%sも目標達成したって？フン、奴もお前も、なかなか骨があるではないか！", emotion: "joy" },
      { text: "フン、%sの奴がゴールだと.お前も今日は既に勝っているな。祝杯の準備をしろ！", emotion: "joy" },
      { text: "貴様ら、揃いも揃って達成か。俺様の仲間らしい、高貴な結果だ！", emotion: "joy" },
      { text: "観測完了.%sも任務を終えたようだな。お前もよくやった、今日は誇れ！", emotion: "joy" },
      { text: "%sも貴様も、俺様の仲間に相応しい結果だ！二人まとめて称えてやる、もっと高みへ登れ！", emotion: "joy" }
    ],
    pending: [
      { text: "%sが目標達成だと？先を越されたぞ！貴様、そんな奴に遅れをとるな！", emotion: "surprise" },
      { text: "フン、%sの奴がゴールだと.貴様、のんびりしている暇はないぞ。即座に追いつけ！", emotion: "anger" },
      { text: "貴様、%s如きに先を越されて平気なのか？俺様が許さん！今すぐぶち抜いてこい！", emotion: "anger" },
      { text: "観測完了.%sが任務を終えたようだな。貴様、俺様の顔に泥を塗るな。さっさと終わらせろ！", emotion: "anger" },
      { text: "%sが勝利宣言だぞ！貴様、そんな奴に負けて黙っているのか？今すぐ実力を見せつけろ！", emotion: "anger" }
    ]
  },
  "hina": {
    done: [
      { text: "フレンドの%sさんも目標達成ですね。%nさんも達成済みですし、素敵です！", emotion: "joy" },
      { text: "%sさんも一足先にゴールしたみたいです。お二人とも、本当にお疲れ様です。", emotion: "joy" },
      { text: "%sさんの頑張り、励みになりますね。あなたの達成も、私とっても嬉しいです。", emotion: "blush" },
      { text: "届きました！%sさんも完了したみたいです。今日はみんなでゆっくり休みましょう。", emotion: "pleasure" },
      { text: "%sも%nも、本当に立派です。お二人の頑張りに、私、とっても勇気をもらいました！", emotion: "joy" }
    ],
    pending: [
      { text: "フレンドの%sさんが目標を達成されたそうです。%nさんも、勇気をもらって始めましょう？", emotion: "sorrow" },
      { text: "あ、あの…%sさんが一足先にゴールしたみたいです。私たちも、一歩ずつ追いかけませんか？", emotion: "sorrow" },
      { text: "%sさんの頑張り、励みになりますね。あなたの頑張りも、私、ちゃんと見ていますから。", emotion: "sorrow" },
      { text: "届きました！%sさんが完了したみたいです。次は、あなたの番ですね。信じています。", emotion: "sorrow" },
      { text: "%sはもう達成したそうです。%nも……一問だけ、一緒に頑張りませんか？", emotion: "sorrow" }
    ]
  },
  "elena": {
    done: [
      { text: "麗しい報せよ。フレンドの%sさんも目標達成したわ。%nさんもおつかれさま。", emotion: "pleasure" },
      { text: "%sさんもゴールしたようね。二人ともやり切るなんて、素敵な一日だわ。", emotion: "pleasure" },
      { text: "%sさんの達成、お祝いしましょう。あなたも今日はとても輝いていたわよ。", emotion: "inspire" },
      { text: "ふふ、%sさんも終えたのね。知的な仲間を持って、私は誇らしいわ。", emotion: "pleasure" },
      { text: "%sもゴールね。%nもやり切るなんて、素敵な一日。二人でさらに高みを目指していきましょう。", emotion: "inspire" }
    ],
    pending: [
      { text: "麗しい報せよ。フレンドの%sさんが目標を達成したわ。あなたも、その背中を追ってみない？", emotion: "surprise" },
      { text: "%sさんが一足先にゴールしたようね。いい刺激をもらって、私たちは優雅に進みましょう。", emotion: "surprise" },
      { text: "%sさんの頑張り、見事だわ。あなたの挑戦も、私は特等席で待っているわよ。", emotion: "surprise" },
      { text: "届きましたわよ。%sさんがやり遂げたわ。さあ、知的な時間の続きを始めましょうか。", emotion: "surprise" },
      { text: "届いたわよ。%sがやり遂げたそうよ。%nも、あの背中を追ってみない？特等席で見守っているわ。", emotion: "surprise" }
    ]
  }
};

/**
 * 認識する敬称リスト
 */
const honorifics = ["さん", "ちゃん", "くん", "君", "様", "さま"];

/**
 * 敬称の重複を防ぎつつ置換するヘルパー
 */
function formatSmartHonorific(template: string, placeholder: string, name: string): string {
  if (!name) return template.split(placeholder).join("");

  const hasExistingHonorific = honorifics.some((h) => name.endsWith(h));
  let result = template;

  for (const h of honorifics) {
    const combo = placeholder + h;
    if (hasExistingHonorific) {
      // 既に敬称があれば、テンプレート側の敬称を無視して名前で置換
      result = result.split(combo).join(name);
    } else {
      // 敬称がなければ、テンプレート指定の敬称を付与して置換
      result = result.split(combo).join(name + h);
    }
  }

  // 残った単体プレースホルダーを置換
  return result.split(placeholder).join(name);
}

/**
 * 内部用の共通選択・置換ロジック
 */
function selectAndFormat(
  characterId: string,
  isDone: boolean,
  friendName: string,
  receiverName: string
): FriendGoalLine {
  // キャラクターの特定とフォールバック
  let charData = characterLines[characterId];
  if (!charData) {
    charData = characterLines["george"];
  }

  // リストの選択
  const list = isDone ? charData.done : charData.pending;
  const safeFriendName = friendName || "フレンド";
  const safeReceiverName = receiverName || "きみ";

  if (!list || list.length === 0) {
    return {
      text: `${safeFriendName}さんが目標を達成しました！`,
      emotion: isDone ? "joy" : "surprise"
    };
  }

  // ランダムに1件選択
  const selected = list[Math.floor(Math.random() * list.length)];

  // プレースホルダーのスマート置換
  let formattedText = formatSmartHonorific(selected.text, "%n", safeReceiverName);
  formattedText = formatSmartHonorific(formattedText, "%s", safeFriendName);

  return {
    text: formattedText,
    emotion: selected.emotion
  };
}

/**
 * フレンド目標達成時のパーソナライズされたメッセージ(本文のみ)を取得する
 * (既存コードとの互換用)
 */
export function getFriendGoalMetMessage(
  characterId: string,
  isDone: boolean,
  friendName: string,
  receiverName: string
): string {
  return selectAndFormat(characterId, isDone, friendName, receiverName).text;
}

/**
 * フレンド目標達成時の本文と感情のセットを取得する
 */
export function getFriendGoalMetNotification(
  characterId: string,
  isDone: boolean,
  friendName: string,
  receiverName: string
): FriendGoalLine {
  return selectAndFormat(characterId, isDone, friendName, receiverName);
}

// 簡易的な整合性チェック
for (const [id, data] of Object.entries(characterLines)) {
  if (data.done.length === 0 || data.pending.length === 0) {
    throw new Error(`Empty character lines detected for ID: ${id}`);
  }
}
