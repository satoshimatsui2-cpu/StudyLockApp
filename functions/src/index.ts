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

// ■ 2. 緊急セキュリティ警告（不正検知→親・子）
export const sendSecurityAlert = functions.region('asia-northeast1').https.onCall(async (data: any, context: any) => {
    const uid = (context.auth && context.auth.uid) || data.uid;
    if (!uid) return { success: false, message: "ID missing" };

    const alertType = data.alertType || "unknown";
    const timestamp = new Date().toLocaleString('ja-JP', { timeZone: 'Asia/Tokyo' });

    // 親と子のドキュメントを並行して取得
    const parentsRef = db.collection("users").doc(uid).collection("parents");
    const userDocRef = db.collection("users").doc(uid);
    const [parentsSnapshot, userDoc] = await Promise.all([parentsRef.get(), userDocRef.get()]);

    const messages: admin.messaging.Message[] = [];

    // --- 親への通知を作成 ---
    if (!parentsSnapshot.empty) {
        const title = "⚠️ セキュリティアラート";
        let body = `お子様が「アクセシビリティ権限」をONにしました。\n時刻: ${timestamp}`;
        if (alertType === "accessibility_disabled") {
            body = `⚠️ お子様が「アクセシビリティ権限」をOFFにしました！\nアプリの監視が無効化されています。\n時刻: ${timestamp}`;
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

    // --- 子供への通知を作成 (OFFの場合のみ) ---
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

    // 作成したすべての通知を送信
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