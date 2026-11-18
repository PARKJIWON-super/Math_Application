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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.math_application.MathProblem
import com.example.math_application.ui.components.EmptyInfoBanner
import com.example.math_application.ui.components.StrokeWidthChipGroup

data class PageMetrics(
    val pageNumber: Int, // 1부터 시작하는 페이지 번호 (pdfCurrentPageIndex + 1)
    var strokeCount: Int = 0,
    var pauseCount: Int = 0,
    val pauseRecords: MutableList<PauseRecord> = mutableListOf()
) {
    val maxPauseMs: Long
        get() = pauseRecords.maxOfOrNull { it.pauseMs } ?: 0L
}

@Composable
fun MathWorkspaceScreen(
    currentProblem: MathProblem?,
    onBack: () -> Unit,
    onSummaryClicked: () -> Unit,
    onSummaryData: (Int, Int, Long, List<PauseRecord>) -> Unit = { _, _, _, _ -> },
    onSummaryPageMetrics: (List<PageMetrics>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var strokeCount by remember { mutableIntStateOf(0) }
    var pauseCount by remember { mutableIntStateOf(0) }
    var maxPauseMs by remember { mutableStateOf(0L) }
    val pauseRecords = remember { mutableStateListOf<PauseRecord>() }

    // PDF 페이지 번호(1-based)별 메트릭스 저장소
    val pageMetricsMap = remember { mutableStateMapOf<Int, PageMetrics>() }

    // PDF 페이지 번호(1-based)별 잉크(완성된 획) 저장소
    val pageStrokesMap = remember { mutableStateMapOf<Int, List<StrokeData>>() }

    // PDF 관련 state
    var pdfUri by remember { mutableStateOf<Uri?>(null) }
    var pdfBitmap by remember { mutableStateOf<AndroidBitmap?>(null) }
    var pdfError by remember { mutableStateOf<String?>(null) }

    // PDF 페이지 상태
    var pdfPageCount by remember { mutableIntStateOf(0) }
    var pdfCurrentPageIndex by remember { mutableIntStateOf(0) }

    val currentPageNumber = pdfCurrentPageIndex + 1

    // 펜 두께 상태
    var selectedStrokeWidth by remember { mutableStateOf(4f) }

    // 페이지 이동 시 해당 페이지 항목을 보장하고 저장된 기록으로 화면 상태 동기화
    LaunchedEffect(pdfUri, pdfCurrentPageIndex) {
        val pageNum = pdfCurrentPageIndex + 1
        if (pdfUri != null && !pageMetricsMap.containsKey(pageNum)) {
            pageMetricsMap[pageNum] = PageMetrics(pageNumber = pageNum)
        }
        val saved = pageMetricsMap[pageNum]
        strokeCount = saved?.strokeCount ?: 0
        pauseCount = saved?.pauseCount ?: 0
        maxPauseMs = saved?.maxPauseMs ?: 0L
        pauseRecords.clear()
        if (saved != null) {
            pauseRecords.addAll(saved.pauseRecords)
        }
        Log.d("PageDebug", "LaunchedEffect -> index=$pdfCurrentPageIndex, pageNum=$pageNum")
    }

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
                            pageMetricsMap.clear()
                            pageStrokesMap.clear()
                            strokeCount = 0
                            pauseCount = 0
                            maxPauseMs = 0L
                            pauseRecords.clear()
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

    val navigateToSummary = {
        val sortedMetrics = pageMetricsMap.values
            .sortedBy { it.pageNumber }
            .map { pm ->
                pm.copy(pauseRecords = pm.pauseRecords.toMutableList())
            }
        val allRecords = sortedMetrics.flatMap { it.pauseRecords }
        val totalStrokes = sortedMetrics.sumOf { it.strokeCount }
        val totalPauses = sortedMetrics.sumOf { it.pauseCount }
        val maxPause = allRecords.maxOfOrNull { it.pauseMs } ?: 0L

        onSummaryData(
            totalStrokes,
            totalPauses,
            maxPause,
            allRecords
        )
        onSummaryPageMetrics(sortedMetrics)
        onSummaryClicked()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // 상단 한 줄 통합 컨트롤 바
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "뒤로 가기"
                        )
                    }

                    Text(
                        text = currentProblem?.title ?: "필기 풀이",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    // 페이지 이동 (< P1/20 >)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IconButton(
                            onClick = {
                                if (pdfCurrentPageIndex > 0) {
                                    pdfCurrentPageIndex--
                                }
                            },
                            enabled = pdfCurrentPageIndex > 0 && pdfPageCount > 0,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowLeft,
                                contentDescription = "이전 페이지"
                            )
                        }
                        Text(
                            text = if (pdfPageCount > 0) "P$currentPageNumber/$pdfPageCount" else "P$currentPageNumber",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(
                            onClick = {
                                if (pdfCurrentPageIndex < pdfPageCount - 1) {
                                    pdfCurrentPageIndex++
                                }
                            },
                            enabled = pdfPageCount > 0 && pdfCurrentPageIndex < pdfPageCount - 1,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowRight,
                                contentDescription = "다음 페이지"
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // 펜 두께 선택 (2px 4px 6px)
                    StrokeWidthChipGroup(
                        selectedWidth = selectedStrokeWidth,
                        onSelectWidth = { selectedStrokeWidth = it }
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    // 우측 액션 (PDF 변경 / 지우기 / 요약)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        TextButton(
                            onClick = {
                                pdfError = null
                                launcherPdf.launch(arrayOf("application/pdf"))
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (pdfUri != null) "PDF변경" else "PDF열기",
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        TextButton(
                            onClick = {
                                pageStrokesMap.remove(currentPageNumber)
                                pageMetricsMap.remove(currentPageNumber)
                                pauseRecords.clear()
                                strokeCount = 0
                                pauseCount = 0
                                maxPauseMs = 0L
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "지우기",
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        Button(
                            onClick = navigateToSummary,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.padding(end = 2.dp)
                        ) {
                            Text(
                                text = "요약",
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // PDF 로드 에러 표시
            pdfError?.let { error ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .background(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // 중앙 필기 캔버스 영역
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (pdfBitmap != null) {
                        Image(
                            bitmap = pdfBitmap!!.asImageBitmap(),
                            contentDescription = "PDF 배경",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .zIndex(-1f)
                        )
                    } else if (currentProblem == null) {
                        EmptyInfoBanner(
                            message = "상단의 'PDF열기'를 눌러 문제지 PDF를 불러오거나, 캔버스에 바로 풀이해 보세요.",
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(12.dp)
                        )
                    }

                    SimpleDrawingCanvas(
                        modifier = Modifier.fillMaxSize(),
                        pageKey = currentPageNumber,
                        strokes = pageStrokesMap[currentPageNumber] ?: emptyList(),
                        strokeWidth = selectedStrokeWidth,
                        onPauseDetected = { pauseMs, strokeId ->
                            val pageNum = currentPageNumber
                            Log.d("PageDebug", "onPauseDetected -> pageNum=$pageNum, pauseMs=$pauseMs")
                            val existing = pageMetricsMap[pageNum] ?: PageMetrics(pageNumber = pageNum)
                            val updatedRecords = existing.pauseRecords.toMutableList().apply {
                                add(
                                    PauseRecord(
                                        timestampMillis = System.currentTimeMillis(),
                                        pauseMs = pauseMs,
                                        strokeId = strokeId,
                                        pageNumber = pageNum
                                    )
                                )
                            }
                            val updated = PageMetrics(
                                pageNumber = pageNum,
                                strokeCount = existing.strokeCount,
                                pauseCount = existing.pauseCount + 1,
                                pauseRecords = updatedRecords
                            )
                            pageMetricsMap[pageNum] = updated

                            pauseCount = updated.pauseCount
                            maxPauseMs = updated.maxPauseMs
                            pauseRecords.clear()
                            pauseRecords.addAll(updated.pauseRecords)
                        },
                        onStrokeFinished = { stroke ->
                            val pageNum = currentPageNumber
                            Log.d("PageDebug", "onStrokeFinished -> pageNum=$pageNum")

                            val newStrokes = (pageStrokesMap[pageNum] ?: emptyList()) + stroke
                            pageStrokesMap[pageNum] = newStrokes

                            val existing = pageMetricsMap[pageNum] ?: PageMetrics(pageNumber = pageNum)
                            pageMetricsMap[pageNum] = PageMetrics(
                                pageNumber = pageNum,
                                strokeCount = newStrokes.size,
                                pauseCount = existing.pauseCount,
                                pauseRecords = existing.pauseRecords.toMutableList()
                            )
                            strokeCount = newStrokes.size
                        }
                    )
                }
            }
        }
    }
}