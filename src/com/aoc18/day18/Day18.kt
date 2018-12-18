package com.aoc18.day18

import com.aoc18.utils.readFile

enum class ForestTile {
    Open,
    Trees,
    Lumberyard,
}

data class ForestBoard(
    val width: Int,
    val height: Int,
    private val tiles: Array<ForestTile> =
        generateSequence { ForestTile.Open }.take(width * height).toList().toTypedArray()
) {
    operator fun set(x: Int, y: Int, tile: ForestTile) {
        tiles[y * width + x] = tile
    }

    operator fun get(x: Int, y: Int): ForestTile {
        val i = y * width + x
        return if (i >= 0 && i < tiles.size) tiles[i] else ForestTile.Open
    }

    override fun toString(): String =
        (0 until height).joinToString("\n") { y -> rowToString(y) }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ForestBoard

        if (width != other.width) return false
        if (height != other.height) return false
        if (!tiles.contentEquals(other.tiles)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + tiles.contentHashCode()
        return result
    }

    fun iterate(): ForestBoard {
        val nextForest = copy()

        for (x in 0 until width) {
            for (y in 0 until height) {
                when (get(x, y)) {

                    ForestTile.Open ->
                        if (adjacentSum(x, y, ForestTile.Trees) >= 3) {
                            nextForest[x, y] = ForestTile.Trees
                        }

                    ForestTile.Trees ->
                        if (adjacentSum(x, y, ForestTile.Lumberyard) >= 3) {
                            nextForest[x, y] = ForestTile.Lumberyard
                        }

                    ForestTile.Lumberyard ->
                        if (adjacentSum(x, y, ForestTile.Lumberyard) == 0 ||
                            adjacentSum(x, y, ForestTile.Trees) == 0
                        ) {
                            nextForest[x, y] = ForestTile.Open
                        }
                }
            }
        }

        return nextForest
    }

    fun totalResourceValue(): Int =
        countAdjacent(ForestTile.Trees) * countAdjacent(ForestTile.Lumberyard)

    private fun inBounds(x: Int, y: Int) = x in 0 until width && y in 0 until height

    fun countAdjacent(t: ForestTile): Int {
        var sum = 0

        for (i in 0 until tiles.size) {
            if (t == tiles[i]) {
                sum += 1
            }
        }

        return sum
    }

    private fun adjacentSum(x: Int, y: Int, t: ForestTile): Int {
        var sum = 0

        for (xx in x - 1..x + 1) {
            for (yy in y - 1..y + 1) {
                if ((xx != x || yy != y) && inBounds(xx, yy) && t == tiles[yy * width + xx]) {
                    sum += 1
                }
            }
        }

        return sum
    }

    private fun rowToString(y: Int): String =
        (0 until width)
            .joinToString("") { x ->
                when (tiles[y * width + x]) {
                    ForestTile.Open -> "."
                    ForestTile.Trees -> "|"
                    ForestTile.Lumberyard -> "#"
                }
            }

    private fun copy(): ForestBoard {
        val board = ForestBoard(width, height)

        for (i in 0 until tiles.size) {
            board.tiles[i] = tiles[i]
        }

        return board
    }
}

fun initBoard(inputString: String): ForestBoard {
    val lines = inputString.split('\n')
    val width: Int = lines.map { l -> l.length }.max()!!
    val height: Int = lines.count() - 1

    val board = ForestBoard(width, height)
    var x = 0
    var y = 0

    inputString.forEach { c ->
        if (c == '\n') {
            x = 0
            y += 1
        } else {
            board[x, y] = when (c) {
                '.' -> ForestTile.Open
                '#' -> ForestTile.Lumberyard
                '|' -> ForestTile.Trees
                else -> throw Exception("Unrecognized character \"$c\"")
            }

            x += 1
        }
    }

    return board
}

fun main() {
    val file = readFile("day18.txt")
    var currentBoard = initBoard(file)

    for (i in 1..10) {
        currentBoard = currentBoard.iterate()
    }

    val part1 = currentBoard

    currentBoard = initBoard(file)

    val boardToIteration: MutableMap<ForestBoard, Int> = mutableMapOf()

    val maxIteration = 1000000000

    for (i in 1..maxIteration) {
        val nextBoard = currentBoard.iterate()

        if (boardToIteration.containsKey(nextBoard)) {
            for (j in 0..(maxIteration - i) % (i - boardToIteration[nextBoard]!!)) {
                currentBoard = currentBoard.iterate()
                println(currentBoard)
            }

            break
        }

        boardToIteration[nextBoard] = i
        currentBoard = nextBoard
    }

    println("part1: ${part1.totalResourceValue()}")
    println("part2: ${currentBoard.totalResourceValue()}")
}
