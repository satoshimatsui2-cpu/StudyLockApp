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