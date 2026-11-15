package com.example.math_application.ui

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
    val strokeId: Int
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

@Composable
fun SimpleDrawingCanvas(
    modifier: Modifier = Modifier,
    clearSignal: Int = 0,
    onPauseDetected: (pauseMs: Long, strokeId: Int) -> Unit = { _, _ -> },
    onStrokeAdded: (Int) -> Unit = {}
) {
    val points = remember { mutableStateListOf<StrokeData>() }
    val currentStroke = remember { mutableStateListOf<Offset>() }
    val activePointers = remember { mutableSetOf<Long>() }
    val strokeCounter = remember { mutableIntStateOf(0) }
    val lastTimestampMillis = remember { mutableStateOf(0L) }

    LaunchedEffect(clearSignal) {
        if (clearSignal > 0) {
            points.clear()
            currentStroke.clear()
            activePointers.clear()
            strokeCounter.intValue = 0
            lastTimestampMillis.value = 0L
        }
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
                            val now = System.currentTimeMillis()
                            val prevTime = lastTimestampMillis.value
                            val deltaMs = if (prevTime != 0L) now - prevTime else 0L
                            lastTimestampMillis.value = now

                            if (deltaMs >= 1000L) {
                                logPause(deltaMs, strokeCounter.intValue)
                                onPauseDetected(deltaMs, strokeCounter.intValue)
                            }

                            for (change in event.changes) {
                                val pointerId = change.id.value
                                when {
                                    change.pressed && !activePointers.contains(pointerId) -> {
                                        strokeCounter.intValue++
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
                                            points.add(StrokeData(currentStroke.toList()))
                                            onStrokeAdded(points.size)
                                        }
                                        currentStroke.clear()
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            for (stroke in points) {
                var prev = stroke.points.first()
                for (point in stroke.points.drop(1)) {
                    drawLine(
                        start = prev,
                        end = point,
                        color = stroke.color,
                        strokeWidth = stroke.strokeWidth,
                        cap = StrokeCap.Round
                    )
                    prev = point
                }
            }
            if (currentStroke.size >= 2) {
                var prev = currentStroke.first()
                for (point in currentStroke.drop(1)) {
                    drawLine(
                        start = prev,
                        end = point,
                        color = Color.Black,
                        strokeWidth = 4f,
                        cap = StrokeCap.Round
                    )
                    prev = point
                }
            }
        }
    }
}