package com.example.math_application.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.math_application.MathProblem
import com.example.math_application.ui.components.EmptyInfoBanner
import com.example.math_application.ui.components.MathSectionCard

@Composable
fun HomeScreen(
    students: List<MathProblem>,
    onAddStudent: (name: String, grade: String, note: String) -> Unit,
    onDeleteStudent: (MathProblem) -> Unit,
    onProblemSelected: (MathProblem) -> Unit,
    modifier: Modifier = Modifier
) {
    var nextId by remember { mutableIntStateOf(4) }
    val students = remember {
        mutableStateListOf(
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
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var newStudentName by remember { mutableStateOf("") }
    var newStudentGrade by remember { mutableStateOf("") }
    var newStudentNote by remember { mutableStateOf("") }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            // 상단 헤더 + 학생 추가 버튼
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "학생별 수학 메타인지 분석",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "분석할 학생을 선택해 필기 풀이를 시작하세요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = {
                        newStudentName = ""
                        newStudentGrade = ""
                        newStudentNote = ""
                        showAddDialog = true
                    },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "학생 추가",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 학생 수 요약 라벨
            Text(
                text = "등록된 학생 (${students.size}명)",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            // 학생 목록
            if (students.isEmpty()) {
                EmptyInfoBanner(
                    message = "등록된 학생이 없습니다. 우측 상단의 '학생 추가' 버튼을 눌러 학생을 등록해 주세요."
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(
                        items = students,
                        key = { it.id }
                    ) { student ->
                        MathSectionCard(
                            modifier = Modifier.clickable { onProblemSelected(student) },
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 이름 첫 글자 아바타
                                Surface(
                                    modifier = Modifier.size(42.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = student.title.take(1).ifBlank { "학" },
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = student.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (student.difficulty.isNotBlank() || student.tag.isNotBlank()) {
                                            val subBadge = listOf(student.difficulty, student.tag)
                                                .filter { it.isNotBlank() }
                                                .joinToString(" · ")
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant
                                            ) {
                                                Text(
                                                    text = subBadge,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(
                                                        horizontal = 8.dp,
                                                        vertical = 3.dp
                                                    )
                                                )
                                            }
                                        }
                                    }

                                    if (student.description.isNotBlank()) {
                                        Text(
                                            text = student.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { students.remove(student) }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "${student.title} 삭제",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 학생 추가 다이얼로그
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = {
                Text(
                    text = "새 학생 추가",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newStudentName,
                        onValueChange = { newStudentName = it },
                        label = { Text("학생 이름 *") },
                        placeholder = { Text("예: 최유진") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newStudentGrade,
                        onValueChange = { newStudentGrade = it },
                        label = { Text("학년 / 반 (선택)") },
                        placeholder = { Text("예: 고2") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newStudentNote,
                        onValueChange = { newStudentNote = it },
                        label = { Text("과목 / 메모 (선택)") },
                        placeholder = { Text("예: 수1 삼각함수 집중") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmedName = newStudentName.trim()
                        if (trimmedName.isNotEmpty()) {
                            students.add(
                                MathProblem(
                                    id = nextId++,
                                    title = trimmedName,
                                    description = newStudentNote.trim(),
                                    difficulty = newStudentGrade.trim(),
                                    tag = ""
                                )
                            )
                            showAddDialog = false
                        }
                    },
                    enabled = newStudentName.trim().isNotEmpty()
                ) {
                    Text("추가")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("취소")
                }
            }
        )
    }
}