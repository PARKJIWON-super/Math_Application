package com.example.math_application.ui

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

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
    onClear: () -> Unit = {}
) {
    val points = remember { mutableStateListOf<StrokeData>() }
    val currentStroke = remember { mutableStateListOf<Offset>() }
    val activePointers = remember { mutableSetOf<Long>() }
    val strokeCounter = remember { mutableIntStateOf(0) }
    val lastTimestampMillis = remember { mutableStateOf(0L) }
    val pauseCount = remember { mutableIntStateOf(0) }
    val lastPauseMs = remember { mutableStateOf(0L) }
    val pauseRecords = remember { mutableStateListOf<PauseRecord>() }

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
                                pauseCount.intValue++
                                lastPauseMs.value = deltaMs
                                pauseRecords.add(
                                    PauseRecord(
                                        timestampMillis = now,
                                        pauseMs = deltaMs,
                                        strokeId = strokeCounter.intValue
                                    )
                                )
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

        Text(
            text = "Stroke=${points.size} Pause=${pauseCount.intValue} LastPauseMs=${lastPauseMs.value}",
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
            color = Color.Black
        )

        Button(
            onClick = {
                val maxPauseMs = pauseRecords.maxOfOrNull { it.pauseMs } ?: 0L
                Log.d(
                    "MathMetaPenLog",
                    "Summary totalPause=${pauseRecords.size} maxPauseMs=$maxPauseMs Time=${System.currentTimeMillis()}"
                )
                points.clear()
                currentStroke.clear()
                pauseRecords.clear()
                pauseCount.intValue = 0
                lastPauseMs.value = 0L
                onClear()
            },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Text(text = "Clear")
        }
    }
}