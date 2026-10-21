package com.drivescan.kmp.scan

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Dependency-free document quadrilateral detector (v1.1.0).
 *
 * Pipeline on a small grayscale frame: central-difference gradient edges →
 * convex hull of edge points → hull reduced to 4 corners → geometric
 * validation (area + corner angles). Pure Kotlin / JVM so it is unit
 * testable off-device. Stability over time is the caller's job.
 */
object DocDetector {

    private data class Pt(val x: Float, val y: Float)

    /**
     * [gray] is row-major luminance 0..255 of size [w]*[h].
     * Returns normalized corners (TL, TR, BR, BL) in 0..1, or null.
     */
    fun detect(gray: IntArray, w: Int, h: Int): FloatArray? {
        if (w < 16 || h < 16 || gray.size < w * h) return null

        // 1) Edge points from gradient magnitude.
        val edges = ArrayList<Pt>()
        val threshold = 42
        for (y in 1 until h - 1) {
            val row = y * w
            for (x in 1 until w - 1) {
                val gx = gray[row + x + 1] - gray[row + x - 1]
                val gy = gray[row + w + x] - gray[row - w + x]
                if (abs(gx) + abs(gy) > threshold) edges.add(Pt(x.toFloat(), y.toFloat()))
            }
        }
        if (edges.size < 40) return null

        // 2) Convex hull (Andrew's monotone chain).
        val hull = convexHull(edges)
        if (hull.size < 4) return null

        // 3) Reduce hull to exactly 4 corners (drop least-significant vertices).
        val quad = reduceToQuad(hull)
        if (quad.size != 4) return null

        // 4) Order corners TL, TR, BR, BL.
        val tl = quad.minByOrNull { it.x + it.y } ?: return null
        val br = quad.maxByOrNull { it.x + it.y } ?: return null
        val tr = quad.maxByOrNull { it.x - it.y } ?: return null
        val bl = quad.minByOrNull { it.x - it.y } ?: return null
        val ordered = listOf(tl, tr, br, bl)
        if (ordered.toSet().size != 4) return null

        // 5) Validate geometry.
        val frameArea = (w * h).toDouble()
        val area = abs(polygonArea(ordered))
        if (area < frameArea * 0.10 || area > frameArea * 0.96) return null
        for (i in 0 until 4) {
            val prev = ordered[(i + 3) % 4]
            val cur = ordered[i]
            val next = ordered[(i + 1) % 4]
            val angle = angleDeg(prev, cur, next)
            if (angle < 48.0 || angle > 132.0) return null
        }
        val top = dist(tl, tr); val bottom = dist(bl, br)
        val left = dist(tl, bl); val right = dist(tr, br)
        if (top <= 0 || bottom <= 0 || left <= 0 || right <= 0) return null
        if (maxOf(top, bottom) / minOf(top, bottom) > 2.8) return null
        if (maxOf(left, right) / minOf(left, right) > 2.8) return null

        return floatArrayOf(
            tl.x / w, tl.y / h,
            tr.x / w, tr.y / h,
            br.x / w, br.y / h,
            bl.x / w, bl.y / h
        )
    }

    private fun convexHull(points: List<Pt>): List<Pt> {
        val sorted = points.sortedWith(compareBy<Pt> { it.x }.thenBy { it.y })
        fun cross(o: Pt, a: Pt, b: Pt): Float =
            (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
        val lower = ArrayList<Pt>()
        for (p in sorted) {
            while (lower.size >= 2 && cross(lower[lower.size - 2], lower[lower.size - 1], p) <= 0f) {
                lower.removeAt(lower.size - 1)
            }
            lower.add(p)
        }
        val upper = ArrayList<Pt>()
        for (p in sorted.asReversed()) {
            while (upper.size >= 2 && cross(upper[upper.size - 2], upper[upper.size - 1], p) <= 0f) {
                upper.removeAt(upper.size - 1)
            }
            upper.add(p)
        }
        // Drop the last point of each half (repeated endpoints).
        return lower.dropLast(1) + upper.dropLast(1)
    }

    /** Repeatedly removes the hull vertex closest to its neighbours' segment until 4 remain. */
    private fun reduceToQuad(hull: List<Pt>): List<Pt> {
        val pts = hull.toMutableList()
        while (pts.size > 4) {
            var removeIdx = -1
            var bestDist = Float.MAX_VALUE
            for (i in pts.indices) {
                val prev = pts[(i + pts.size - 1) % pts.size]
                val next = pts[(i + 1) % pts.size]
                val d = pointSegmentDistance(pts[i], prev, next)
                if (d < bestDist) {
                    bestDist = d
                    removeIdx = i
                }
            }
            if (removeIdx < 0) break
            pts.removeAt(removeIdx)
        }
        return pts
    }

    private fun pointSegmentDistance(p: Pt, a: Pt, b: Pt): Float {
        val abx = b.x - a.x
        val aby = b.y - a.y
        val len2 = abx * abx + aby * aby
        if (len2 == 0f) return dist(p, a)
        val t = (((p.x - a.x) * abx + (p.y - a.y) * aby) / len2).coerceIn(0f, 1f)
        val projX = a.x + t * abx
        val projY = a.y + t * aby
        return sqrt((p.x - projX) * (p.x - projX) + (p.y - projY) * (p.y - projY))
    }

    private fun polygonArea(pts: List<Pt>): Double {
        var sum = 0.0
        for (i in pts.indices) {
            val a = pts[i]
            val b = pts[(i + 1) % pts.size]
            sum += (a.x * b.y - b.x * a.y).toDouble()
        }
        return sum / 2.0
    }

    private fun angleDeg(a: Pt, b: Pt, c: Pt): Double {
        val v1x = a.x - b.x
        val v1y = a.y - b.y
        val v2x = c.x - b.x
        val v2y = c.y - b.y
        val dot = v1x * v2x + v1y * v2y
        val m1 = sqrt(v1x * v1x + v1y * v1y)
        val m2 = sqrt(v2x * v2x + v2y * v2y)
        if (m1 == 0f || m2 == 0f) return 0.0
        val cos = (dot / (m1 * m2)).coerceIn(-1f, 1f)
        return Math.toDegrees(atan2(sqrt(1f - cos * cos).toDouble(), cos.toDouble()))
    }

    private fun dist(a: Pt, b: Pt): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }
}
