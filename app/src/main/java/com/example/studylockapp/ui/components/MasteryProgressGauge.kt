package com.example.studylockapp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// テーマカラー（紺 × マスタード）
private val NavyPrimary = Color(0xFF163A70)
private val MustardAccent = Color(0xFFD4A72C)
private val GrayOutline = Color(0xFFD8E0EC)

/**
 * 学習進捗ゲージUI（レイアウト・アニメーション完全同期版）
 * 
 * @param absoluteLevel 0〜10の学習レベル
 * @param itemKey 現在の単語IDや問題ID。これが変わるとアニメーションせずにゲージを即時切り替えます。
 */
@Composable
fun MasteryProgressGauge(
    absoluteLevel: Int,
    itemKey: Any,
    modifier: Modifier = Modifier
) {
    val isLongTerm = absoluteLevel > 5

    // 1. ターゲットとなる相対進捗 (0.0 to 1.0) の計算
    val targetProgress = remember(absoluteLevel) {
        val relLevel = when {
            absoluteLevel == 0 -> 0
            absoluteLevel <= 5 -> absoluteLevel
            absoluteLevel == 6 -> 0
            else -> absoluteLevel - 5
        }
        // 5ノード（間隔は4つ）なので、1段階あたり0.25進む
        if (relLevel == 0) 0f else (relLevel - 1) / 4f
    }

    // 2. アニメーション制御用のステート
    val progressAnimatable = remember { Animatable(targetProgress) }
    val nodeScales = remember { List(5) { Animatable(1f) } }
    var lastTriggeredIndex by remember { mutableIntStateOf(-1) }
    var lastItemKey by remember { mutableStateOf<Any?>(null) }

    // 3. アイテム切り替え vs レベルアップ時のみのアニメーション判定
    LaunchedEffect(itemKey, absoluteLevel) {
        val currentVal = progressAnimatable.value
        
        if (lastItemKey != itemKey) {
            // A. 問題自体が変わった場合：即時反映（Snap）
            progressAnimatable.snapTo(targetProgress)
            lastTriggeredIndex = (targetProgress * 4).roundToInt()
            lastItemKey = itemKey
        } else {
            // B. 同じ問題の場合
            if (targetProgress > currentVal) {
                // 進捗が増えた時だけアニメーション
                progressAnimatable.animateTo(
                    targetValue = targetProgress,
                    animationSpec = tween(durationMillis = 500, easing = LinearOutSlowInEasing)
                )
            } else {
                // 減少時や維持、または5→6への切替時は即時反映
                progressAnimatable.snapTo(targetProgress)
                lastTriggeredIndex = (targetProgress * 4).roundToInt()
            }
        }
    }

    // 4. 線の先端がノードに到着した瞬間に発火
    val scope = rememberCoroutineScope()
    LaunchedEffect(progressAnimatable.value) {
        val current = progressAnimatable.value
        for (i in 0 until 5) {
            val threshold = i / 4f
            if (current >= threshold && i > lastTriggeredIndex) {
                lastTriggeredIndex = i
                scope.launch {
                    nodeScales[i].animateTo(1.8f, tween(100, easing = FastOutLinearInEasing))
                    nodeScales[i].animateTo(1.0f, tween(150, easing = LinearOutSlowInEasing))
                }
            }
        }
    }

    // UIサイズ定数
    val nodeSize = 14.dp
    val accentColor = if (isLongTerm) NavyPrimary else MustardAccent

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.BottomCenter // 下（●の行）を基準に配置
        ) {
            // --- レイヤー1: 線（背景と前景） ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = nodeSize / 2) // ●の半径分左右をあける
                    .height(3.dp)
                    .align(Alignment.BottomCenter)
                    .offset(y = -(nodeSize / 2)) // ●の垂直中心を通るように配置
            ) {
                // 背景線
                Box(Modifier.fillMaxSize().background(GrayOutline, CircleShape))
                
                // 前景線 (LV0とLV6は非表示)
                if (absoluteLevel != 0 && absoluteLevel != 6) {
                    Box(
                        Modifier
                            .fillMaxWidth(progressAnimatable.value)
                            .fillMaxHeight()
                            .background(accentColor, CircleShape)
                    )
                }
            }

            // --- レイヤー2: ノード列（ラベル領域 + 固定Spacer + ●） ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom // 下基準で揃える
            ) {
                repeat(5) { index ->
                    val threshold = index / 4f
                    val nodeLevel = if (isLongTerm) index + 6 else index + 1

                    // ドットの点灯判定
                    val isLit = progressAnimatable.value >= threshold && 
                                absoluteLevel != 0 && absoluteLevel != 6

                    val nodeColor by animateColorAsState(
                        targetValue = if (isLit) accentColor else GrayOutline,
                        label = "NodeColor"
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom // 下寄せ
                    ) {
                        // 1. ラベル領域 (20dp固定の高さを確保)
                        Box(
                            modifier = Modifier.height(20.dp),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            if (nodeLevel == absoluteLevel && absoluteLevel != 0) {
                                Text(
                                    text = "LV$nodeLevel",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NavyPrimary
                                )
                            }
                        }

                        // 2. 固定Spacer (8dp)
                        Spacer(modifier = Modifier.height(8.dp))

                        // 3. ● ドット本体
                        Box(
                            modifier = Modifier
                                .size(nodeSize)
                                .scale(nodeScales[index].value)
                                .background(nodeColor, CircleShape)
                        )
                    }
                }
            }
        }
    }
}

/**
 * プレビュー用：動作確認
 */
@Preview(showBackground = true)
@Composable
fun MasteryProgressGaugePreview() {
    var level by remember { mutableStateOf(1) }
    var currentWordId by remember { mutableStateOf(101) }
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFFBFAF5)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MasteryProgressGauge(
                absoluteLevel = level,
                itemKey = currentWordId
            )
            
            Spacer(modifier = Modifier.height(40.dp))
            
            Row {
                androidx.compose.material3.Button(onClick = { 
                    if (level < 10) level++ 
                }) {
                    Text("レベルアップ")
                }
                
                Spacer(Modifier.width(8.dp))

                androidx.compose.material3.Button(onClick = { 
                    if (level > 0) level-- 
                }) {
                    Text("レベルダウン")
                }
                
                Spacer(Modifier.width(8.dp))
                
                androidx.compose.material3.Button(onClick = { 
                    currentWordId++
                    level = (1..10).random()
                }) {
                    Text("次の単語へ")
                }
            }
        }
    }
}
