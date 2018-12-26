package com.aoc18.day25

import com.aoc18.day16.parseEndLine
import com.aoc18.parser.ParseResult
import com.aoc18.parser.Parser
import com.aoc18.parser.andThen
import com.aoc18.parser.keepNext
import com.aoc18.parser.keepPrevious
import com.aoc18.parser.map
import com.aoc18.parser.parse
import com.aoc18.parser.parseChar
import com.aoc18.parser.parseInt
import com.aoc18.parser.zeroOrMoreTimes
import com.aoc18.utils.readFile

data class Point(val x: Int, val y: Int, val z: Int, val w: Int)

fun Point.distance(point: Point): Int =
    Math.abs(x - point.x) + Math.abs(y - point.y) + Math.abs(z - point.z) + Math.abs(w - point.w)

val parsePoint: Parser<Point> =
    parseInt
        .andThen { x ->
            parseChar(',')
                .keepNext(parseInt)
                .andThen { y ->
                    parseChar(',')
                        .keepNext(parseInt)
                        .andThen { z ->
                            parseChar(',')
                                .keepNext(parseInt)
                                .map { w -> Point(x, y, z, w) }
                        }
                }
        }
        .keepPrevious(parseEndLine)

val parsePointFile: Parser<List<Point>> = parsePoint.zeroOrMoreTimes()

data class Bounds(
    val xRange: IntRange,
    val yRange: IntRange,
    val zRange: IntRange,
    val wRange: IntRange
)

fun IntRange.width(): Int = endInclusive - start + 1

fun Bounds.isPointInsideBounds(point: Point): Boolean =
    point.x in xRange && point.y in yRange && point.z in zRange && point.w in wRange

fun Bounds.index(point: Point): Int =
    (point.x - xRange.start) +
        ((point.y - yRange.start) * (xRange.width())) +
        ((point.z - zRange.start) * (xRange.width() * yRange.width())) +
        ((point.w - wRange.start) * (xRange.width() * yRange.width() * zRange.width()))

fun Bounds.width(): Int = xRange.width() * yRange.width() * zRange.width() * wRange.width()

data class SpaceTime(val bounds: Bounds) {
    private val array: IntArray = IntArray(bounds.width()) { Int.MAX_VALUE }

    operator fun set(point: Point, x: Int) {
        array[bounds.index(point)] = x
    }

    operator fun get(point: Point): Int =
        if (bounds.isPointInsideBounds(point)) array[bounds.index(point)] else Int.MIN_VALUE
}

fun main() {
    val file = readFile("day25.txt")
    val result = parsePointFile.parse(file)
    result as ParseResult.OK
    val points = result.value

    val spaceTime =
        SpaceTime(
            Bounds(
                xRange = points.minBy { it.x }!!.x..points.maxBy { it.x }!!.x,
                yRange = points.minBy { it.y }!!.y..points.maxBy { it.y }!!.y,
                zRange = points.minBy { it.z }!!.z..points.maxBy { it.z }!!.z,
                wRange = points.minBy { it.w }!!.w..points.maxBy { it.w }!!.w
            )
        )

    fun distanceFill(point: Point, distance: Int) {
        if (distance > 1) return

        val p = spaceTime[point]

        if (distance < p) {
            spaceTime[point] = distance

            distanceFill(point.copy(x = point.x - 1), distance + 1)
            distanceFill(point.copy(x = point.x + 1), distance + 1)
            distanceFill(point.copy(y = point.y - 1), distance + 1)
            distanceFill(point.copy(y = point.y + 1), distance + 1)
            distanceFill(point.copy(z = point.z - 1), distance + 1)
            distanceFill(point.copy(z = point.z + 1), distance + 1)
            distanceFill(point.copy(w = point.w - 1), distance + 1)
            distanceFill(point.copy(w = point.w + 1), distance + 1)
        }
    }

    points.forEach { point ->
        distanceFill(point, 0)
    }

    fun floodFill(point: Point) {
        val p = spaceTime[point]

        if (p != Int.MIN_VALUE && p != Int.MAX_VALUE) {
            spaceTime[point] = Int.MIN_VALUE

            floodFill(point.copy(x = point.x - 1))
            floodFill(point.copy(x = point.x + 1))
            floodFill(point.copy(y = point.y - 1))
            floodFill(point.copy(y = point.y + 1))
            floodFill(point.copy(z = point.z - 1))
            floodFill(point.copy(z = point.z + 1))
            floodFill(point.copy(w = point.w - 1))
            floodFill(point.copy(w = point.w + 1))
        }
    }

    var count = 0

    points.forEach { point ->
        if (spaceTime[point] != Int.MIN_VALUE) {
            count += 1
            floodFill(point)
        }
    }

    println(count)
}

// 0123456789
//0 1  1
//1101101
//2 1  1
//3
//4
//5
//6
//7
//8
//9