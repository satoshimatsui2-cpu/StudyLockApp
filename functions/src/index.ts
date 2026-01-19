import * as functions from "firebase-functions/v1";
import * as admin from "firebase-admin";

admin.initializeApp();
const db = admin.firestore();

// 東京リージョンを指定
export const requestUnlockCode = functions.region('asia-northeast1').https.onCall(async (data: any, context: any) => {
    // ID手渡し対応
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
                    body: `コード: ${code}`,
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

// 緊急警告用も東京で
export const sendSecurityAlert = functions.region('asia-northeast1').https.onCall(async (data: any, context: any) => {
    const uid = (context.auth && context.auth.uid) || data.uid;
    if (!uid) return { success: false, message: "ID missing" };

    // ...中略（前回のコードと同じロジックでOK）...
    // ※もし必要なら以前のコードを貼りますが、まずは解除コード機能だけでOKです
    return { success: true };
});
// ■ 3. 日次レポート配信（毎日21時に実行）
export const sendDailyReport = functions.region('asia-northeast1').pubsub.schedule('every day 21:00').timeZone('Asia/Tokyo').onRun(async (context) => {

    // 1. 全ユーザー（子供）を取得
    const usersSnapshot = await db.collection("users").where("role", "==", "child").get();

    if (usersSnapshot.empty) {
        console.log("No children found.");
        return null;
    }

    const promises: Promise<any>[] = [];

    // 2. 一人ずつループ処理
    for (const userDoc of usersSnapshot.docs) {
        const uid = userDoc.id;

        // 今日の勉強時間を取得（例: dailyStatsコレクションなどがあればそこから読む）
        // ※ここでは簡易的に「今日の学習記録」があるか確認するロジック例です
        const todayStr = new Date().toISOString().split('T')[0]; // YYYY-MM-DD
        const statsRef = db.collection("users").doc(uid).collection("dailyStats").doc(todayStr);
        const statsDoc = await statsRef.get();

        let studyMessage = "本日の学習データはありません。";
        if (statsDoc.exists) {
            const data = statsDoc.data();
            const points = data?.points || 0;
            studyMessage = `今日の獲得ポイント: ${points} pt`;
        }

        // 3. 親を探して通知を送る
        const parentsSnapshot = await db.collection("users").doc(uid).collection("parents").get();

        parentsSnapshot.forEach((parentDoc) => {
            const parentData = parentDoc.data();
            if (parentData.fcmToken) {
                const message = {
                    token: parentData.fcmToken,
                    notification: {
                        title: "📅 日次学習レポート",
                        body: studyMessage,
                    },
                };
                promises.push(admin.messaging().send(message));
            }
        });
    }

    // 全員分の通知送信を待機
    if (promises.length > 0) {
        await Promise.all(promises);
    }

    console.log(`Sent reports to ${promises.length} parents.`);
    return null;
});