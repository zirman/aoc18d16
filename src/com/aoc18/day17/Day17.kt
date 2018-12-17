package com.aoc18.day17

import com.aoc18.day16.parseEndLine
import com.aoc18.day16.parsePosNum
import com.aoc18.parser.ParseResult
import com.aoc18.parser.Parser
import com.aoc18.parser.andThen
import com.aoc18.parser.apLeft
import com.aoc18.parser.orElse
import com.aoc18.parser.parse
import com.aoc18.parser.parseLift
import com.aoc18.parser.parseString
import com.aoc18.parser.zeroOrMoreTimes
import com.aoc18.utils.readFile

sealed class ClayRange {
    data class ClayRangeY(val x: Int, val yRange: IntRange) : ClayRange() {
        override fun xMin(): Int = x
        override fun xMax(): Int = x
        override fun yMin(): Int = yRange.min()!!
        override fun yMax(): Int = yRange.max()!!
    }

    data class ClayRangeX(val y: Int, val xRange: IntRange) : ClayRange() {
        override fun xMin(): Int = xRange.min()!!
        override fun xMax(): Int = xRange.max()!!
        override fun yMin(): Int = y
        override fun yMax(): Int = y
    }

    abstract fun xMin(): Int
    abstract fun xMax(): Int
    abstract fun yMin(): Int
    abstract fun yMax(): Int
}

val parseClayRangeY: Parser<ClayRange.ClayRangeY> =
    parseString("x=")
        .andThen { parsePosNum }
        .andThen { x ->
            parseString(", y=")
                .andThen {
                    parsePosNum
                        .andThen { yBeginRange ->
                            parseString("..")
                                .andThen {
                                    parsePosNum
                                        .andThen { yEndRange ->
                                            ClayRange
                                                .ClayRangeY(x, yBeginRange..yEndRange)
                                                .parseLift()
                                        }
                                }
                        }
                }
        }

val parseClayRangeX: Parser<ClayRange.ClayRangeX> =
    parseString("y=")
        .andThen { parsePosNum }
        .andThen { y ->
            parseString(", x=")
                .andThen {
                    parsePosNum
                        .andThen { xBeginRange ->
                            parseString("..")
                                .andThen {
                                    parsePosNum
                                        .andThen { xEndRange ->
                                            ClayRange
                                                .ClayRangeX(y, xBeginRange..xEndRange)
                                                .parseLift()
                                        }
                                }
                        }
                }
        }

val parseClayRange: Parser<ClayRange> = parseClayRangeY.orElse(parseClayRangeX).apLeft(parseEndLine)
val parseClayFile: Parser<List<ClayRange>> = parseClayRange.zeroOrMoreTimes()

enum class FlowTile {
    Sand,
    Clay,
    FlowingWater,
    StandingWater,
}

class FlowBoard(val xOrigin: Int, val yOrigin: Int, val width: Int, val height: Int) {
    val tiles: Array<FlowTile> =
        (generateSequence { FlowTile.Sand }).take(width * height).toList().toTypedArray()

    fun inBounds(x: Int, y: Int) =
        x >= xOrigin && x < xOrigin + width && y >= yOrigin && y < yOrigin + height

    operator fun set(x: Int, y: Int, tile: FlowTile) {
        tiles[(y - yOrigin) * width + (x - xOrigin)] = tile
    }

    operator fun get(x: Int, y: Int): FlowTile {
        val i = (y - yOrigin) * width + (x - xOrigin)
        return if (i >= 0 && i < tiles.size) tiles[i] else FlowTile.Sand
    }

    fun rowToString(y: Int): String =
        (0 until width)
            .joinToString("") { x ->
                when (tiles[y * width + x]) {
                    FlowTile.Clay -> "#"
                    FlowTile.Sand -> "."
                    FlowTile.FlowingWater -> "|"
                    FlowTile.StandingWater -> "~"
                }
            }

    override fun toString(): String =
        (0 until height).joinToString("\n") { y -> rowToString(y) }

    fun countWater(): Int =
        tiles.sumBy { tile ->
            when (tile) {
                FlowTile.Sand -> 0
                FlowTile.Clay -> 0
                FlowTile.FlowingWater -> 1
                FlowTile.StandingWater -> 1
            }
        }

    fun countStandingWater(): Int =
        tiles.sumBy { tile ->
            when (tile) {
                FlowTile.Sand -> 0
                FlowTile.Clay -> 0
                FlowTile.FlowingWater -> 0
                FlowTile.StandingWater -> 1
            }
        }
}

fun standingLeft(board: FlowBoard, x: Int, y: Int) {
    if (board[x, y] == FlowTile.FlowingWater) {
        board[x, y] = FlowTile.StandingWater
        standingLeft(board, x - 1, y)
    }
}

fun standingRight(board: FlowBoard, x: Int, y: Int) {
    if (board[x, y] == FlowTile.FlowingWater) {
        board[x, y] = FlowTile.StandingWater
        standingRight(board, x + 1, y)
    }
}

fun flowLeft(board: FlowBoard, x: Int, y: Int): Boolean =
    when (board[x, y]) {
        FlowTile.Clay, FlowTile.StandingWater -> false
        FlowTile.FlowingWater -> true

        FlowTile.Sand -> {
            board[x, y] = FlowTile.FlowingWater
            flowDown(board, x, y + 1) || flowLeft(board, x - 1, y)
        }
    }

fun flowRight(board: FlowBoard, x: Int, y: Int): Boolean =
    when (board[x, y]) {
        FlowTile.Clay, FlowTile.StandingWater -> false
        FlowTile.FlowingWater -> true

        FlowTile.Sand -> {
            board[x, y] = FlowTile.FlowingWater
            flowDown(board, x, y + 1) || flowRight(board, x + 1, y)
        }
    }

fun flowDown(board: FlowBoard, x: Int, y: Int): Boolean =
    if (board.inBounds(x, y)) {
        when (board[x, y]) {
            FlowTile.Clay, FlowTile.StandingWater -> false
            FlowTile.FlowingWater -> true

            FlowTile.Sand -> {
                if (flowDown(board, x, y + 1)) {
                    board[x, y] = FlowTile.FlowingWater
                    true
                } else {
                    val left = flowLeft(board, x - 1, y)
                    val right = flowRight(board, x + 1, y)

                    if (left || right) {
                        board[x, y] = FlowTile.FlowingWater
                        true
                    } else {
                        board[x, y] = FlowTile.StandingWater
                        standingLeft(board, x - 1, y)
                        standingRight(board, x + 1, y)
                        false
                    }
                }
            }
        }
    } else {
        true
    }

fun initBoard(clayRanges: List<ClayRange>): FlowBoard {
    val xMin = clayRanges.minBy { clayRange -> clayRange.xMin() }!!.xMin() - 1
    val xMax = clayRanges.maxBy { clayRange -> clayRange.xMax() }!!.xMax() + 1
    val yMin = clayRanges.minBy { clayRange -> clayRange.yMin() }!!.yMin()
    val yMax = clayRanges.maxBy { clayRange -> clayRange.yMax() }!!.yMax()

    val board = FlowBoard(xMin, yMin, xMax - xMin + 1, yMax - yMin + 1)

    clayRanges.forEach { clayRange ->
        when (clayRange) {
            is ClayRange.ClayRangeY -> {
                for (y in clayRange.yRange) {
                    board[clayRange.x, y] = FlowTile.Clay
                }
            }
            is ClayRange.ClayRangeX -> {
                for (x in clayRange.xRange) {
                    board[x, clayRange.y] = FlowTile.Clay
                }
            }
        }
    }

    return board
}

fun main() {
    val file = readFile("day17.txt")
    val result = parseClayFile.parse(file)
    result as ParseResult.OK
    val board: FlowBoard = initBoard(result.value)
    flowDown(board, 500, board.yOrigin)
    println(board.toString())
    println("part1: ${board.countWater()}")
    println("part2: ${board.countStandingWater()}")
}
