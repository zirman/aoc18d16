package com.aoc18.day20

import com.aoc18.parser.ParseResult
import com.aoc18.parser.Parser
import com.aoc18.parser.andThen
import com.aoc18.parser.becomes
import com.aoc18.parser.otherwise
import com.aoc18.parser.fixPoint
import com.aoc18.parser.keepNext
import com.aoc18.parser.keepPrevious
import com.aoc18.parser.map
import com.aoc18.parser.ofThese
import com.aoc18.parser.oneOrMoreTimes
import com.aoc18.parser.parse
import com.aoc18.parser.parseChar
import com.aoc18.utils.readFile

enum class Direction {
    North,
    South,
    East,
    West
}

typealias AndGroups = List<Group>
typealias OrGroups = List<AndGroups>

sealed class Group {
    data class Directions(val directions: List<Direction>) : Group()
    data class SubGroups(val groups: OrGroups) : Group()
}

val parseDirection: Parser<Direction> =
    listOf(
        parseChar('N').becomes(Direction.North),
        parseChar('W').becomes(Direction.West),
        parseChar('E').becomes(Direction.East),
        parseChar('S').becomes(Direction.South)
    )
        .ofThese()

val parseGroup: Parser<OrGroups> =
    fixPoint { parser ->
        listOf(
            parseDirection.oneOrMoreTimes().map { directions -> Group.Directions(directions) },
            parseChar('(')
                .keepNext(parser).map { subGroup -> Group.SubGroups(subGroup) }
                .keepPrevious(parseChar(')'))
        )
            .ofThese()
            .oneOrMoreTimes() // And
            .oneOrMoreTimes(parseChar('|')) // Or
            .andThen { orGroup ->
                parseChar('|')
                    .map { orGroup.plus(listOf(listOf(Group.Directions(emptyList())))) }
                    .otherwise(orGroup)
            }
    }

val parseFile: Parser<Group> =
    parseChar('^')
        .keepNext(parseGroup)
        .map { Group.SubGroups(it) }
        .keepPrevious(parseChar('$'))
        .keepPrevious(parseChar('\n'))

enum class HouseTile {
    Wall,
    NSDoor,
    WEDoor,
    Floor
}

data class Rect(val xMin: Int, val xMax: Int, val yMin: Int, val yMax: Int)
data class Pos(val x: Int, val y: Int)

fun movePos(direction: Direction, pos: Pos): Pos =
    when (direction) {
        Direction.West -> Pos(pos.x - 1, pos.y)
        Direction.East -> Pos(pos.x + 1, pos.y)
        Direction.North -> Pos(pos.x, pos.y - 1)
        Direction.South -> Pos(pos.x, pos.y + 1)
    }

fun directionDoor(direction: Direction): HouseTile =
    when (direction) {
        Direction.North, Direction.South -> HouseTile.NSDoor
        Direction.East, Direction.West -> HouseTile.WEDoor
    }

fun ordinals(rect: Rect): Int = width(rect) * height(rect)
fun width(rect: Rect): Int = rect.xMax - rect.xMin + 1
fun height(rect: Rect): Int = rect.yMax - rect.yMin + 1

fun posToOrdinal(pos: Pos, rect: Rect): Int =
    ((pos.y - rect.yMin) * width(rect)) + (pos.x - rect.xMin)

fun ordinalToPos(ordinal: Int, rect: Rect): Pos {
    val wd = width(rect)
    return Pos(x = (ordinal % wd) + rect.xMin, y = (ordinal / wd) + rect.yMin)
}

fun contains(pos: Pos, rect: Rect): Boolean =
    pos.x >= rect.xMin && pos.x <= rect.xMax && pos.y >= rect.yMin && pos.y <= rect.yMax

fun addPoint(rect: Rect, pos: Pos): Rect =
    rect.copy(
        xMin = Math.min(rect.xMin, pos.x),
        xMax = Math.max(rect.xMax, pos.x),
        yMin = Math.min(rect.yMin, pos.y),
        yMax = Math.max(rect.yMax, pos.y)
    )

class HouseGrid {
    var rect: Rect = Rect(0, 0, 0, 0)

    private var tiles: Array<HouseTile> =
        (generateSequence { HouseTile.Wall }).take(ordinals(rect)).toList().toTypedArray()

    operator fun set(pos: Pos, tile: HouseTile) {
        if (contains(pos, rect).not()) {
            val newRect = addPoint(rect, pos)

            val newTiles =
                sequence {
                    for (i in 0 until ordinals(newRect)) {
                        val toPos = ordinalToPos(i, newRect)

                        yield(
                            if (contains(toPos, rect)) {
                                tiles[posToOrdinal(toPos, rect)]
                            } else {
                                HouseTile.Wall
                            }
                        )
                    }
                }
                    .toList()
                    .toTypedArray()

            rect = newRect
            tiles = newTiles
        }

        tiles[posToOrdinal(pos, rect)] = tile
    }

    operator fun get(pos: Pos): HouseTile =
        if (contains(pos, rect)) tiles[posToOrdinal(pos, rect)] else HouseTile.Wall

    private fun rowToString(y: Int): String =
        (rect.xMin - 1..rect.xMax + 1)
            .joinToString("") { x ->
                if (x == 0 && y == 0) {
                    "X"
                } else {
                    when (get(Pos(x = x, y = y))) {
                        HouseTile.Wall -> "#"
                        HouseTile.WEDoor -> "|"
                        HouseTile.NSDoor -> "-"
                        HouseTile.Floor -> "."
                    }
                }
            }

    override fun toString(): String =
        (rect.yMin - 1..rect.yMax + 1).joinToString("\n") { y -> rowToString(y) }
}

class DistanceGrid(val rect: Rect) {
    val tiles: Array<Int?> = arrayOfNulls(ordinals(rect))

    operator fun set(pos: Pos, distance: Int) {
        tiles[posToOrdinal(pos, rect)] = distance
    }

    operator fun get(pos: Pos): Int? = tiles[posToOrdinal(pos, rect)]
}

fun makeHouseGrid(group: Group): HouseGrid {
    val houseGrid = HouseGrid()
    houseGrid[Pos(0, 0)] = HouseTile.Floor

    fun traversePaths(group: Group, pos: Pos): Set<Pos> =
        when (group) {
            is Group.Directions -> {
                var newPos = pos

                group.directions.forEach { direction ->
                    newPos = movePos(direction, newPos)
                    houseGrid[newPos] = directionDoor(direction)
                    newPos = movePos(direction, newPos)
                    houseGrid[newPos] = HouseTile.Floor
                }

                setOf(newPos)
            }

            is Group.SubGroups -> {
                group.groups
                    .fold(emptySet()) { acc, andGroups ->
                        acc.plus(
                            andGroups
                                .fold(setOf(pos)) { acc2, group ->
                                    acc2.flatMap { p -> traversePaths(group, p) }
                                        .toSet()
                                }
                        )
                    }
            }
        }

    traversePaths(group, Pos(0, 0))
    return houseGrid
}

fun makeDistanceGrid(houseGrid: HouseGrid): DistanceGrid {
    val distanceGrid = DistanceGrid(houseGrid.rect)

    fun traverseHouse(pos: Pos, distance: Int) {
        if (distanceGrid[pos] != null) return
        distanceGrid[pos] = distance

        val n = movePos(Direction.North, pos)

        if (houseGrid[n] == HouseTile.NSDoor) {
            traverseHouse(movePos(Direction.North, n), distance + 1)
        }

        val s = movePos(Direction.South, pos)

        if (houseGrid[s] == HouseTile.NSDoor) {
            traverseHouse(movePos(Direction.South, s), distance + 1)
        }

        val w = movePos(Direction.West, pos)

        if (houseGrid[w] == HouseTile.WEDoor) {
            traverseHouse(movePos(Direction.West, w), distance + 1)
        }

        val e = movePos(Direction.East, pos)

        if (houseGrid[e] == HouseTile.WEDoor) {
            traverseHouse(movePos(Direction.East, e), distance + 1)
        }
    }

    traverseHouse(Pos(0, 0), 0)
    return distanceGrid
}

fun main() {
    val file = readFile("day20.txt")
    val result = parseFile.parse(file)
    result as ParseResult.OK
    val houseGrid = makeHouseGrid(result.value)
    println(houseGrid)
    val distanceGrid = makeDistanceGrid(houseGrid)
    val distances = distanceGrid.tiles.filterNotNull()
    println("part1: ${distances.max()}")
    println("part2: ${distances.filter { it >= 1000 }.count()}")
}
