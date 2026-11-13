package com.example.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import kotlin.math.abs

/**
 * Pinch-to-zoom and zoomed-pan gesture modifier.
 * - Allows zooming OUT (< 1.0f) and zooming IN (> 1.0f).
 * - When scale <= 1.05f (including zoomed-out states!), 1-finger drags are NEVER consumed,
 *   letting LazyColumn scroll freely at full speed without ever locking or stalling.
 * - Calls [onGestureEnd] when all fingers are lifted so near-1.0x scales can snap cleanly.
 */
fun Modifier.smartZoomPanGestures(
    currentScale: () -> Float,
    onTransform: (zoomChange: Float, panChange: Offset) -> Unit,
    onGestureEnd: () -> Unit = {}
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var zoom = 1f
        var pan = Offset.Zero
        var pastTouchSlop = false
        var didTransform = false
        val touchSlop = viewConfiguration.touchSlop

        do {
            val event = awaitPointerEvent()
            val canceled = event.changes.any { it.isConsumed }
            if (!canceled) {
                val pointerCount = event.changes.count { it.pressed }
                val shouldIntercept = pointerCount > 1 || currentScale() > 1.05f

                if (shouldIntercept) {
                    val zoomChange = event.calculateZoom()
                    val panChange = event.calculatePan()

                    if (!pastTouchSlop) {
                        zoom *= zoomChange
                        pan += panChange
                        val centroidSize = event.calculateCentroidSize(useCurrent = false)
                        val zoomMotion = abs(1 - zoom) * centroidSize
                        val panMotion = pan.getDistance()
                        if (zoomMotion > touchSlop || panMotion > touchSlop || pointerCount > 1) {
                            pastTouchSlop = true
                        }
                    }

                    if (pastTouchSlop) {
                        if (zoomChange != 1f || panChange != Offset.Zero) {
                            didTransform = true
                            onTransform(zoomChange, panChange)
                        }
                        event.changes.forEach { change ->
                            if (change.positionChanged()) {
                                change.consume()
                            }
                        }
                    }
                } else {
                    zoom = 1f
                    pan = Offset.Zero
                    pastTouchSlop = false
                }
            }
        } while (event.changes.any { it.pressed })

        if (didTransform) {
            onGestureEnd()
        }
    }
}
