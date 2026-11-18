package com.example.math_application

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.example.math_application.ui.HomeScreen
import com.example.math_application.ui.MathWorkspaceScreen
import com.example.math_application.ui.PageMetrics
import com.example.math_application.ui.PauseRecord
import com.example.math_application.ui.SummaryScreen
import com.example.math_application.ui.theme.Math_ApplicationTheme
import org.json.JSONArray
import org.json.JSONObject

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

private const val PREFS_NAME = "math_meta_prefs"
private const val KEY_STUDENTS_JSON = "students_json"
private const val KEY_NEXT_ID = "students_next_id"

private fun defaultStudents(): List<MathProblem> = listOf(
    MathProblem(
        id = 1,
        title = "김민수",
        description = "풀이 속도 및 멈춤 구간 집중 관찰",
        difficulty = "고2",
        tag = "미적분"
    ),
    MathProblem(
        id = 2,
        title = "이서연",
        description = "서술형 식 전개 과정 분석",
        difficulty = "고1",
        tag = "수학(상)"
    ),
    MathProblem(
        id = 3,
        title = "박지훈",
        description = "모의고사 킬러 문항 접근 패턴 점검",
        difficulty = "고3",
        tag = "확률과 통계"
    )
)

private fun loadStudents(context: Context): Pair<List<MathProblem>, Int> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val jsonStr = prefs.getString(KEY_STUDENTS_JSON, null)
    val savedNextId = prefs.getInt(KEY_NEXT_ID, 4)

    if (jsonStr == null) {
        return defaultStudents() to 4
    }

    return try {
        val arr = JSONArray(jsonStr)
        val list = mutableListOf<MathProblem>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(
                MathProblem(
                    id = obj.getInt("id"),
                    title = obj.getString("title"),
                    description = obj.optString("description", ""),
                    difficulty = obj.optString("difficulty", ""),
                    tag = obj.optString("tag", "")
                )
            )
        }
        val maxId = list.maxOfOrNull { it.id } ?: 0
        list to maxOf(savedNextId, maxId + 1)
    } catch (e: Exception) {
        defaultStudents() to 4
    }
}

private fun saveStudents(context: Context, students: List<MathProblem>, nextId: Int) {
    val arr = JSONArray()
    students.forEach { s ->
        val obj = JSONObject().apply {
            put("id", s.id)
            put("title", s.title)
            put("description", s.description)
            put("difficulty", s.difficulty)
            put("tag", s.tag)
        }
        arr.put(obj)
    }
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_STUDENTS_JSON, arr.toString())
        .putInt(KEY_NEXT_ID, nextId)
        .apply()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Math_ApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val context = LocalContext.current
                    val initialData = remember { loadStudents(context) }
                    val students = remember {
                        mutableStateListOf<MathProblem>().apply {
                            addAll(initialData.first)
                        }
                    }
                    var nextStudentId by remember { mutableIntStateOf(initialData.second) }

                    // 학생 목록이 변경될 때마다 기기에 영구 저장
                    LaunchedEffect(students.toList(), nextStudentId) {
                        saveStudents(context, students.toList(), nextStudentId)
                    }

                    var screen by remember { mutableStateOf(AppScreen.HOME) }
                    var currentProblem by remember { mutableStateOf<MathProblem?>(null) }
                    var summaryStrokeCount by remember { mutableIntStateOf(0) }
                    var summaryPauseCount by remember { mutableIntStateOf(0) }
                    var summaryMaxPauseMs by remember { mutableStateOf(0L) }
                    val summaryPauseRecords = remember { mutableStateListOf<PauseRecord>() }
                    var summaryPageMetrics by remember { mutableStateOf<List<PageMetrics>>(emptyList()) }

                    when (screen) {
                        AppScreen.HOME -> HomeScreen(
                            students = students,
                            onAddStudent = { name, grade, note ->
                                students.add(
                                    MathProblem(
                                        id = nextStudentId++,
                                        title = name,
                                        description = note,
                                        difficulty = grade,
                                        tag = ""
                                    )
                                )
                            },
                            onDeleteStudent = { student ->
                                students.remove(student)
                            },
                            onProblemSelected = { problem ->
                                currentProblem = problem
                                screen = AppScreen.WORKSPACE
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        AppScreen.WORKSPACE -> MathWorkspaceScreen(
                            currentProblem = currentProblem,
                            onBack = { screen = AppScreen.HOME },
                            onSummaryClicked = { screen = AppScreen.SUMMARY },
                            onSummaryData = { strokeCount, pauseCount, maxPauseMs, records ->
                                summaryStrokeCount = strokeCount
                                summaryPauseCount = pauseCount
                                summaryMaxPauseMs = maxPauseMs
                                summaryPauseRecords.clear()
                                summaryPauseRecords.addAll(records)
                                screen = AppScreen.SUMMARY
                            },
                            onSummaryPageMetrics = { metricsList ->
                                summaryPageMetrics = metricsList
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        AppScreen.SUMMARY -> SummaryScreen(
                            strokeCount = summaryStrokeCount,
                            pauseCount = summaryPauseCount,
                            maxPauseMs = summaryMaxPauseMs,
                            pauseRecords = summaryPauseRecords.toList(),
                            pageMetricsList = summaryPageMetrics,
                            examTitle = currentProblem?.title ?: "모의고사 풀이",
                            onBack = { screen = AppScreen.HOME },
                            onRetry = {
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
                students = defaultStudents(),
                onAddStudent = { _, _, _ -> },
                onDeleteStudent = {},
                onProblemSelected = {},
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}