package com.example.math_application.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.math_application.MathProblem

@Composable
fun HomeScreen(
    onProblemSelected: (MathProblem) -> Unit,
    onSummaryClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sampleProblems = listOf(
        MathProblem(id = 1, title = "미분 문제", description = "f(x) = x^3 + 2x^2 - 5x + 1 의 미분값을 구하시오.", difficulty = "중", tag = "미분"),
        MathProblem(id = 2, title = "방정식 풀이", description = "2x^2 - 5x + 3 = 0 의 실수 해를 구하시오.", difficulty = "상", tag = "방정식"),
        MathProblem(id = 3, title = "확률 계산", description = "주사위를 2번 던져 합이 7이 나올 확률을 구하시오.", difficulty = "하", tag = "확률")
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            text = "수학 메타인지 분석 앱",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.align(Alignment.Start)
        )
        Text(
            text = "문제를 선택하고 필기 풀이하세요.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .align(Alignment.Start)
                .padding(top = 8.dp, bottom = 24.dp)
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            sampleProblems.forEach { problem ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clickable { onProblemSelected(problem) },
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "문제 ${problem.id}. ${problem.title}",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.align(Alignment.Start)
                            )
                            Text(
                                text = problem.description,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .align(Alignment.Start)
                                    .padding(top = 4.dp)
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
                        Text(text = "→", style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onSummaryClicked,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "최근 풀이 요약 보기")
        }
    }
}