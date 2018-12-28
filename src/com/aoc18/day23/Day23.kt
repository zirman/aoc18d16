package com.aoc18.day23

import com.aoc18.day16.parseDigits
import com.aoc18.parser.Parser
import com.aoc18.parser.Either
import com.aoc18.parser.andThen
import com.aoc18.parser.keepNext
import com.aoc18.parser.keepPrevious
import com.aoc18.parser.map
import com.aoc18.parser.parse
import com.aoc18.parser.parseChar
import com.aoc18.parser.parseLift
import com.aoc18.parser.parseString
import com.aoc18.parser.zeroOrMoreTimes
import com.aoc18.parser.zeroOrOneTime
import com.aoc18.utils.readFile

data class Pos(val x: Int, val y: Int, val z: Int)
data class Nanobot(val pos: Pos, val radius: Int)

fun adjacentPos(pos: Pos): List<Pos> =
    listOf(
        pos.copy(x = pos.x - 1),
        pos.copy(x = pos.x + 1),
        pos.copy(y = pos.y - 1),
        pos.copy(y = pos.y + 1),
        pos.copy(z = pos.z - 1),
        pos.copy(z = pos.z + 1)
    )

fun cornersNanobot(nanobot: Nanobot): List<Pos> =
    listOf(
        nanobot.pos.copy(x = nanobot.pos.x - nanobot.radius),
        nanobot.pos.copy(x = nanobot.pos.x + nanobot.radius),
        nanobot.pos.copy(y = nanobot.pos.y - nanobot.radius),
        nanobot.pos.copy(y = nanobot.pos.y + nanobot.radius),
        nanobot.pos.copy(z = nanobot.pos.z - nanobot.radius),
        nanobot.pos.copy(z = nanobot.pos.z + nanobot.radius)
    )

fun posInRange(nanobot: Nanobot, pos: Pos): Boolean =
    distancePos(pos, nanobot.pos) <= nanobot.radius

fun overlappingNanobot(bot1: Nanobot, bot2: Nanobot): List<Pos> {
    fun floodFill(pos1: Pos, filled: Set<Pos>): Set<Pos> {
        if (distancePos(pos1, bot1.pos) > bot1.radius ||
            distancePos(pos1, bot2.pos) > bot2.radius ||
            filled.contains(pos1)
        ) {
            return filled
        }

        return adjacentPos(pos1).fold(filled.plus(pos1)) { acc, pos ->
            floodFill(pos, acc)
        }
    }

    return cornersNanobot(if (bot1.radius <= bot2.radius) bot1 else bot2)
        .fold(emptySet<Pos>()) { filled, pos -> floodFill(pos, filled) }
        .toList()
}

val parseLong: Parser<Int> =
    parseChar('-')
        .zeroOrOneTime()
        .andThen { sign ->
            parseDigits
                .andThen { digits ->
                    digits
                        .joinToString("", sign?.let { "-" } ?: "")
                        .toInt()
                        .parseLift()
                }
        }

val parsePos: Parser<Pos> =
    parseChar('<')
        .keepNext(parseLong)
        .andThen { x ->
            parseChar(',')
                .keepNext(parseLong)
                .andThen { y ->
                    parseChar(',')
                        .keepNext(parseLong)
                        .map { z -> Pos(x, y, z) }
                }
        }
        .keepPrevious(parseChar('>'))

val parseBot: Parser<Nanobot> =
    parseString("pos=")
        .keepNext(parsePos)
        .andThen { pos ->
            parseString(", r=")
                .keepNext(parseLong)
                .map { r -> Nanobot(pos, r) }
        }
        .keepPrevious(parseChar('\n'))

val parseNanoBotFile: Parser<List<Nanobot>> =
    parseBot.zeroOrMoreTimes()

fun distancePos(pos1: Pos, pos2: Pos): Int =
    Math.abs(pos1.x - pos2.x) + Math.abs(pos1.y - pos2.y) + Math.abs(pos1.z - pos2.z)

fun main() {
    val file = readFile("day23.txt")
    val result = parseNanoBotFile.parse(file)
    result as Either.Ok
    val nanobots = result.value

    val largestRadiusBot = nanobots.maxBy { it.radius }!!

    nanobots
        .filter { bot -> distancePos(bot.pos, largestRadiusBot.pos) <= largestRadiusBot.radius }
        .count()
        .let { println("part1: $it") }

    val ps: Set<Pos> = nanobots
        .mapIndexed { i, bot ->
            nanobots
                .drop(i + 1)
                .filter { bot2 -> distancePos(bot.pos, bot2.pos) - (bot.radius + bot2.radius) == 0 }
                .flatMap { bot2 -> overlappingNanobot(bot, bot2) }
        }
        .flatten()
        .toSet()

    ps
        .map { pos ->
            Pair(pos,
                nanobots
                    .map { bot -> if (posInRange(bot, pos)) 1 else 0 }
                    .sum()
            )
        }
        .maxBy { it.second }!!
        .let { println("part2: ${distancePos(Pos(0, 0, 0), it.first)}") }
}
