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

// ■ 3. 日次レポート（毎日7時）
export const sendDailyReport = functions.region('asia-northeast1').pubsub.schedule('every day 07:00').timeZone('Asia/Tokyo').onRun(async (context) => {
    const usersSnapshot = await db.collection("users").where("role", "==", "child").get();
    if (usersSnapshot.empty) {
        console.log("No children found for daily report.");
        return null;
    }

    // レポート対象日（昨日）の日付文字列を YYYY-MM-DD 形式で取得
    const yesterday = new Date();
    yesterday.setDate(yesterday.getDate() - 1);
    const yesterdayStr = yesterday.toISOString().split('T')[0];

    const promises: Promise<any>[] = [];

    for (const userDoc of usersSnapshot.docs) {
        const uid = userDoc.id;
        const statsRef = db.collection("users").doc(uid).collection("dailyStats").doc(yesterdayStr);
        const statsDoc = await statsRef.get();

        let reportBody: string;
        if (statsDoc.exists) {
            const data = statsDoc.data() || {};
            // Firestoreから取得するデータ。Androidアプリ側でこのフィールド名で保存されている必要があります。
            const earnedPoints = data.points || 0;
            const usedPoints = data.pointsUsed || 0;
            const grades = (data.gradesStudied && data.gradesStudied.length > 0) ? data.gradesStudied.join(', ') : 'なし';
            const modes = (data.modesStudied && data.modesStudied.length > 0) ? data.modesStudied.join(', ') : 'なし';
            const studyCount = data.studyCount || 0;
            const correctCount = data.correctCount || 0;

            // 通知の本文を組み立て
            reportBody = [
                `獲得ポイント: ${earnedPoints} pt`,
                `使用ポイント: ${usedPoints} pt`,
                `学習グレード: ${grades}`,
                `学習モード: ${modes}`,
                `学習数: ${studyCount}問`,
                `正解数: ${correctCount}問`
            ].join('\\n');

        } else {
            reportBody = "昨日の学習データはありませんでした。";
        }

        // ユーザーのすべての親に通知を送信
        const parentsSnapshot = await db.collection("users").doc(uid).collection("parents").get();
        if (parentsSnapshot.empty) continue;

        const dateString = `${yesterday.getMonth() + 1}/${yesterday.getDate()}`;
        const title = `📅 学習レポート (${dateString})`;

        parentsSnapshot.forEach((parentDoc) => {
            const parentData = parentDoc.data();
            if (parentData.fcmToken) {
                promises.push(admin.messaging().send({
                    token: parentData.fcmToken,
                    notification: { title, body: reportBody },
                }));
            }
        });
    }

    if (promises.length > 0) {
        await Promise.all(promises);
    }
    return null;
});