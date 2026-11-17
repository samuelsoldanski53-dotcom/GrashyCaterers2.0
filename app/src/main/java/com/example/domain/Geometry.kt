package com.example.domain

import com.example.model.LetterheadElement
import com.example.model.PageSettings
import com.example.model.SnapGuide
import kotlin.math.abs
import kotlin.math.roundToInt

object Geometry {
    const val A4_WIDTH_MM = 210f
    const val A4_HEIGHT_MM = 297f
    const val PT_TO_MM = 25.4f / 72f

    fun roundMm(value: Float, step: Float = 0.5f): Float {
        return (value / step).roundToInt() * step
    }

    fun clamp(value: Float, min: Float, max: Float): Float {
        return value.coerceIn(min, max)
    }

    fun snapValueToGrid(value: Float, gridSizeMm: Float): Float {
        if (gridSizeMm <= 0f) return value
        return (value / gridSizeMm).roundToInt() * gridSizeMm
    }

    data class SnapResult(
        val x: Float,
        val y: Float,
        val guides: List<SnapGuide>
    )

    fun calculateSnap(
        movingId: String,
        movingX: Float,
        movingY: Float,
        movingWidth: Float,
        movingHeight: Float,
        pageIndex: Int,
        allElements: List<LetterheadElement>,
        pageSettings: PageSettings,
        thresholdMm: Float = 2.0f
    ): SnapResult {
        var snappedX = movingX
        var snappedY = movingY
        val guides = mutableListOf<SnapGuide>()

        if (!pageSettings.snapToGuides) {
            if (pageSettings.snapToGrid) {
                snappedX = snapValueToGrid(snappedX, pageSettings.gridSizeMm)
                snappedY = snapValueToGrid(snappedY, pageSettings.gridSizeMm)
            }
            return SnapResult(roundMm(snappedX), roundMm(snappedY), guides)
        }

        val margins = pageSettings.margins
        val xTargets = mutableListOf(
            margins.left to "margin",
            (pageSettings.widthMm - margins.right) to "margin",
            (pageSettings.widthMm / 2f) to "page-center"
        )
        val yTargets = mutableListOf(
            margins.top to "margin",
            (pageSettings.heightMm - margins.bottom) to "margin",
            (pageSettings.heightMm / 2f) to "page-center"
        )

        for (el in allElements) {
            if (el.id == movingId || el.pageIndex != pageIndex || !el.visible) continue
            xTargets.add(el.x to "element")
            xTargets.add((el.x + el.width / 2f) to "element")
            xTargets.add((el.x + el.width) to "element")
            yTargets.add(el.y to "element")
            yTargets.add((el.y + el.height / 2f) to "element")
            yTargets.add((el.y + el.height) to "element")
        }

        var bestDx = thresholdMm + 1f
        var bestXGuide: SnapGuide? = null
        var bestNewX = snappedX

        val movingXEdges = listOf(
            0f to movingX,
            (movingWidth / 2f) to (movingX + movingWidth / 2f),
            movingWidth to (movingX + movingWidth)
        )

        for ((offset, edgePos) in movingXEdges) {
            for ((pos, kind) in xTargets) {
                val diff = abs(edgePos - pos)
                if (diff < bestDx) {
                    bestDx = diff
                    bestNewX = pos - offset
                    bestXGuide = SnapGuide("x", pos, kind)
                }
            }
        }

        if (bestXGuide != null && bestDx <= thresholdMm) {
            snappedX = bestNewX
            guides.add(bestXGuide)
        } else if (pageSettings.snapToGrid) {
            snappedX = snapValueToGrid(snappedX, pageSettings.gridSizeMm)
        }

        var bestDy = thresholdMm + 1f
        var bestYGuide: SnapGuide? = null
        var bestNewY = snappedY

        val movingYEdges = listOf(
            0f to movingY,
            (movingHeight / 2f) to (movingY + movingHeight / 2f),
            movingHeight to (movingY + movingHeight)
        )

        for ((offset, edgePos) in movingYEdges) {
            for ((pos, kind) in yTargets) {
                val diff = abs(edgePos - pos)
                if (diff < bestDy) {
                    bestDy = diff
                    bestNewY = pos - offset
                    bestYGuide = SnapGuide("y", pos, kind)
                }
            }
        }

        if (bestYGuide != null && bestDy <= thresholdMm) {
            snappedY = bestNewY
            guides.add(bestYGuide)
        } else if (pageSettings.snapToGrid) {
            snappedY = snapValueToGrid(snappedY, pageSettings.gridSizeMm)
        }

        return SnapResult(roundMm(snappedX), roundMm(snappedY), guides)
    }

    enum class AlignmentTarget {
        LEFT, CENTER_X, RIGHT, TOP, MIDDLE_Y, BOTTOM
    }

    fun alignElementToPage(
        el: LetterheadElement,
        alignment: AlignmentTarget,
        pageSettings: PageSettings,
        respectMargins: Boolean = true
    ): LetterheadElement {
        val leftBound = if (respectMargins) pageSettings.margins.left else 0f
        val rightBound = if (respectMargins) pageSettings.widthMm - pageSettings.margins.right else pageSettings.widthMm
        val topBound = if (respectMargins) pageSettings.margins.top else 0f
        val bottomBound = if (respectMargins) pageSettings.heightMm - pageSettings.margins.bottom else pageSettings.heightMm

        return when (alignment) {
            AlignmentTarget.LEFT -> el.copy(x = roundMm(leftBound))
            AlignmentTarget.CENTER_X -> el.copy(x = roundMm((pageSettings.widthMm - el.width) / 2f))
            AlignmentTarget.RIGHT -> el.copy(x = roundMm(rightBound - el.width))
            AlignmentTarget.TOP -> el.copy(y = roundMm(topBound))
            AlignmentTarget.MIDDLE_Y -> el.copy(y = roundMm((pageSettings.heightMm - el.height) / 2f))
            AlignmentTarget.BOTTOM -> el.copy(y = roundMm(bottomBound - el.height))
        }
    }

    enum class ZOrderAction {
        FORWARD, BACKWARD, FRONT, BACK
    }

    fun reorderZIndex(
        elements: List<LetterheadElement>,
        targetId: String,
        action: ZOrderAction
    ): List<LetterheadElement> {
        val target = elements.find { it.id == targetId } ?: return elements
        val samePage = elements.filter { it.pageIndex == target.pageIndex }
            .sortedBy { it.zIndex }
            .toMutableList()
        val idx = samePage.indexOfFirst { it.id == targetId }
        if (idx == -1) return elements

        when (action) {
            ZOrderAction.FORWARD -> if (idx < samePage.lastIndex) {
                val tmp = samePage[idx]
                samePage[idx] = samePage[idx + 1]
                samePage[idx + 1] = tmp
            }
            ZOrderAction.BACKWARD -> if (idx > 0) {
                val tmp = samePage[idx]
                samePage[idx] = samePage[idx - 1]
                samePage[idx - 1] = tmp
            }
            ZOrderAction.FRONT -> {
                val item = samePage.removeAt(idx)
                samePage.add(item)
            }
            ZOrderAction.BACK -> {
                val item = samePage.removeAt(idx)
                samePage.add(0, item)
            }
        }

        val newZMap = samePage.mapIndexed { i, item -> item.id to ((i + 1) * 10) }.toMap()
        return elements.map { el ->
            val z = newZMap[el.id]
            if (z != null) el.copy(zIndex = z) else el
        }
    }

    fun rectsOverlap(
        a: LetterheadElement,
        b: LetterheadElement,
        padding: Float = 0f
    ): Boolean {
        return !(
            a.x + a.width + padding <= b.x ||
                b.x + b.width + padding <= a.x ||
                a.y + a.height + padding <= b.y ||
                b.y + b.height + padding <= a.y
            )
    }
}
