package info.nightscout.androidaps.plugins.general.overview.graphExtensions

/**
 * GraphView
 * Copyright (C) 2014  Jonas Gehring
 *
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License,
 * with the "Linking Exception", which can be found at the license.txt
 * file in this program.
 *
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 *
 * You should have received a copy of the GNU General Public License
 * with the "Linking Exception" along with this program; if not,
 * write to the author Jonas Gehring <g.jjoe64></g.jjoe64>@gmail.com>.
 *
 *
 * Added by mike
 */

/**
 * Added by mike
 */

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Paint.*
import android.graphics.Path
import android.graphics.Point
import android.graphics.Rect
import android.graphics.Typeface

import com.jjoe64.graphview.GraphView
import com.jjoe64.graphview.series.BaseSeries

import info.nightscout.androidaps.MainApp
import kotlin.math.min

// Added by Rumen for scalable text

/**
 * Series that plots the data as points.
 * The points can be different shapes or a
 * complete custom drawing.
 *
 * @author jjoe64
 */
class PointsWithLabelGraphSeries<E : DataPointWithLabelInterface> : BaseSeries<E> {
    // Default spSize
    private var spSize = 14
    // Convert the sp to pixels
    private var context = MainApp.instance().applicationContext
    private var scaledTextSize = spSize * context.resources.displayMetrics.scaledDensity
    private var scaledPxSize = context.resources.displayMetrics.scaledDensity * 3f

    /**
     * internal paint object
     */
    private var mPaint: Paint? = null

    /**
     * choose a predefined shape to render for
     * each data point.
     * You can also render a custom drawing via [com.jjoe64.graphview.series.PointsGraphSeries.CustomShape]
     */
    enum class Shape {
        BG,
        PREDICTION,
        TRIANGLE,
        RECTANGLE,
        BOLUS,
        SMB,
        EXTENDEDBOLUS,
        PROFILE,
        MBG,
        BGCHECK,
        ANNOUNCEMENT,
        OPENAPSOFFLINE,
        EXERCISE,
        GENERAL,
        GENERALWITHDURATION,
        COBFAILOVER,
        IOBPREDICTION
    }

    /**
     * creates the series without data
     */
    constructor() {
        init()
    }

    /**
     * creates the series with data
     *
     * @param data datapoints
     */
    constructor(data: Array<E>) : super(data) {
        init()
    }

    /**
     * inits the internal objects
     * set the defaults
     */
    private fun init() {
        mPaint = Paint()
        mPaint!!.strokeCap = Cap.ROUND
    }

    /**
     * Helper class holds
     */
    class Metrics(
            val graphWidth: Float,
            val graphHeight: Float,
            val minY: Double,
            val maxY: Double,
            val minX: Double,
            val maxX: Double,
            val diffY: Double = maxY - minY,
            val diffX: Double = maxX - minX,
            val scaleX: Float = (graphWidth / diffX).toFloat(),
            val graphLeft: Float,
            val graphTop: Float
    )

    /**
     * plot the data to the viewport
     *
     * @param gw graphview
     * @param canvas canvas to draw on
     * @param isSecondScale whether it is the second scale
     */
    override fun draw(gw: GraphView, canvas: Canvas, isSecondScale: Boolean) {
        resetDataPoints()

        val metrics = Metrics(
                minX = gw.viewport.getMinX(false),
                maxX = gw.viewport.getMaxX(false),
                minY = if (isSecondScale) gw.secondScale.minY else gw.viewport.getMinY(false),
                maxY = if (isSecondScale) gw.secondScale.maxY else gw.viewport.getMaxY(false),
                graphHeight = gw.graphContentHeight.toFloat(),
                graphWidth = gw.graphContentWidth.toFloat(),
                graphLeft = gw.graphContentLeft.toFloat(),
                graphTop = gw.graphContentTop.toFloat()
        )

        getValues(metrics.minX, metrics.maxX).forEach {
            drawValue(it, metrics, canvas, mPaint ?: return)
        }

    }

    private fun drawValue(value: E, m: Metrics, canvas: Canvas, paint: Paint) {
        val valY = value.y - m.minY
        val ratY = valY / m.diffY
        val y = m.graphHeight * ratY

        val valX = value.x - m.minX
        val ratX = valX / m.diffX
        var x = m.graphWidth * ratX

        // overdraw
        var overdraw = false
        if (x > m.graphWidth) { // end right
            overdraw = true
        }

        if (y < 0) { // end bottom
            overdraw = true
        }
        if (y > m.graphHeight) { // end top
            overdraw = true
        }

        val duration = value.duration
        val endWithDuration = (x + (duration * m.scaleX).toDouble() + m.graphLeft.toDouble() + 1.0).toFloat()
        // cut off to graph start if needed
        if (x < 0 && endWithDuration > 0) {
            x = 0.0
        }

        /* Fix a bug that continue to show the DOT after Y axis */
        if (x < 0) {
            overdraw = true
        }

        val endX = x.toFloat() + (m.graphLeft + 1)
        val endY = (m.graphTop - y).toFloat() + m.graphHeight
        registerDataPoint(endX, endY, value)

        var xpluslength = 0f
        if (duration > 0) {
            xpluslength = min(endWithDuration, m.graphLeft + m.graphWidth)
        }

        if (overdraw) return


        paint.color = value.color
        when (value.shape) {
            Shape.BG, Shape.COBFAILOVER, Shape.IOBPREDICTION -> {
                paint.style = Style.FILL
                paint.strokeWidth = 0f
                if (value.shape == Shape.IOBPREDICTION) color = value.color
                canvas.drawCircle(endX, endY, value.size * scaledPxSize, paint)
            }
            Shape.PREDICTION -> {
                paint.color = value.color
                paint.style = Style.FILL
                paint.strokeWidth = 0f
                canvas.drawCircle(endX, endY, scaledPxSize, paint)
                paint.style = Style.FILL
                paint.strokeWidth = 0f
                canvas.drawCircle(endX, endY, scaledPxSize / 3, paint)
            }
            Shape.RECTANGLE -> {
                canvas.drawRect(endX - scaledPxSize, endY - scaledPxSize, endX + scaledPxSize, endY + scaledPxSize, paint)
            }
            Shape.TRIANGLE -> {
                paint.strokeWidth = 0f
                val points = arrayOf(
                        Point(endX.toInt(), (endY - scaledPxSize).toInt()),
                        Point((endX + scaledPxSize).toInt(), (endY + scaledPxSize * 0.67).toInt()),
                        Point((endX - scaledPxSize).toInt(), (endY + scaledPxSize * 0.67).toInt())
                )
                drawArrows(points, canvas, paint)
            }
            Shape.BOLUS -> {
                paint.strokeWidth = 0f
                paint.style = Style.FILL_AND_STROKE
                val points = arrayOf(
                        Point(endX.toInt(), (endY - scaledPxSize).toInt()),
                        Point((endX + scaledPxSize).toInt(), (endY + scaledPxSize * 0.67).toInt()),
                        Point((endX - scaledPxSize).toInt(), (endY + scaledPxSize * 0.67).toInt()))
                drawArrows(points, canvas, paint)

                if (value.label != null) {
                    drawLabel45(endX, endY, value, canvas, paint)
                }
            }
            Shape.SMB -> {
                paint.strokeWidth = 2f
                val size = value.size * scaledPxSize
                val points = arrayOf(
                        Point(endX.toInt(), (endY - size).toInt()),
                        Point((endX + size).toInt(), (endY + size * 0.67).toInt()),
                        Point((endX - size).toInt(), (endY + size * 0.67).toInt())
                )
                paint.style = Style.FILL_AND_STROKE
                drawArrows(points, canvas, paint)
            }
            Shape.EXTENDEDBOLUS -> {
                if (value.label == null) return

                val bounds = Rect(endX.toInt(), endY.toInt() + 3, xpluslength.toInt(), endY.toInt() + 8)
                paint.style = Style.FILL_AND_STROKE
                canvas.drawRect(bounds, paint)
                paint.textSize = scaledTextSize
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                paint.isFakeBoldText = true
                canvas.drawText(value.label, endX, endY, paint)

            }
            Shape.PROFILE -> {
                if (value.label == null) return

                paint.strokeWidth = 0f
                paint.textSize = (scaledTextSize * 1.2).toFloat()
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                val bounds = Rect()
                paint.getTextBounds(value.label, 0, value.label.length, bounds)
                paint.style = Style.STROKE
                val px = endX + bounds.height() / 2
                val py = (m.graphHeight * ratY + bounds.width().toDouble() + 10.0).toFloat()
                canvas.save()
                canvas.rotate(-90f, px, py)
                canvas.drawText(value.label, px, py, paint)
                canvas.drawRect(px - 3, bounds.top + py - 3, bounds.right.toFloat() + px + 3f, bounds.bottom.toFloat() + py + 3f, paint)
                canvas.restore()

            }
            Shape.MBG -> {
                paint.style = Style.STROKE
                paint.strokeWidth = 5f
                canvas.drawCircle(endX, endY, scaledPxSize, paint)
            }
            Shape.BGCHECK -> {
                paint.style = Style.FILL_AND_STROKE
                paint.strokeWidth = 0f
                canvas.drawCircle(endX, endY, scaledPxSize, paint)
                if (value.label != null) {
                    drawLabel45(endX, endY, value, canvas, paint)
                }
            }
            Shape.ANNOUNCEMENT -> {
                paint.style = Style.FILL_AND_STROKE
                paint.strokeWidth = 0f
                canvas.drawCircle(endX, endY, scaledPxSize, paint)
                if (value.label != null) {
                    drawLabel45(endX, endY, value, canvas, paint)
                }
            }
            Shape.GENERAL -> {
                paint.style = Style.FILL_AND_STROKE
                paint.strokeWidth = 0f
                canvas.drawCircle(endX, endY, scaledPxSize, paint)
                if (value.label != null) {
                    drawLabel45(endX, endY, value, canvas, paint)
                }
            }
            Shape.EXERCISE -> {
                if (value.label == null) return

                paint.strokeWidth = 0f
                paint.textSize = (scaledTextSize * 1.2).toFloat()
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                val bounds = Rect()
                paint.getTextBounds(value.label, 0, value.label.length, bounds)
                paint.style = Style.STROKE
                val py = m.graphTop + 20
                canvas.drawText(value.label, endX, py, paint)
                paint.strokeWidth = 5f
                canvas.drawRect(endX - 3, bounds.top + py - 3, xpluslength + 3, bounds.bottom.toFloat() + py + 3f, paint)
            }
            Shape.OPENAPSOFFLINE -> {
                if (value.duration == 0L || value.label == null) return

                paint.strokeWidth = 0f
                paint.style = Style.FILL_AND_STROKE
                paint.strokeWidth = 5f
                canvas.drawRect(endX - 3, m.graphTop, xpluslength + 3, m.graphTop + m.graphHeight, paint)

            }
            Shape.GENERALWITHDURATION -> {
                if (value.label == null) return

                paint.strokeWidth = 0f
                paint.textSize = (scaledTextSize * 1.5).toFloat()
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                val bounds = Rect()
                paint.getTextBounds(value.label, 0, value.label.length, bounds)
                paint.style = Style.STROKE
                val py = m.graphTop + 80
                canvas.drawText(value.label, endX, py, paint)
                paint.strokeWidth = 5f
                canvas.drawRect(endX - 3, bounds.top + py - 3, xpluslength + 3, bounds.bottom.toFloat() + py + 3f, paint)

            }
            null -> {
                //nothing
            }
        }
    }


    private fun drawLabel45(endX: Float, endY: Float, value: E, canvas: Canvas, paint: Paint) {
        if (value.label.startsWith("~")) {
            val py = endY + scaledPxSize
            canvas.save()
            canvas.rotate(-45f, endX, py)
            paint.textSize = (scaledTextSize * 0.8).toFloat()
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.isFakeBoldText = true
            paint.textAlign = Align.RIGHT
            canvas.drawText(value.label.substring(1), endX - scaledPxSize, py, paint)
            paint.textAlign = Align.LEFT
            canvas.restore()
        } else {
            val py = endY - scaledPxSize
            canvas.save()
            canvas.rotate(-45f, endX, py)
            paint.textSize = (scaledTextSize * 0.8).toFloat()
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.isFakeBoldText = true
            canvas.drawText(value.label, endX + scaledPxSize, py, paint)
            canvas.restore()
        }
    }

    companion object {
        /**
         * helper to render triangle
         *
         * @param point array with 3 coordinates
         * @param canvas canvas to draw on
         * @param paint paint object
         */
        private fun drawArrows(point: Array<Point>, canvas: Canvas, paint: Paint) {
            val points = FloatArray(8)
            points[0] = point[0].x.toFloat()
            points[1] = point[0].y.toFloat()
            points[2] = point[1].x.toFloat()
            points[3] = point[1].y.toFloat()
            points[4] = point[2].x.toFloat()
            points[5] = point[2].y.toFloat()
            points[6] = point[0].x.toFloat()
            points[7] = point[0].y.toFloat()

            canvas.save()
            canvas.drawVertices(Canvas.VertexMode.TRIANGLES, 8, points, 0, null, 0, null, 0, null, 0, 0, paint)
            val path = Path()
            path.moveTo(point[0].x.toFloat(), point[0].y.toFloat())
            path.lineTo(point[1].x.toFloat(), point[1].y.toFloat())
            path.lineTo(point[2].x.toFloat(), point[2].y.toFloat())
            canvas.drawPath(path, paint)
            canvas.restore()
        }
    }
}