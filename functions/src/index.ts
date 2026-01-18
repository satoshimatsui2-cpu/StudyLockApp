import { onCall, HttpsError } from "firebase-functions/v2/https";
import * as admin from "firebase-admin";
import * as logger from "firebase-functions/logger";

admin.initializeApp();

const db = admin.firestore();

// データインターフェース定義
interface ReportData {
  mode: string;
  answerCount: number;
  correctCount: number;
  pointsChange: number;
}

export const sendDailyReport = onCall(async (request) => {
  // 1. 認証チェック (V2では request.auth を参照)
  if (!request.auth) {
    throw new HttpsError(
      "unauthenticated",
      "The function must be called while authenticated."
    );
  }

  const uid = request.auth.uid;
  const report = request.data as ReportData;

  // バリデーション
  if (!report || typeof report.answerCount !== "number") {
    throw new HttpsError(
      "invalid-argument",
      "The function must be called with valid report data."
    );
  }

  // 2. 親ユーザーのトークンを取得
  const parentsRef = db.collection("users").doc(uid).collection("parents");
  const parentsSnapshot = await parentsRef.get();

  if (parentsSnapshot.empty) {
    logger.info(`No parents found for user ${uid}.`);
    return { success: true, message: "No parents to notify." };
  }

  const messages: admin.messaging.Message[] = [];
  const parentDocs: FirebaseFirestore.QueryDocumentSnapshot[] = [];

  parentsSnapshot.forEach((doc) => {
    const parentData = doc.data();
    if (parentData.fcmToken) {
      const message: admin.messaging.Message = {
        token: parentData.fcmToken,
        notification: {
          title: "学習レポート",
          body: `本日の学習: ${report.answerCount}問中 ${report.correctCount}問正解しました！`,
        },
        data: {
          type: "daily_report",
          childUid: uid,
          pointsChange: String(report.pointsChange),
          mode: report.mode,
          timestamp: new Date().toISOString(),
        },
        android: {
          priority: "high",
        },
      };
      messages.push(message);
      parentDocs.push(doc);
    }
  });

  if (messages.length === 0) {
    return { success: true, message: "No valid tokens found." };
  }

  // 3. 送信処理
  const responses = await Promise.all(
    messages.map((msg) => admin.messaging().send(msg)
      .then(() => ({ success: true, error: null }))
      .catch((error) => ({ success: false, error }))
    )
  );

  // 4. エラーハンドリングとクリーンアップ
  const cleanupPromises: Promise<any>[] = [];

  responses.forEach((response, index) => {
    if (!response.success && response.error) {
      const errorCode = response.error.code;

      logger.error(`Error sending to parent ${parentDocs[index].id}:`, response.error);

      if (
        errorCode === "messaging/registration-token-not-registered" ||
        errorCode === "messaging/invalid-registration-token"
      ) {
        logger.info(`Removing invalid token for parent doc: ${parentDocs[index].id}`);
        cleanupPromises.push(parentDocs[index].ref.delete());
      }
    }
  });

  await Promise.all(cleanupPromises);

  return {
    success: true,
    sentCount: messages.length - cleanupPromises.length,
    cleanupCount: cleanupPromises.length,
  };
});