package com.airgest.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.DisplayMetrics
import android.view.accessibility.AccessibilityEvent
import android.view.WindowManager

class AirgestAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun tap() = stroke { path, width, height ->
        val x = width / 2f
        val y = height / 2f
        path.moveTo(x, y)
        path.lineTo(x + 1f, y + 1f)
    }

    fun swipe(direction: Direction) = stroke { path, width, height ->
        val centerX = width / 2f
        val centerY = height / 2f
        when (direction) {
            Direction.UP -> { path.moveTo(centerX, centerY + height * .18f); path.lineTo(centerX, centerY - height * .18f) }
            Direction.DOWN -> { path.moveTo(centerX, centerY - height * .18f); path.lineTo(centerX, centerY + height * .18f) }
            Direction.LEFT -> { path.moveTo(centerX + width * .18f, centerY); path.lineTo(centerX - width * .18f, centerY) }
            Direction.RIGHT -> { path.moveTo(centerX - width * .18f, centerY); path.lineTo(centerX + width * .18f, centerY) }
        }
    }

    private fun stroke(buildPath: (Path, Float, Float) -> Unit): Boolean {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        val path = Path().also { buildPath(it, metrics.widthPixels.toFloat(), metrics.heightPixels.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 250))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    enum class Direction { UP, DOWN, LEFT, RIGHT }

    companion object {
        @Volatile var instance: AirgestAccessibilityService? = null
            private set
    }
}
