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
    const uid = context.auth?.uid;
    if (!uid) {
      throw new functions.https.HttpsError("unauthenticated", "Authentication required");
    }

    const code = data.code;
    if (typeof code !== 'string' || !/^\d{6}$/.test(code)) {
      throw new functions.https.HttpsError("invalid-argument", "Invalid code format (6 digits required)");
    }

    const parentsRef = db.collection("users").doc(uid).collection("parents");
    const parentsSnapshot = await parentsRef.get();

    if (parentsSnapshot.empty) {
      return { success: false, message: "保護者が登録されていません" };
    }

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

    if (messages.length === 0) {
      return { success: false, message: "通知可能な親端末が見つかりません (FCMトークン未設定)" };
    }

    await Promise.all(messages.map((msg) =>
      admin.messaging().send(msg).catch((e) => console.error("FCM send failed", e))
    ));

    return { success: true };
  });

// ■ 2. セキュリティ警告（不正検知→親）
export const sendSecurityAlert = functions
  .region("asia-northeast1")
  .https.onCall(async (data: any, context: any) => {
    const uid = context.auth?.uid;
    if (!uid) {
      throw new functions.https.HttpsError("unauthenticated", "Authentication required");
    }

    const alertType = data.alertType || "unknown";
    const timestamp = formatTokyoTimestamp();

    const parentsRef = db.collection("users").doc(uid).collection("parents");
    const parentsSnapshot = await parentsRef.get();

    if (parentsSnapshot.empty) {
      return { success: false, message: "保護者が登録されていません" };
    }

    const messages: admin.messaging.Message[] = [];
    parentsSnapshot.forEach((doc) => {
      const parentData = doc.data();
      const childName = parentData.childDisplayName || "お子様";

      const title = "⚠️ セキュリティアラート";
      let body = `${childName}が設定を変更しました。\n時刻: ${timestamp}`;

      if (alertType === "accessibility_disabled") {
        body = `⚠️ ${childName}が「アクセシビリティ権限」をOFFにしました！監視が無効化されています。\n時刻: ${timestamp}`;
      } else if (alertType === "accessibility_enabled") {
        body = `${childName}が「アクセシビリティ権限」をONにしました。\n時刻: ${timestamp}`;
      }

      if (parentData.fcmToken) {
        messages.push({
          token: parentData.fcmToken,
          notification: { title, body },
          android: { priority: "high" },
        });
      }
    });

    if (messages.length === 0) {
      return { success: false, message: "通知可能な親端末が見つかりません" };
    }

    let successCount = 0;
    await Promise.all(messages.map((msg) =>
      admin.messaging().send(msg)
        .then(() => { successCount++; })
        .catch((e) => console.error("Security alert FCM failed", e))
    ));

    if (successCount === 0) {
      return { success: false, message: "全通知の送信に失敗しました" };
    }

    return { success: true };
  });

// ■ 3. 日次レポート（毎日 朝7:30 / Tokyo基準）
export const sendDailyReport = functions
  .region("asia-northeast1")
  .pubsub.schedule("every day 07:30")
  .timeZone("Asia/Tokyo")
  .onRun(async () => {
    const usersSnapshot = await db.collection("users").where("role", "==", "child").get();
    if (usersSnapshot.empty) return null;

    const now = admin.firestore.Timestamp.now();
    const sevenDaysMillis = 7 * 24 * 60 * 60 * 1000;

    // レポート対象は昨日分
    const yesterday = new Date(now.toMillis() - 24 * 60 * 60 * 1000);
    const dateStr = formatTokyoDateYYYYMMDD(yesterday);

    // 東京日付ベースの表示用日付 (M/D)
    const [_, mm, dd] = dateStr.split("-");
    const displayDate = `${Number(mm)}/${Number(dd)}`;

    for (const userDoc of usersSnapshot.docs) {
      const uid = userDoc.id;
      const userData = userDoc.data() || {};

      if (userData.dailyReportPaused === true) continue;

      let lastActive = userData.lastActiveAt;
      if (!lastActive || typeof lastActive.toMillis !== "function") {
        await userDoc.ref.set({ lastActiveAt: now }, { merge: true });
        lastActive = now;
      }

      const diffMillis = now.toMillis() - lastActive.toMillis();
      if (diffMillis >= sevenDaysMillis) {
        const parentsSnapshot = await db.collection("users").doc(uid).collection("parents").get();
        for (const parentDoc of parentsSnapshot.docs) {
          const parentData = parentDoc.data();
          const childName = parentData.childDisplayName || userData.displayName || "お子様";
          if (parentData.fcmToken) {
            try {
              await admin.messaging().send({
                token: parentData.fcmToken,
                notification: {
                  title: "日次レポートの一旦停止",
                  body: `${childName}のアプリ起動が7日間ないため、日次レポート通知を一旦停止します。子端末でアプリを起動すると自動で再開されます。`,
                },
                android: { priority: "high" },
              });
            } catch (e) { console.error("Final notice failed", e); }
          }
        }
        await userDoc.ref.set({
          dailyReportPaused: true,
          dailyReportPausedAt: now,
          dailyReportPauseReason: "inactive",
        }, { merge: true });
        continue;
      }

      // --- 通常レポート送信処理 ---
      const statsDoc = await db.collection("users").doc(uid).collection("dailyStats").doc(dateStr).get();
      if (!statsDoc.exists) {
        console.log(`[DailyReport] No stats for uid: ${uid}, date: ${dateStr}. Skipping.`);
        continue;
      }

      const stats = statsDoc.data() || {};

      // 1万人規模を見据えた明細削除後の考慮
      // 1. すでに詳細削除済みなら保存されたレポートテキストを再利用可能にする（今回は新規送信）
      if (stats.detailDeleted === true && stats.reportText) {
         console.log(`[DailyReport] Detailed records already deleted for uid: ${uid}. Skipping.`);
         continue;
      }

      const studyRecords = Array.isArray(stats.studyRecords) ? stats.studyRecords : [];
      const usedRecords = Array.isArray(stats.usedRecords) ? stats.usedRecords : [];
      const unlockRecords = Array.isArray(stats.unlockRecords) ? stats.unlockRecords : [];
      const allRecords = [...studyRecords, ...usedRecords, ...unlockRecords];

      console.log(`[DailyReport] uid=${uid}, targetDate=${dateStr}`);

      const points = stats.points || 0;
      const usedPoints = stats.usedPoints || stats.pointsUsed || 0;

      const gradeMap: Record<string, { total: number, correct: number }> = {};
      let voiceCheckCount = 0;
      let voiceWordCheckCount = 0;
      let voiceSentenceCheckCount = 0;
      let voiceBadgeCount = 0;
      let voiceBonusPoints = 0;
      const unlockMap: Record<string, { mins: number, pts: number }> = {};

      allRecords.forEach((r: any) => {
        const type = r.type || r.mode;
        if (type === "study") {
          const rawGrade = r.grade !== undefined && r.grade !== null ? String(r.grade) : "不明";
          const g = (rawGrade === "不明" || rawGrade.endsWith("級")) ? rawGrade : `${rawGrade}級`;
          if (!gradeMap[g]) gradeMap[g] = { total: 0, correct: 0 };
          gradeMap[g].total++;
          if (r.isCorrect === true) gradeMap[g].correct++;
        } else if (type === "voice_check") {
          voiceCheckCount++;
          if (r.checkType === "word") voiceWordCheckCount++;
          if (r.checkType === "sentence") voiceSentenceCheckCount++;
        } else if (type === "voice_bonus") {
          voiceBadgeCount++;
          voiceBonusPoints += Number(r.earnedPoints || r.points || 0);
        } else if (type === "unlock" || type === "used_points") {
          const label = r.appLabel || r.packageName?.split('.').pop() || "不明アプリ";
          const mins = Number(r.unlockedMinutes || 0);
          const pts = Number(r.usedPoints || r.pointsUsed || 0);
          if (!unlockMap[label]) unlockMap[label] = { mins: 0, pts: 0 };
          unlockMap[label].mins += mins;
          unlockMap[label].pts += pts;
        }
      });

      let studyText = "学習: なし";
      const gradeEntries = Object.entries(gradeMap).sort();
      if (gradeEntries.length > 0) {
        studyText = gradeEntries.map(([g, s], i) => {
          const acc = Math.round((s.correct / (s.total || 1)) * 100);
          const line = `${g}：${s.total}問（正解${s.correct} / 不正解${s.total - s.correct}、正解率${acc}%）`;
          return i === 0 ? `学習: ${line}` : `　　  ${line}`;
        }).join("\n");
      } else if ((stats.studyCount || 0) > 0) {
        studyText = `学習: ${stats.studyCount}問（正解${stats.correctCount || 0} / 不正解${(stats.studyCount || 0) - (stats.correctCount || 0)}）`;
      }

      let voiceText = "発音: なし";
      if (voiceCheckCount > 0 || voiceBadgeCount > 0) {
        voiceText = `発音: チェック${voiceCheckCount}回 / バッジ${voiceBadgeCount}個（+${voiceBonusPoints}pt）`;
      }

      let unlockText = "解放: なし";
      const unlockEntries = Object.entries(unlockMap).sort((a, b) => b[1].pts - a[1].pts);
      if (unlockEntries.length > 0) {
        unlockText = `解放: ` + unlockEntries.map(([label, data]) => `${label} ${data.mins}分（${data.pts}pt）`).join(", ");
      } else if (usedPoints > 0) {
        unlockText = `解放: あり（${usedPoints}pt使用）`;
      }

      const sMaster = stats.shortMasterCount || 0;
      const lMaster = stats.longMasterCount || 0;
      const accEnabled = userData.accessibilityEnabled ? "ON" : "OFF";

      // 送信・保存用の全文レポートテキストを生成
      const reportText = `獲得: ${points}pt / 使用: ${usedPoints}pt\n${studyText}\n${voiceText}\nマスター累計: 短期${sMaster}語 / 長期${lMaster}語\n${unlockText}\n監視: アクセシビリティ${accEnabled}`;

      const reportSummary = {
        points,
        usedPoints,
        studyCount: stats.studyCount || 0,
        voiceCheckCount,
        unlockCount: unlockEntries.length,
        shortMasterCount: sMaster,
        longMasterCount: lMaster
      };

      const parentsSnapshotInner = await db.collection("users").doc(uid).collection("parents").get();
      const sendPromises: Promise<any>[] = [];

      parentsSnapshotInner.forEach((parentDoc) => {
        const parentData = parentDoc.data();
        if (parentData.fcmToken) {
          const childName = parentData.childDisplayName || userData.displayName || "お子様";
          sendPromises.push(
            admin.messaging().send({
              token: parentData.fcmToken,
              notification: {
                title: `📅 ${displayDate} ${childName}の学習レポート`,
                body: reportText,
              },
              android: { priority: "high" },
            })
          );
        }
      });

      if (sendPromises.length > 0) {
        try {
          await Promise.all(sendPromises);

          // 送信成功時のみ明細を削除し、結果を保存
          // detailDeleted: true は「旧形式の配列明細を削除済み」を意味する
          await statsDoc.ref.update({
            reportText: reportText,
            reportSummary: reportSummary,
            reportSentAt: admin.firestore.FieldValue.serverTimestamp(),
            detailDeleted: true,
            studyRecords: admin.firestore.FieldValue.delete(),
            unlockRecords: admin.firestore.FieldValue.delete(),
            usedRecords: admin.firestore.FieldValue.delete(),
          });
          console.log(`[DailyReport] Sent and cleaned up uid: ${uid}`);
        } catch (e) {
          console.error(`[DailyReport] Failed to send or cleanup uid: ${uid}`, e);
        }
      }
    }

    return null;
  });
