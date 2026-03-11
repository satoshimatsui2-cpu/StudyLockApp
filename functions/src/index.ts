import * as functions from "firebase-functions/v1";
import * as admin from "firebase-admin";

admin.initializeApp();
const db = admin.firestore();

// ===== 共通ユーティリティ（Tokyo固定）=====
function formatTokyoDateYYYYMMDD(dateObj: Date): string {
  const fmt = new Intl.DateTimeFormat("ja-JP", {
    timeZone: "Asia/Tokyo",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  });
  const parts = fmt.formatToParts(dateObj);
  const y = parts.find((p) => p.type === "year")?.value || "1970";
  const m = parts.find((p) => p.type === "month")?.value || "01";
  const d = parts.find((p) => p.type === "day")?.value || "01";
  return `${y}-${m}-${d}`;
}

function formatTokyoTimestamp(): string {
  return new Date().toLocaleString("ja-JP", { timeZone: "Asia/Tokyo" });
}

// ■ 1. 解除コード通知（子供→親）
export const requestUnlockCode = functions
  .region("asia-northeast1")
  .https.onCall(async (data: any, context: any) => {
    const uid = (context.auth && context.auth.uid) || data.uid;
    if (!uid) return { success: false, message: "ID missing" };

    const code = data.code;
    const parentsRef = db.collection("users").doc(uid).collection("parents");
    const parentsSnapshot = await parentsRef.get();

    if (parentsSnapshot.empty) return { success: false, message: "No parents" };

    const messages: admin.messaging.Message[] = [];
    parentsSnapshot.forEach((doc) => {
      const parentData = doc.data();
      const childName = parentData.childDisplayName || "お子様";

      if (parentData.fcmToken) {
        messages.push({
          token: parentData.fcmToken,
          notification: {
            title: "🔑 解除コード",
            body: `コード: ${code}\n${childName}が管理画面へのアクセスを求めています。`,
          },
          android: { priority: "high" },
        });
      }
    });

    if (messages.length > 0) {
      await Promise.all(messages.map((msg) => admin.messaging().send(msg)));
    }
    return { success: true };
  });

// ■ 2. 緊急セキュリティ警告（不正検知→親・子）
export const sendSecurityAlert = functions
  .region("asia-northeast1")
  .https.onCall(async (data: any, context: any) => {
    const uid = (context.auth && context.auth.uid) || data.uid;
    if (!uid) return { success: false, message: "ID missing" };

    const alertType = data.alertType || "unknown";
    const timestamp = formatTokyoTimestamp();

    // 親と子のドキュメントを並行して取得
    const parentsRef = db.collection("users").doc(uid).collection("parents");
    const userDocRef = db.collection("users").doc(uid);
    const [parentsSnapshot, userDoc] = await Promise.all([parentsRef.get(), userDocRef.get()]);

    const messages: admin.messaging.Message[] = [];

    // --- 親への通知 ---
    if (!parentsSnapshot.empty) {
      parentsSnapshot.forEach((doc) => {
        const parentData = doc.data();
        const childName = parentData.childDisplayName || "お子様";

        const title = "⚠️ セキュリティアラート";
        let body = `${childName}が「アクセシビリティ権限」をONにしました。\n時刻: ${timestamp}`;
        if (alertType === "accessibility_disabled") {
          body =
            `⚠️ ${childName}が「アクセシビリティ権限」をOFFにしました！\n` +
            `アプリの監視が無効化されています。\n時刻: ${timestamp}`;
        }

        if (parentData.fcmToken) {
          messages.push({
            token: parentData.fcmToken,
            notification: { title, body },
            android: { priority: "high" },
          });
        }
      });
    }

    // --- 子供への通知 (OFFの場合のみ) ---
    if (alertType === "accessibility_disabled") {
      const userData = userDoc.data();
      if (userData && userData.fcmToken) {
        messages.push({
          token: userData.fcmToken,
          notification: {
            title: "⚠️ 設定が必要です",
            body: "⚠️アプリを使用出来ないためアクセシビリティをONにして下さい。",
          },
          android: { priority: "high" },
        });
      }
    }

    if (messages.length > 0) {
      await Promise.all(messages.map((msg) => admin.messaging().send(msg)));
    }
    return { success: true };
  });

// モード名変換マップ
const modeMap: Record<string, string> = {
  meaning: "英単語→日本語",
  listening: "リスニング(英語→英語)",
  listening_jp: "リスニング(英語→日本語)",
  japanese_to_english: "日本語→英単語",
  english_english_1: "英単語→英語意味",
  english_english_2: "英語意味→英単語",
  test_fill_blank: "穴埋めテスト",
  test_sort: "並べ替えテスト",
  test_listen_q1: "リスニング質問テスト",
  test_listen_q2: "会話リスニングテスト"
};

// ■ 3. 日次レポート（毎日 朝7:30 / Tokyo基準で前日→当日→2日前フォールバック）
export const sendDailyReport = functions
  .region("asia-northeast1")
  .pubsub.schedule("every day 07:30") // 少し遅らせてアプリ側のアップロードを待つ
  .timeZone("Asia/Tokyo")
  .onRun(async () => {
    const usersSnapshot = await db.collection("users").where("role", "==", "child").get();
    if (usersSnapshot.empty) {
      console.log("No children found for daily report.");
      return null;
    }

    // Tokyo基準
    const now = new Date();
    const yesterday = new Date(Date.now() - 24 * 60 * 60 * 1000);
    const twoDaysAgo = new Date(Date.now() - 48 * 60 * 60 * 1000);

    const dateYesterday = formatTokyoDateYYYYMMDD(yesterday);
    const dateToday = formatTokyoDateYYYYMMDD(now);
    const dateTwoDaysAgo = formatTokyoDateYYYYMMDD(twoDaysAgo);
    const candidateDates = [dateYesterday, dateToday, dateTwoDaysAgo];

    const promises: Promise<any>[] = [];

    for (const userDoc of usersSnapshot.docs) {
      const uid = userDoc.id;

      let pickedDate: string | null = null;
      let pickedStats: FirebaseFirestore.DocumentData | null = null;

      for (const d of candidateDates) {
        const doc = await db.collection("users").doc(uid).collection("dailyStats").doc(d).get();
        if (doc.exists) {
          pickedDate = d;
          pickedStats = doc.data() || {};
          break;
        }
      }

      if (pickedDate && pickedStats) {
        const points = pickedStats.points || 0;
        const pointsUsed = (pickedStats.pointsUsed ?? pickedStats.usedPoints ?? 0) || 0;
        const gradesStudied = Array.isArray(pickedStats.gradesStudied) ? pickedStats.gradesStudied : [];
        const modesStudied = Array.isArray(pickedStats.modesStudied) ? pickedStats.modesStudied : [];
        const studyCount = pickedStats.studyCount || 0;
        const correctCount = pickedStats.correctCount || 0;
        const accuracy = studyCount > 0 ? Math.round((correctCount / studyCount) * 100) : 0;

        // 解放実績の整形 (上位3件)
        const unlockSummary = pickedStats.unlockSummary ?? null;
        let unlockText = "";
        if (unlockSummary) {
          unlockText = "\n【解放実績】";
          const sortedApps = Object.entries(unlockSummary as Record<string, number>)
            .sort((a, b) => b[1] - a[1])
            .slice(0, 3);
          sortedApps.forEach(([app, sec]) => {
            const min = Math.floor(sec / 60);
            unlockText += `\n・${app}: ${min > 0 ? min + "分" : (sec % 60) + "秒"}`;
          });
        }

        const gradesText = gradesStudied.length > 0 ? Array.from(new Set(gradesStudied)).join("、") : "なし";
        const displayModes = modesStudied.map((m: string) => modeMap[m] ?? m);
        const modesText = displayModes.length > 0 ? Array.from(new Set(displayModes)).join("、") : "なし";

        const studyMessage =
          `獲得: ${points} pt / 使用: ${pointsUsed} pt` +
          unlockText +
          `\n級: ${gradesText}` +
          `\n学習モード: ${modesText}` +
          `\n正解率: ${accuracy}% (${correctCount}/${studyCount})`;

        const parentsSnapshot = await db.collection("users").doc(uid).collection("parents").get();
        if (parentsSnapshot.empty) continue;

        parentsSnapshot.forEach((parentDoc) => {
          const parentData = parentDoc.data();
          const childName = parentData.childDisplayName || "お子様";
          const pickedMD = (() => {
            const parts = (pickedDate ?? "").split("-");
            if (parts.length !== 3) return pickedDate ?? "";
            return `${parseInt(parts[1], 10)}/${parseInt(parts[2], 10)}`;
          })();

          if (parentData.fcmToken) {
            promises.push(
              admin.messaging().send({
                token: parentData.fcmToken,
                notification: {
                  title: `📅 【${childName}】${pickedMD}レポート`,
                  body: studyMessage,
                },
                android: { priority: "high" },
              })
            );
          }
        });
      }
    }

    if (promises.length > 0) {
      await Promise.all(promises);
    }
    return null;
  });