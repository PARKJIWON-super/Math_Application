package com.example.math_application.ui

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
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

@Composable
fun SimpleDrawingCanvas(
    modifier: Modifier = Modifier,
    onClear: () -> Unit = {}
) {
    val points = remember { mutableStateListOf<StrokeData>() }
    val currentStroke = remember { mutableStateListOf<Offset>() }
    val activePointers = remember { mutableSetOf<Long>() }

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
                            for (change in event.changes) {
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

        Button(
            onClick = {
                points.clear()
                currentStroke.clear()
                onClear()
            },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Text(text = "Clear")
        }
    }
}