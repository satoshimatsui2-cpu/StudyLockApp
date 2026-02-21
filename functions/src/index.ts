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
      if (parentData.fcmToken) {
        messages.push({
          token: parentData.fcmToken,
          notification: {
            title: "🔑 解除コード",
            body: `コード: ${code}\nお子様が管理画面へのアクセスを求めています。`,
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
      const title = "⚠️ セキュリティアラート";
      let body = `お子様が「アクセシビリティ権限」をONにしました。\n時刻: ${timestamp}`;
      if (alertType === "accessibility_disabled") {
        body =
          `⚠️ お子様が「アクセシビリティ権限」をOFFにしました！\n` +
          `アプリの監視が無効化されています。\n時刻: ${timestamp}`;
      }

      parentsSnapshot.forEach((doc) => {
        const parentData = doc.data();
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

// ■ 3. 日次レポート（毎日 朝7時 / Tokyo基準で前日→当日→2日前フォールバック）
export const sendDailyReport = functions
  .region("asia-northeast1")
  .pubsub.schedule("every day 07:00")
  .timeZone("Asia/Tokyo")
  .onRun(async () => {
    const usersSnapshot = await db.collection("users").where("role", "==", "child").get();
    if (usersSnapshot.empty) {
      console.log("No children found for daily report.");
      return null;
    }

    // Tokyo基準：前日 / 当日 / 2日前 を候補にする
    const now = new Date();
    const yesterday = new Date(Date.now() - 24 * 60 * 60 * 1000);
    const twoDaysAgo = new Date(Date.now() - 48 * 60 * 60 * 1000);

    const dateYesterday = formatTokyoDateYYYYMMDD(yesterday);
    const dateToday = formatTokyoDateYYYYMMDD(now);
    const dateTwoDaysAgo = formatTokyoDateYYYYMMDD(twoDaysAgo);
    const candidateDates = [dateYesterday, dateToday, dateTwoDaysAgo];

    const promises: Promise<string>[] = [];

    for (const userDoc of usersSnapshot.docs) {
      const uid = userDoc.id;

      // どれか存在する dailyStats を採用（前日→当日→2日前）
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

      // メッセージ組み立て
      let studyMessage = `前後の学習データが見つかりませんでした。\n(参照候補: ${candidateDates.join(
        ", "
      )})`;

      if (pickedDate && pickedStats) {
        const points = pickedStats.points || 0;

        // ★重要：pointsUsed が本命。互換で usedPoints も見る
        const pointsUsed =
          (pickedStats.pointsUsed ?? pickedStats.usedPoints ?? pickedStats.usedPointsTotal ?? 0) || 0;

        const gradesStudied = Array.isArray(pickedStats.gradesStudied) ? pickedStats.gradesStudied : [];
        const modesStudied = Array.isArray(pickedStats.modesStudied) ? pickedStats.modesStudied : [];

        const studyCount = pickedStats.studyCount || 0;
        const correctCount = pickedStats.correctCount || 0;

        const accuracy = studyCount > 0 ? Math.round((correctCount / studyCount) * 100) : 0;

        const gradesText = gradesStudied.length > 0 ? gradesStudied.join("、") : "なし";
        const modesText = modesStudied.length > 0 ? modesStudied.join("、") : "なし";

        studyMessage =
          `獲得: ${points} pt / 使用: ${pointsUsed} pt\n` +
          `級: ${gradesText}\n` +
          `学習モード: ${modesText}\n` +
          `正解率: ${accuracy}% (${correctCount}/${studyCount})`;
      }

      // 親へ送信
      const parentsSnapshot = await db.collection("users").doc(uid).collection("parents").get();
      if (parentsSnapshot.empty) continue;

      parentsSnapshot.forEach((parentDoc) => {
        const parentData = parentDoc.data();
        const childName = parentData.childDisplayName || "お子様";
        const pickedMD = (() => {
          const parts = (pickedDate ?? "").split("-");
          if (parts.length !== 3) return pickedDate ?? "";
          const m = String(parseInt(parts[1], 10)); // "2"
          const d = String(parseInt(parts[2], 10)); // "21"
          return `${m}/${d}`;
        })();
        if (parentData.fcmToken) {
          promises.push(
            admin.messaging().send({
              token: parentData.fcmToken,
              notification: {
                title: `📅 【${childName}】${pickedMD}レポート`,
          body: studyMessage, // ← `${}`いらないのでそのままでOK
              },
              android: { priority: "high" },
            })
          );
        }
      });
    }

    if (promises.length > 0) {
      await Promise.all(promises);
    }
    return null;
  });