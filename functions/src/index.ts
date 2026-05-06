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

    // レポート対象は昨日分のみ
    const yesterday = new Date(now.toMillis() - 24 * 60 * 60 * 1000);
    const dateStr = formatTokyoDateYYYYMMDD(yesterday);

    // 東京日付ベースの表示用日付 (M/D)
    const [_, mm, dd] = dateStr.split("-");
    const displayDate = `${Number(mm)}/${Number(dd)}`;

    const promises: Promise<any>[] = [];

    for (const userDoc of usersSnapshot.docs) {
      const uid = userDoc.id;
      const userData = userDoc.data() || {};

      // 1. 既に停止中なら即スキップ
      if (userData.dailyReportPaused === true) continue;

      // 2. 最終アクティブ日時の確認 (toMillis有無で判定)
      let lastActive = userData.lastActiveAt;
      if (!lastActive || typeof lastActive.toMillis !== "function") {
        await userDoc.ref.set({ lastActiveAt: now }, { merge: true });
        lastActive = now;
      }

      // 3. 7日間チェック (一定期間未使用なら停止通知を送ってスキップ)
      const diffMillis = now.toMillis() - lastActive.toMillis();
      if (diffMillis >= sevenDaysMillis) {
        const parentsSnapshot = await db.collection("users").doc(uid).collection("parents").get();
        let sentCount = 0;
        let failCount = 0;

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
              sentCount++;
            } catch (e) {
              console.error(`Final notice failed for parent ${parentDoc.id}:`, e);
              failCount++;
            }
          }
        }

        if (sentCount > 0) {
          console.log(`Sent inactivity notice for ${uid}: ${sentCount} parents notified.`);
        } else if (failCount > 0 || !parentsSnapshot.empty) {
          console.warn(`Failed to send inactivity notice for ${uid}. Check parent tokens.`);
        }

        // ステータスを停止中に更新
        const pauseUpdate: any = {
          dailyReportPaused: true,
          dailyReportPausedAt: now,
          dailyReportPauseReason: "inactive",
        };
        if (sentCount > 0) {
          pauseUpdate.inactivityNoticeSentAt = now;
        }
        await userDoc.ref.set(pauseUpdate, { merge: true });
        continue;
      }

      // --- 4. 通常レポート送信処理 ---
      const statsDoc = await db.collection("users").doc(uid).collection("dailyStats").doc(dateStr).get();
      const stats = statsDoc.data() || {};
      const records = Array.isArray(stats.studyRecords) ? stats.studyRecords : [];

      // 調査用一時ログ
      console.log(`[DailyReport] uid: ${uid}, date: ${dateStr}`);
      console.log(`stats: points=${stats.points}, studyCount=${stats.studyCount}, recordsLength=${records.length}`);
      const studySample = records.filter((r: any) => r.type === "study").slice(0, 3);
      console.log("study records sample:", JSON.stringify(studySample));

      // 獲得/使用ポイント
      const points = stats.points || 0;
      const usedPoints = stats.usedPoints || stats.pointsUsed || 0;

      // 各種集計用
      const gradeMap: Record<string, { total: number, correct: number }> = {};
      let voiceCheckCount = 0;
      let voiceWordCheckCount = 0;
      let voiceSentenceCheckCount = 0;
      let voiceBadgeCount = 0;
      let voiceWordBadges = 0;
      let voiceSentenceBadges = 0;
      let voiceBonusPoints = 0;
      const unlockMap: Record<string, number> = {};

      records.forEach((r: any) => {
        if (r.type === "study" && r.grade !== undefined && r.grade !== null) {
          // 通常学習集計
          const rawGrade = String(r.grade);
          const g = rawGrade.endsWith("級") ? rawGrade : `${rawGrade}級`;
          if (!gradeMap[g]) gradeMap[g] = { total: 0, correct: 0 };
          gradeMap[g].total++;
          if (r.isCorrect === true) gradeMap[g].correct++;
        } else if (r.type === "voice_check") {
          // 発音チェック回数集計
          voiceCheckCount++;
          if (r.checkType === "word") voiceWordCheckCount++;
          if (r.checkType === "sentence") voiceSentenceCheckCount++;
        } else if (r.type === "voice_bonus") {
          // 発音バッジ・ボーナス集計
          voiceBadgeCount++;
          voiceBonusPoints += Number(r.earnedPoints || r.points || 0);
          if (r.checkType === "word") voiceWordBadges++;
          if (r.checkType === "sentence") voiceSentenceBadges++;
        } else if (r.type === "unlock") {
          // アプリ解放集計
          const label = r.appLabel || r.packageName?.split('.').pop() || "不明";
          const mins = r.unlockedMinutes || Math.floor((r.usedPoints || 0) / 2);
          unlockMap[label] = (unlockMap[label] || 0) + mins;
        }
      });

      let studyText = "学習: なし";
      const gradeEntries = Object.entries(gradeMap).sort().slice(0, 3);
      if (gradeEntries.length > 0) {
        studyText = gradeEntries.map(([g, s], i) => {
          const acc = Math.round((s.correct / s.total) * 100);
          const line = `${g}：${s.total}問（正解${s.correct} / 不正解${s.total - s.correct}、正解率${acc}%）`;
          return i === 0 ? `学習: ${line}` : `　　  ${line}`;
        }).join("\n");
      }

      let voiceText = "発音: なし";
      if (voiceCheckCount > 0 || voiceBadgeCount > 0) {
        voiceText = `発音: チェック${voiceCheckCount}回（単語${voiceWordCheckCount} / 例文${voiceSentenceCheckCount}） / バッジ${voiceBadgeCount}個（単語${voiceWordBadges} / 例文${voiceSentenceBadges}、+${voiceBonusPoints}pt）`;
      }

      // マスター累計
      const sMaster = stats.shortMasterCount || 0;
      const lMaster = stats.longMasterCount || 0;

      let unlockText = "解放: なし";
      const unlockEntries = Object.entries(unlockMap).sort((a, b) => b[1] - a[1]).slice(0, 3);
      if (unlockEntries.length > 0) {
        unlockText = `解放: ` + unlockEntries.map(([label, min]) => `${label} ${min}分`).join(", ");
      }

      // アクセシビリティ状態
      const accEnabled = userData.accessibilityEnabled ? "ON" : "OFF";

      const parentsSnapshotInner = await db.collection("users").doc(uid).collection("parents").get();
      parentsSnapshotInner.forEach((parentDoc) => {
        const parentData = parentDoc.data();
        const childName = parentData.childDisplayName || "お子様";

        if (parentData.fcmToken) {
          promises.push(
            admin.messaging().send({
              token: parentData.fcmToken,
              notification: {
                title: `📅 ${displayDate} ${childName}の学習レポート`,
                body: `獲得: ${points}pt / 使用: ${usedPoints}pt\n${studyText}\n${voiceText}\nマスター累計: 短期${sMaster}語 / 長期${lMaster}語\n${unlockText}\n監視: アクセシビリティ${accEnabled}`,
              },
              android: { priority: "high" },
            }).catch((e) => console.error("Report FCM failed", e))
          );
        }
      });
    }

    await Promise.all(promises);
    return null;
  });
