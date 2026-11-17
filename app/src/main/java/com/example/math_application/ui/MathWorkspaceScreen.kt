package com.example.math_application.ui

import android.graphics.Bitmap as AndroidBitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.math_application.MathProblem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MathWorkspaceScreen(
    currentProblem: MathProblem?,
    onBack: () -> Unit,
    onSummaryClicked: () -> Unit,
    onSummaryData: (Int, Int, Long, List<PauseRecord>) -> Unit = { _, _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var strokeCount by remember { mutableIntStateOf(0) }
    var pauseCount by remember { mutableIntStateOf(0) }
    var lastPauseMs by remember { mutableStateOf(0L) }
    var clearSignal by remember { mutableIntStateOf(0) }
    val pauseRecords = remember { mutableStateListOf<PauseRecord>() }

    // PDF 관련 state
    var pdfUri by remember { mutableStateOf<Uri?>(null) }
    var pdfBitmap by remember { mutableStateOf<AndroidBitmap?>(null) }
    var pdfError by remember { mutableStateOf<String?>(null) }

    // PDF 페이지 상태
    var pdfPageCount by remember { mutableIntStateOf(0) }
    var pdfCurrentPageIndex by remember { mutableIntStateOf(0) }

    // 펜 두께 상태
    var selectedStrokeWidth by remember { mutableStateOf(4f) }

    // PDF 선택 launcher
    val launcherPdf = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                pdfError = null
                try {
                    val parcelFd = context.contentResolver.openFileDescriptor(uri, "r")
                        ?: throw IllegalStateException("파일 디스크립터를 열 수 없습니다.")
                    parcelFd.use { pfd ->
                        PdfRenderer(pfd).use { renderer ->
                            if (renderer.pageCount <= 0) {
                                throw IllegalStateException("PDF 페이지가 없습니다.")
                            }
                            pdfPageCount = renderer.pageCount
                            pdfCurrentPageIndex = 0
                            pdfUri = uri
                        }
                    }
                } catch (e: Exception) {
                    pdfError = "PDF 로드 실패: ${e.message}"
                    Log.e("MathWorkspaceScreen", "PDF 로드 에러", e)
                }
            }
        }
    )

    // PDF 페이지 전환을 위한 DisposableEffect (리소스 안전 정리)
    DisposableEffect(pdfUri, pdfCurrentPageIndex) {
        val targetUri = pdfUri
        var currentParcelFd: ParcelFileDescriptor? = null
        var currentRenderer: PdfRenderer? = null
        var currentPage: PdfRenderer.Page? = null

        if (targetUri != null) {
            try {
                currentParcelFd = context.contentResolver.openFileDescriptor(targetUri, "r")
                    ?: throw IllegalStateException("파일 디스크립터를 열 수 없습니다.")
                currentRenderer = PdfRenderer(currentParcelFd)
                if (pdfCurrentPageIndex in 0 until currentRenderer.pageCount) {
                    currentPage = currentRenderer.openPage(pdfCurrentPageIndex)
                    val bitmap = AndroidBitmap.createBitmap(
                        currentPage.width,
                        currentPage.height,
                        AndroidBitmap.Config.ARGB_8888
                    )
                    currentPage.render(
                        bitmap,
                        null,
                        null,
                        PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                    )
                    pdfBitmap = bitmap
                }
            } catch (e: Exception) {
                pdfError = "PDF 페이지 렌더링 실패: ${e.message}"
                Log.e("MathWorkspaceScreen", "PDF 페이지 렌더링 에러", e)
            }
        }

        onDispose {
            currentPage?.close()
            currentRenderer?.close()
            currentParcelFd?.close()
        }
    }

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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Stroke=$strokeCount  Pause=$pauseCount  LastPauseMs=$lastPauseMs",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Button(onClick = {
                            onSummaryData(
                                strokeCount,
                                pauseCount,
                                lastPauseMs,
                                pauseRecords.toList()
                            )
                            onSummaryClicked()
                        }) {
                            Text(text = "요약")
                        }
                        Button(
                            onClick = {
                                pdfError = null
                                launcherPdf.launch(arrayOf("application/pdf"))
                            }
                        ) {
                            Text(text = if (pdfUri != null) "PDF 다시 선택" else "PDF 선택")
                        }
                        // 페이지 이동 버튼
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (pdfCurrentPageIndex > 0) {
                                        pdfCurrentPageIndex--
                                    }
                                },
                                enabled = pdfCurrentPageIndex > 0 && pdfPageCount > 0
                            ) {
                                Text(text = "◀")
                            }
                            Text(
                                text = if (pdfPageCount > 0) {
                                    "${pdfCurrentPageIndex + 1} / $pdfPageCount"
                                } else {
                                    "0 / 0"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Button(
                                onClick = {
                                    if (pdfCurrentPageIndex < pdfPageCount - 1) {
                                        pdfCurrentPageIndex++
                                    }
                                },
                                enabled = pdfPageCount > 0 && pdfCurrentPageIndex < pdfPageCount - 1
                            ) {
                                Text(text = "▶")
                            }
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
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
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

            // PDF 로드 에러 표시
            pdfError?.let { error ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(12.dp)
                ) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // 중앙 필기 캔버스 영역 (PDF 배경 위에 SimpleDrawingCanvas 오버레이)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                pdfBitmap?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "PDF 배경",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(-1f)
                    )
                }

                SimpleDrawingCanvas(
                    modifier = Modifier.fillMaxSize(),
                    clearSignal = clearSignal,
                    strokeWidth = selectedStrokeWidth,
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
            }

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
                Button(
                    onClick = { selectedStrokeWidth = 2f },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedStrokeWidth == 2f) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        contentColor = if (selectedStrokeWidth == 2f) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                ) { Text(text = "2px") }
                Button(
                    onClick = { selectedStrokeWidth = 4f },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedStrokeWidth == 4f) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        contentColor = if (selectedStrokeWidth == 4f) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                ) { Text(text = "4px") }
                Button(
                    onClick = { selectedStrokeWidth = 6f },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedStrokeWidth == 6f) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        contentColor = if (selectedStrokeWidth == 6f) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                ) { Text(text = "6px") }
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = {
                        pauseRecords.clear()
                        strokeCount = 0
                        pauseCount = 0
                        lastPauseMs = 0L
                        clearSignal++
                        pdfBitmap = null
                        pdfUri = null
                        pdfError = null
                        pdfPageCount = 0
                        pdfCurrentPageIndex = 0
                    }
                ) {
                    Text(text = "Clear")
                }
            }
        }
    }
}