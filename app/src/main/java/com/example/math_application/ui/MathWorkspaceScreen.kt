package com.example.math_application.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.math_application.MathProblem
import kotlin.OptIn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MathWorkspaceScreen(
    currentProblem: MathProblem?,
    onBack: () -> Unit,
    onSummaryClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    var strokeCount by remember { mutableIntStateOf(0) }
    var pauseCount by remember { mutableIntStateOf(0) }
    var lastPauseMs by remember { mutableStateOf(0L) }
    var clearSignal by remember { mutableIntStateOf(0) }
    val pauseRecords = remember { mutableStateListOf<PauseRecord>() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = currentProblem?.title ?: "필기 풀이",
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    Button(onClick = onBack) {
                        Text(text = "←")
                    }
                },
                actions = {
                    Row(
                        modifier = Modifier.padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Stroke=$strokeCount  Pause=$pauseCount  LastPauseMs=$lastPauseMs",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Button(onClick = onSummaryClicked) {
                            Text(text = "요약")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 문제 제시 카드
            currentProblem?.let { problem ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "문제 ${problem.id}. ${problem.title}",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Text(
                            text = problem.description,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .align(Alignment.Start)
                                .padding(top = 8.dp)
                        )
                        Row(
                            modifier = Modifier
                                .align(Alignment.Start)
                                .padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "난이도: ${problem.difficulty}   태그: ${problem.tag}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 중앙 필기 캔버스 영역
            SimpleDrawingCanvas(
                modifier = Modifier.weight(1f),
                clearSignal = clearSignal,
                onPauseDetected = { pauseMs, strokeId ->
                    pauseCount++
                    lastPauseMs = pauseMs
                    pauseRecords.add(
                        PauseRecord(
                            timestampMillis = System.currentTimeMillis(),
                            pauseMs = pauseMs,
                            strokeId = strokeId
                        )
                    )
                },
                onStrokeAdded = { newCount ->
                    strokeCount = newCount
                }
            )

            // 하단 바: 펜 두께 및 메타인지 상태 선택 뼈대
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "펜 두께:",
                    style = MaterialTheme.typography.labelSmall
                )
                Button(onClick = {}) { Text(text = "2px") }
                Button(onClick = {}) { Text(text = "4px") }
                Button(onClick = {}) { Text(text = "6px") }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "메타인지 상태:",
                    style = MaterialTheme.typography.labelSmall
                )
                Button(onClick = {}) { Text(text = "집중") }
                Button(onClick = {}) { Text(text = "막힘") }
                Button(onClick = {}) { Text(text = "검토") }
                Button(
                    onClick = {
                        pauseRecords.clear()
                        strokeCount = 0
                        pauseCount = 0
                        lastPauseMs = 0L
                        clearSignal++
                    }
                ) {
                    Text(text = "Clear")
                }
            }
        }
    }
}