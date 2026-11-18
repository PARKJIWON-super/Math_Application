package com.example.math_application.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.math_application.ai.AiFeedbackHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(
    strokeCount: Int = 0,
    pauseCount: Int = 0,
    maxPauseMs: Long = 0L, // [변경] lastPauseMs -> maxPauseMs (실제 의미: 전체 중 가장 긴 멈춤)
    pauseRecords: List<PauseRecord> = emptyList(),
    pageMetricsList: List<PageMetrics> = emptyList(),
    examTitle: String = "모의고사 풀이",
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var feedbackLoading by remember { mutableStateOf(false) }
    var feedbackResult by remember { mutableStateOf<String?>(null) }
    var feedbackError by remember { mutableStateOf<String?>(null) }

    val sortedPages = remember(pageMetricsList) {
        pageMetricsList.sortedBy { it.pageNumber }
    }

    // [추가] 실제로 필기/멈춤 기록이 있는 페이지만 AI에 보낸다. (페이지만 열어보고 안 쓴 경우 제외)
    val activePages = remember(sortedPages) {
        sortedPages.filter { it.strokeCount > 0 || it.pauseCount > 0 }
    }
    // [추가] 보낼 데이터가 하나도 없으면 AI 요청을 막는다.
    val hasData = activePages.isNotEmpty() || strokeCount > 0 || pauseCount > 0

    // 풀이 데이터가 바뀌면 이전 AI 피드백 결과/에러를 초기화
    LaunchedEffect(sortedPages, strokeCount, pauseCount) {
        feedbackResult = null
        feedbackError = null
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "풀이 요약",
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    Button(onClick = onBack) {
                        Text(text = "←")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            Text(
                text = "이번 풀이 기록",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(12.dp))

            // 전체 합산 카드
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "전체 요약",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "총 획 수: $strokeCount",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "총 Pause 횟수: $pauseCount",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Text(
                        text = "최대 Pause 시간: $maxPauseMs ms",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    if (pauseRecords.isNotEmpty()) {
                        Text(
                            // [변경] 페이지 번호를 함께 표시 (예: p2-획3(1500ms))
                            text = "전체 구간별 멈춤: " + pauseRecords.joinToString(", ") {
                                "p${it.pageNumber}-획${it.strokeId}(${it.pauseMs}ms)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }

            // 페이지별 상세 카드
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "페이지별 기록",
                        style = MaterialTheme.typography.titleMedium
                    )

                    if (sortedPages.isEmpty()) {
                        Text(
                            text = "저장된 페이지별 기록이 없습니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        sortedPages.forEach { pm ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = "${pm.pageNumber}페이지",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "총 획 수: ${pm.strokeCount}개",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                    Text(
                                        text = "총 Pause 횟수: ${pm.pauseCount}회",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                    Text(
                                        text = "최대 Pause 시간: ${pm.maxPauseMs} ms",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                    if (pm.pauseRecords.isNotEmpty()) {
                                        Text(
                                            text = "구간 멈춤: " + pm.pauseRecords.joinToString(", ") {
                                                "획${it.strokeId}(${it.pauseMs}ms)"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // AI 메타인지 피드백 카드
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "AI 메타인지 피드백",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            if (feedbackLoading) return@Button
                            feedbackLoading = true
                            feedbackResult = null
                            feedbackError = null
                            coroutineScope.launch {
                                try {
                                    val result = withContext(Dispatchers.IO) {
                                        if (activePages.isNotEmpty()) {
                                            val items = activePages.map { pm ->
                                                AiFeedbackHelper.ItemFeedbackInput(
                                                    pageNumber = pm.pageNumber,
                                                    strokeCount = pm.strokeCount,
                                                    pauseCount = pm.pauseCount,
                                                    maxPauseMs = pm.maxPauseMs,
                                                    pauseRecords = pm.pauseRecords
                                                )
                                            }
                                            AiFeedbackHelper.generateMultiItemFeedback(
                                                examTitle = examTitle,
                                                items = items
                                            )
                                        } else {
                                            AiFeedbackHelper.generateFeedback(
                                                problemTitle = examTitle,
                                                problemDescription = "",
                                                strokeCount = strokeCount,
                                                pauseCount = pauseCount,
                                                maxPauseMs = maxPauseMs,
                                                pauseRecords = pauseRecords
                                            )
                                        }
                                    }
                                    if (result.success) {
                                        feedbackResult = result.message
                                    } else {
                                        feedbackError = result.error ?: "피드백을 불러오지 못했습니다."
                                    }
                                } catch (e: Exception) {
                                    feedbackError = e.message ?: "오류가 발생했습니다."
                                } finally {
                                    feedbackLoading = false
                                }
                            }
                        },
                        enabled = hasData, // [추가] 기록이 없으면 비활성화
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (feedbackLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(text = "AI 피드백 요청")
                        }
                    }

                    if (!hasData) {
                        Text(
                            text = "풀이 기록이 없어 AI 피드백을 요청할 수 없어요. 문제를 풀고 다시 확인해 주세요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .align(Alignment.Start)
                                .padding(top = 8.dp)
                        )
                    }

                    if (feedbackLoading) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        )
                    }

                    feedbackResult?.let { result ->
                        Text(
                            // [추가] 프롬프트에서 마크다운을 금지했지만, 혹시 섞여 와도 ** 가 그대로 보이지 않게 제거
                            text = result.replace("**", ""),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .align(Alignment.Start)
                                .padding(top = 8.dp)
                        )
                    }

                    feedbackError?.let { error ->
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .align(Alignment.Start)
                                .padding(top = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "다시 풀기")
                }
                Button(
                    onClick = onBack,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "문제 목록으로")
                }
            }
        }
    }
}