package com.example.math_application

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.math_application.ui.HomeScreen
import com.example.math_application.ui.MathWorkspaceScreen
import com.example.math_application.ui.PauseRecord
import com.example.math_application.ui.SummaryScreen
import com.example.math_application.ui.theme.Math_ApplicationTheme

enum class AppScreen {
    HOME,
    WORKSPACE,
    SUMMARY
}

data class MathProblem(
    val id: Int,
    val title: String,
    val description: String,
    val difficulty: String,
    val tag: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Math_ApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var screen by remember { mutableStateOf(AppScreen.HOME) }
                    var currentProblem by remember { mutableStateOf<MathProblem?>(null) }
                    var summaryStrokeCount by remember { mutableIntStateOf(0) }
                    var summaryPauseCount by remember { mutableIntStateOf(0) }
                    var summaryLastPauseMs by remember { mutableStateOf(0L) }
                    val summaryPauseRecords = remember { mutableStateListOf<PauseRecord>() }

                    when (screen) {
                        AppScreen.HOME -> HomeScreen(
                            onProblemSelected = { problem ->
                                currentProblem = problem
                                screen = AppScreen.WORKSPACE
                            },
                            onSummaryClicked = { screen = AppScreen.SUMMARY },
                            modifier = Modifier.fillMaxSize()
                        )
                        AppScreen.WORKSPACE -> MathWorkspaceScreen(
                            currentProblem = currentProblem,
                            onBack = { screen = AppScreen.HOME },
                            onSummaryClicked = { screen = AppScreen.SUMMARY },
                            onSummaryData = { strokeCount, pauseCount, lastPauseMs, records ->
                                summaryStrokeCount = strokeCount
                                summaryPauseCount = pauseCount
                                summaryLastPauseMs = lastPauseMs
                                summaryPauseRecords.clear()
                                summaryPauseRecords.addAll(records)
                                screen = AppScreen.SUMMARY
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        AppScreen.SUMMARY -> SummaryScreen(
                            strokeCount = summaryStrokeCount,
                            pauseCount = summaryPauseCount,
                            lastPauseMs = summaryLastPauseMs,
                            pauseRecords = summaryPauseRecords.toList(),
                            onBack = { screen = AppScreen.HOME },
                            onRetry = {
                                currentProblem = null
                                screen = AppScreen.WORKSPACE
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainPreview() {
    Math_ApplicationTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            HomeScreen(
                onProblemSelected = {},
                onSummaryClicked = {},
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}