import * as functions from "firebase-functions/v1";
import * as admin from "firebase-admin";

admin.initializeApp();
const db = admin.firestore();

// ===== 共通ユーティリティ =====
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

/**
 * グレード表示の変換（GradeUtils.kt と完全に同期）
 */
function formatGradeLabel(rawGrade: any): string {
  const raw = String(rawGrade ?? "").trim();
  const mapping: Record<string, string> = {
    "1": "5級", "2": "4級", "3": "3級", "4": "準2級", "5": "2級", "6": "準1級", "7": "1級"
  };
  if (Object.values(mapping).includes(raw)) return raw;
  const g = raw.replace("級", "").trim();
  return mapping[g] || "不明";
}

/**
 * 集計・ソート用に級キーを正規化する
 */
function normalizeGradeKey(rawGrade: any): string {
  const raw = String(rawGrade ?? "").trim();
  const displayToKey: Record<string, string> = {
    "5級": "1", "4級": "2", "3級": "3", "準2級": "4", "2級": "5", "準1級": "6", "1級": "7",
  };
  if (/^[1-7]$/.test(raw)) return raw;
  return displayToKey[raw] || "unknown";
}

// ■ 1. 解除コード通知
export const requestUnlockCode = functions
  .region("asia-northeast1")
  .https.onCall(async (data: any, context: any) => {
    const uid = context.auth?.uid;
    if (!uid) throw new functions.https.HttpsError("unauthenticated", "Authentication required");
    const code = data.code;
    if (typeof code !== 'string' || !/^\d{6}$/.test(code)) throw new functions.https.HttpsError("invalid-argument", "Invalid code");

    const parentsSnapshot = await db.collection("users").doc(uid).collection("parents").get();
    if (parentsSnapshot.empty) return { success: false, message: "保護者が登録されていません" };

    const messages: admin.messaging.Message[] = [];
    parentsSnapshot.forEach((doc) => {
      const parentData = doc.data();
      if (parentData.fcmToken) {
        messages.push({
          token: parentData.fcmToken,
          notification: {
            title: "🔑 解除コード",
            body: `コード: ${code}\n${parentData.childDisplayName || "お子様"}が管理画面へのアクセスを求めています。`,
          },
          android: { priority: "high" },
        });
      }
    });
    await Promise.all(messages.map((msg) => admin.messaging().send(msg).catch((e) => console.error("FCM failed", e))));
    return { success: true };
  });

// ■ 2. セキュリティ警告
export const sendSecurityAlert = functions
  .region("asia-northeast1")
  .https.onCall(async (data: any, context: any) => {
    const uid = context.auth?.uid;
    if (!uid) throw new functions.https.HttpsError("unauthenticated", "Authentication required");
    const alertType = data.alertType || "unknown";
    const timestamp = formatTokyoTimestamp();

    const parentsSnapshot = await db.collection("users").doc(uid).collection("parents").get();
    if (parentsSnapshot.empty) return { success: false };

    const messages: admin.messaging.Message[] = [];
    parentsSnapshot.forEach((doc) => {
      const parentData = doc.data();
      const childName = parentData.childDisplayName || "お子様";
      let body = `${childName}が設定を変更しました。\n時刻: ${timestamp}`;
      if (alertType === "accessibility_disabled") body = `⚠️ ${childName}が「アクセシビリティ権限」をOFFにしました！監視が無効です。\n時刻: ${timestamp}`;
      else if (alertType === "accessibility_enabled") body = `${childName}が「アクセシビリティ権限」をONにしました。\n時刻: ${timestamp}`;

      if (parentData.fcmToken) {
        messages.push({ token: parentData.fcmToken, notification: { title: "⚠️ セキュリティアラート", body }, android: { priority: "high" } });
      }
    });
    await Promise.all(messages.map((msg) => admin.messaging().send(msg).catch((e) => console.error("FCM failed", e))));
    return { success: true };
  });

// ■ 3. 日次レポート
export const sendDailyReport = functions
  .region("asia-northeast1")
  .pubsub.schedule("every day 07:30")
  .timeZone("Asia/Tokyo")
  .onRun(async () => {
    const usersSnapshot = await db.collection("users").where("role", "==", "child").get();
    if (usersSnapshot.empty) return null;

    const now = admin.firestore.Timestamp.now();
    const yesterday = new Date(now.toMillis() - 24 * 60 * 60 * 1000);
    const dateStr = formatTokyoDateYYYYMMDD(yesterday);
    const displayDate = `${Number(dateStr.split("-")[1])}/${Number(dateStr.split("-")[2])}`;

    for (const userDoc of usersSnapshot.docs) {
      const uid = userDoc.id;
      const userData = userDoc.data();
      if (userData.dailyReportPaused === true) continue;

      const statsDoc = await db.collection("users").doc(uid).collection("dailyStats").doc(dateStr).get();
      if (!statsDoc.exists) continue;
      const stats = statsDoc.data() || {};
      if (stats.reportSent === true) continue;

      const studyRecords = Array.isArray(stats.studyRecords) ? stats.studyRecords : [];
      const unlockRecords = Array.isArray(stats.unlockRecords) ? stats.unlockRecords : [];

      // --- 1. ポイント集計ロジック ---
      const calculatedEarnedPoints = studyRecords.reduce(
        (sum: number, r: any) => sum + Number(r.earnedPoints ?? 0),
        0
      );
      const calculatedUsedPoints = unlockRecords.reduce(
        (sum: number, r: any) =>
          r.type === "unlock" ? sum + Number(r.usedPoints ?? 0) : sum,
        0
      );

      const hasTopLevelPoints = stats.points !== undefined && stats.points !== null;
      const hasTopLevelUsed = stats.usedPoints !== undefined && stats.usedPoints !== null;

      const dailyEarned = hasTopLevelPoints ? Number(stats.points) : calculatedEarnedPoints;
      const displayUsedPoints = hasTopLevelUsed ? Number(stats.usedPoints) : calculatedUsedPoints;

      let ownedPoints: number;
      if (stats.lastKnownPoints !== undefined && stats.lastKnownPoints !== null) {
        ownedPoints = Number(stats.lastKnownPoints);
      } else if (hasTopLevelPoints && hasTopLevelUsed) {
        ownedPoints = dailyEarned - displayUsedPoints;
      } else {
        ownedPoints = calculatedEarnedPoints - calculatedUsedPoints;
      }

      // ポイント:60【獲得:148/使用:170】
      const pointsDisplay = `ポイント:${ownedPoints}【獲得:${dailyEarned}/使用:${displayUsedPoints}】`;

      // --- 2. 学習・発音集計 ---
      const gradeMap: Record<string, { total: number, correct: number }> = {};
      let voiceCheckCount = 0;
      let voiceBadgeCount = 0;
      let voiceBonusPoints = 0;

      studyRecords.forEach((r: any) => {
        if (r.type === "study") {
          const gKey = normalizeGradeKey(r.grade);
          if (!gradeMap[gKey]) gradeMap[gKey] = { total: 0, correct: 0 };
          gradeMap[gKey].total++;
          if (r.isCorrect === true) gradeMap[gKey].correct++;
        } else if (r.type === "voice_check") {
          voiceCheckCount++;
        } else if (r.type === "voice_bonus") {
          voiceBadgeCount++;
          voiceBonusPoints += Number(r.earnedPoints || 0);
        }
      });

      let studyText = "学習:なし";
      const gradeEntries = Object.entries(gradeMap).sort((a, b) => {
        if (a[0] === "unknown") return 1;
        if (b[0] === "unknown") return -1;
        return Number(a[0]) - Number(b[0]);
      });
      if (gradeEntries.length > 0) {
        studyText = gradeEntries.map(([gKey, s], i) => {
          const gLabel = gKey === "unknown" ? "不明" : formatGradeLabel(gKey);
          const acc = Math.round((s.correct / (s.total || 1)) * 100);
          // 5級:10問 〇8/×2 80%
          const line = `${gLabel}:${s.total}問 〇${s.correct}/×${s.total - s.correct} ${acc}%`;
          return i === 0 ? `学習:${line}` : `     ${line}`;
        }).join("\n");
      }

      let voiceText = "発音:なし";
      if (voiceCheckCount > 0 || voiceBadgeCount > 0) {
        voiceText = `発音:チェック${voiceCheckCount}/バッジ${voiceBadgeCount}(+${voiceBonusPoints}pt)`;
      }

      // --- 3. マスター累計 ---
      const lv1 = Number(stats.lv1Count ?? 0);
      const lv2 = Number(stats.lv2Count ?? 0);
      const lv3 = Number(stats.lv3Count ?? 0);
      const shortMaster = Number(stats.shortMasterCount ?? 0);
      const longMaster = Number(stats.longMasterCount ?? 0);
      // Lv:1=15/2=7/3=0 ★0 🏆0
      const masterText = `Lv:1=${lv1}/2=${lv2}/3=${lv3} ★${shortMaster} 🏆${longMaster}`;

      // --- 4. 解放履歴の構築 ---
      let totalUnlockMins = 0;
      const unlockDetails: string[] = [];

      unlockRecords.forEach((r: any) => {
        if (r.type === "unlock") {
          const pts = Number(r.usedPoints || 0);
          const mins = Number(r.unlockedMinutes || 0);
          totalUnlockMins += mins;
          // - Chrome 1分 10pt
          unlockDetails.push(`- ${r.appLabel || "不明"} ${mins}分 ${pts}pt`);
        }
      });

      let unlockText = "解放:なし";
      if (unlockDetails.length > 0) {
        // 解放:1回/1分/10pt
        const unlockSummaryShort = `解放:${unlockDetails.length}回/${totalUnlockMins}分/${displayUsedPoints}pt`;
        unlockText = `${unlockSummaryShort}\n${unlockDetails.join("\n")}`;
      }

      const accEnabled = userData.accessibilityEnabled ? "ON" : "OFF";
      const reportText = `${pointsDisplay}\n${studyText}\n${voiceText}\n${masterText}\n${unlockText}\n監視:${accEnabled}`;

      const parentsSnapshot = await db.collection("users").doc(uid).collection("parents").get();
      const sendPromises: Promise<any>[] = [];
      parentsSnapshot.forEach((parentDoc) => {
        const parentData = parentDoc.data();
        if (parentData.fcmToken) {
          sendPromises.push(admin.messaging().send({
            token: parentData.fcmToken,
            notification: { title: `📅 ${displayDate} ${parentData.childDisplayName || userData.displayName || "お子様"}のレポート`, body: reportText },
            android: { priority: "high" },
          }));
        }
      });

      if (sendPromises.length > 0) {
        try {
          await Promise.all(sendPromises);
          await statsDoc.ref.update({
            reportText: reportText,
            reportSent: true,
            reportSentAt: admin.firestore.FieldValue.serverTimestamp(),
          });
        } catch (e) { console.error(`Failed for uid: ${uid}`, e); }
      }
    }
    return null;
  });
