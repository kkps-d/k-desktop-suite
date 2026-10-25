package com.kkps.kdesktopsuite.paint

import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

class RoundStrokeTest {
    @Test fun roundStrokesKeepTheirExactCoverageAndBlending() {
        val base = Raster(140,120,IntArray(140 * 120) { 0xFF000000.toInt() or (it * 7919 and 0xFFFFFF) })
        val points = listOf(Point(-3.4f,5.2f),Point(27.8f,14.6f),Point(27.8f,14.6f),Point(62.1f,54.9f),Point(145f,125f))
        for (hard in listOf(true,false)) for (size in listOf(1f,1.5f,3f,8f,25f,100f)) {
            for (color in listOf(BLACK,0x803A79C2.toInt(),0x003A79C2)) for (clip in listOf(null,PixelRect(9,11,95,77),PixelRect(140,120,0,0))) {
                val original = base.pixels.copyOf()
                assertArrayEquals("hard=$hard size=$size color=$color clip=$clip",
                    referenceStroke(base,points,color,size,hard,clip),base.stroke(points,color,size,hard,clip).pixels)
                assertArrayEquals(original,base.pixels)
            }
        }
    }

    @Test(timeout = 2000) fun maximumWidthLongStrokesApplyQuickly() {
        val base = Raster.blank(800,600)
        val points = listOf(Point(50f,50f),Point(750f,150f),Point(50f,300f),Point(750f,550f))
        for (hard in listOf(true,false)) {
            val start = System.nanoTime()
            repeat(5) {
                val result = base.stroke(points,BLACK,100f,hard,null)
                assertEquals(BLACK,result.pixel(400,100))
            }
            println("100px stroke, hard=$hard: ${(System.nanoTime()-start)/5_000_000.0} ms per stroke")
        }
    }

    // Original dab renderer: guard against changing antialiasing, repeated dabs or selection edges.
    private fun referenceStroke(base: Raster, points: List<Point>, color: Int, size: Float, hard: Boolean, clip: PixelRect?): IntArray {
        val output = base.pixels.copyOf()
        val radius = max(0.5f,size / 2)
        fun dab(point: Point) {
            val cx = floor(point.x) + 0.5f
            val cy = floor(point.y) + 0.5f
            for (y in floor(cy-radius).toInt()..ceil(cy+radius).toInt()) {
                for (x in floor(cx-radius).toInt()..ceil(cx+radius).toInt()) {
                    if (x !in 0 until base.width || y !in 0 until base.height || clip?.contains(x,y) == false) continue
                    val distance = hypot(x+0.5f-cx,y+0.5f-cy)
                    val coverage = if (hard) { if (distance <= radius) 1f else 0f }
                        else (radius+0.5f-distance).coerceIn(0f,1f)
                    if (coverage > 0) {
                        val index = y*base.width+x
                        val alpha = (color ushr 24)/255f*coverage
                        var result = BLACK
                        for (shift in intArrayOf(0,8,16)) {
                            val channel = ((color ushr shift and 255)*alpha + (output[index] ushr shift and 255)*(1-alpha)).roundToInt()
                            result = result or (channel shl shift)
                        }
                        output[index] = result
                    }
                }
            }
        }
        dab(points.first())
        points.zipWithNext { start, end ->
            val steps = ceil(hypot(end.x-start.x,end.y-start.y)*2).toInt().coerceIn(1,32768)
            for (step in 1..steps) {
                val t = step.toFloat()/steps
                dab(Point(start.x+(end.x-start.x)*t,start.y+(end.y-start.y)*t))
            }
        }
        return output
    }
}
