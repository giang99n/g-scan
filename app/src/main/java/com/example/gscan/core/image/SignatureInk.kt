package com.example.gscan.core.image

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.example.gscan.feature.editor.domain.Ink
import com.example.gscan.feature.editor.domain.InkPoint
import com.example.gscan.feature.editor.domain.rotated
import com.example.gscan.feature.editor.domain.validateInk
import org.json.JSONArray

object SignatureInk {
    fun encode(ink: Ink): String {
        validateInk(ink)
        return JSONArray().apply {
            ink.forEach { stroke -> put(JSONArray().apply {
                stroke.forEach { point -> put(JSONArray().put(point.x.toDouble()).put(point.y.toDouble())) }
            }) }
        }.toString()
    }

    fun decode(value: String): Ink {
        val array = JSONArray(value)
        require(array.length() <= 100)
        var count = 0
        return List(array.length()) { i ->
            val stroke = array.getJSONArray(i)
            count += stroke.length()
            require(count <= 8000)
            List(stroke.length()) { j ->
                val point = stroke.getJSONArray(j)
                InkPoint(point.getDouble(0).toFloat(), point.getDouble(1).toFloat())
            }
        }.also(::validateInk)
    }

    fun draw(canvas: Canvas, ink: Ink, width: Float, height: Float, rotation: Int = 0) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = minOf(width, height) * 0.0025f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        ink.forEach { stroke ->
            val points = stroke.map { it.rotated(rotation) }
            if (points.size == 1) {
                canvas.drawPoint(points[0].x * width, points[0].y * height, paint)
            } else if (points.isNotEmpty()) {
                val path = Path().apply {
                    moveTo(points[0].x * width, points[0].y * height)
                    points.drop(1).forEach { lineTo(it.x * width, it.y * height) }
                }
                canvas.drawPath(path, paint)
            }
        }
    }
}
