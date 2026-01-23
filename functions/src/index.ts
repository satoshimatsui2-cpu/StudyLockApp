import * as functions from "firebase-functions/v1";
import * as admin from "firebase-admin";

admin.initializeApp();
const db = admin.firestore();

// ■ 1. 解除コード通知（子供→親）
export const requestUnlockCode = functions.region('asia-northeast1').https.onCall(async (data: any, context: any) => {
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

// ■ 2. 緊急セキュリティ警告（不正検知→親）
export const sendSecurityAlert = functions.region('asia-northeast1').https.onCall(async (data: any, context: any) => {
    const uid = (context.auth && context.auth.uid) || data.uid;
    if (!uid) return { success: false, message: "ID missing" };

    const alertType = data.alertType || "unknown";
   // ★スマホの時間は信用せず、サーバー側で強制的に現在時刻（日本時間）を取得する
       const timestamp = new Date().toLocaleString('ja-JP', { timeZone: 'Asia/Tokyo' });

    // 親を探す
    const parentsRef = db.collection("users").doc(uid).collection("parents");
    const parentsSnapshot = await parentsRef.get();

    if (parentsSnapshot.empty) return { success: false, message: "No parents" };

    // 通知内容を決める
    let title = "⚠️ 緊急セキュリティ警告";
    // ★修正: ここで timestamp を使うようにしました
    let body = `StudyLockの状態が変わりました。\nタイプ: ${alertType}\n時刻: ${timestamp}`;

    if (alertType === "accessibility_disabled") {
        // ★修正: ここにも timestamp を追加
        body = `⚠️ お子様が「アクセシビリティ権限」をOFFにしました！\nアプリの監視が無効化されています。\n時刻: ${timestamp}`;
    }

    const messages: admin.messaging.Message[] = [];
    parentsSnapshot.forEach((doc) => {
        const parentData = doc.data();
        if (parentData.fcmToken) {
            messages.push({
                token: parentData.fcmToken,
                notification: {
                    title: title,
                    body: body,
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

// ■ 3. 日次レポート（毎日21時）
export const sendDailyReport = functions.region('asia-northeast1').pubsub.schedule('every day 21:00').timeZone('Asia/Tokyo').onRun(async (context) => {
    const usersSnapshot = await db.collection("users").where("role", "==", "child").get();
    if (usersSnapshot.empty) {
        console.log("No children found.");
        return null;
    }

    const promises: Promise<any>[] = [];

    for (const userDoc of usersSnapshot.docs) {
        const uid = userDoc.id;
        const todayStr = new Date().toISOString().split('T')[0];
        const statsRef = db.collection("users").doc(uid).collection("dailyStats").doc(todayStr);
        const statsDoc = await statsRef.get();

        let studyMessage = "本日の学習データはありません。";
        if (statsDoc.exists) {
            const data = statsDoc.data();
            const points = data?.points || 0;
            studyMessage = `今日の獲得ポイント: ${points} pt`;
        }

        const parentsSnapshot = await db.collection("users").doc(uid).collection("parents").get();
        parentsSnapshot.forEach((parentDoc) => {
            const parentData = parentDoc.data();
            if (parentData.fcmToken) {
                promises.push(admin.messaging().send({
                    token: parentData.fcmToken,
                    notification: {
                        title: "📅 日次学習レポート",
                        body: studyMessage,
                    },
                }));
            }
        });
    }

    if (promises.length > 0) {
        await Promise.all(promises);
    }
    return null;
});