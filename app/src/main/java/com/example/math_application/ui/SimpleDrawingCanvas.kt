package com.example.math_application.ui

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput

data class StrokeData(
    val points: List<Offset>,
    val color: Color = Color.Black,
    val strokeWidth: Float = 4f
)

data class PenEvent(
    val strokeId: Int,
    val x: Float,
    val y: Float,
    val timestampMillis: Long,
    val deltaMs: Long
)

data class PauseRecord(
    val timestampMillis: Long,
    val pauseMs: Long,
    val strokeId: Int,      // 해당 페이지에서 직전까지 완성된 획 번호 (0이면 첫 획 이전)
    val pageNumber: Int = 0 // 1부터 시작하는 PDF 페이지 번호
)

private fun logPenEvent(eventType: String, pointerType: PointerType, x: Float, y: Float) {
    val typeStr = when (pointerType) {
        PointerType.Stylus -> "Stylus"
        PointerType.Touch -> "Touch"
        PointerType.Mouse -> "Mouse"
        else -> "Unknown"
    }
    Log.d(
        "MathMetaPenLog",
        "Event=$eventType Type=$typeStr X=${x.toInt()} Y=${y.toInt()} Time=${System.currentTimeMillis()}"
    )
}

private fun logPause(pauseMs: Long, strokeId: Int) {
    Log.d(
        "MathMetaPenLog",
        "Pause pauseMs=$pauseMs StrokeId=$strokeId Time=${System.currentTimeMillis()}"
    )
}

/** 점 목록을 하나의 Path로 만들어 한 번에 그린다. (획마다 drawLine을 반복하지 않음) */
private fun DrawScope.drawInk(points: List<Offset>, color: Color, width: Float) {
    if (points.size < 2) return
    val path = Path().apply {
        moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) {
            lineTo(points[i].x, points[i].y)
        }
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

/**
 * 필기 캔버스.
 *  - strokes         : 현재 페이지의 완성된 획 목록 (그리기용)
 *  - pageKey         : 페이지 식별자. 바뀌면 진행 중이던 획/멈춤 타이머를 초기화한다.
 *  - stylusOnly      : true면 스타일러스(펜) 입력만 인식하고 손가락(Touch)은 무시한다.
 *  - onStrokeFinished: 획이 하나 끝날 때마다 호출. 저장은 호출한 쪽에서 한다.
 *  - onPauseDetected : strokeId는 "이 페이지에서 직전까지 완성된 획 번호"(0이면 첫 획 이전)
 */
@Composable
fun SimpleDrawingCanvas(
    modifier: Modifier = Modifier,
    pageKey: Int,
    strokes: List<StrokeData>,
    strokeWidth: Float = 4f,
    stylusOnly: Boolean = true,
    onStrokeFinished: (StrokeData) -> Unit = {},
    onPauseDetected: (pauseMs: Long, strokeId: Int) -> Unit = { _, _ -> }
) {
    val currentStroke = remember { mutableStateListOf<Offset>() }
    val activePointers = remember { mutableSetOf<Long>() }
    val lastTimestampMillis = remember { mutableStateOf(0L) }

    val latestStrokes = rememberUpdatedState(strokes)
    val latestStrokeWidth = rememberUpdatedState(strokeWidth)
    val latestStylusOnly = rememberUpdatedState(stylusOnly)
    val latestOnPauseDetected = rememberUpdatedState(onPauseDetected)
    val latestOnStrokeFinished = rememberUpdatedState(onStrokeFinished)

    LaunchedEffect(pageKey) {
        currentStroke.clear()
        activePointers.clear()
        lastTimestampMillis.value = 0L
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()

                            val validChanges = if (latestStylusOnly.value) {
                                event.changes.filter { it.type == PointerType.Stylus }
                            } else {
                                event.changes
                            }
                            if (validChanges.isEmpty()) continue

                            val now = System.currentTimeMillis()
                            val prevTime = lastTimestampMillis.value
                            val deltaMs = if (prevTime != 0L) now - prevTime else 0L
                            lastTimestampMillis.value = now

                            if (deltaMs >= 1000L) {
                                val strokeId = latestStrokes.value.size
                                logPause(deltaMs, strokeId)
                                latestOnPauseDetected.value(deltaMs, strokeId)
                            }

                            for (change in validChanges) {
                                val pointerId = change.id.value
                                when {
                                    change.pressed && !activePointers.contains(pointerId) -> {
                                        activePointers.add(pointerId)
                                        logPenEvent(
                                            "Down",
                                            change.type,
                                            change.position.x,
                                            change.position.y
                                        )
                                        currentStroke.clear()
                                        currentStroke.add(change.position)
                                    }
                                    change.pressed && activePointers.contains(pointerId) -> {
                                        logPenEvent(
                                            "Move",
                                            change.type,
                                            change.position.x,
                                            change.position.y
                                        )
                                        currentStroke.add(change.position)
                                    }
                                    !change.pressed && activePointers.contains(pointerId) -> {
                                        activePointers.remove(pointerId)
                                        logPenEvent(
                                            "Up",
                                            change.type,
                                            change.position.x,
                                            change.position.y
                                        )
                                        if (currentStroke.size >= 2) {
                                            latestOnStrokeFinished.value(
                                                StrokeData(
                                                    points = currentStroke.toList(),
                                                    strokeWidth = latestStrokeWidth.value
                                                )
                                            )
                                        }
                                        currentStroke.clear()
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            for (stroke in strokes) {
                drawInk(stroke.points, stroke.color, stroke.strokeWidth)
            }
            drawInk(currentStroke, Color.Black, strokeWidth)
        }
    }
}