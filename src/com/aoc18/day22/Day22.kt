package com.aoc18.day22

import com.aoc18.day21.eqir
import java.lang.Exception

enum class CaveTile {
    Rocky,
    Narrow,
    Wet
}

enum class Equipment {
    ClimbingGear,
    Torch,
    Neither
}

data class Pos(val x: Int, val y: Int)

fun erosionLevel(depth: Int, target: Pos, pos: Pos): Int =
    (geologicIndex(depth, target, pos) + depth) % 20183

val geologicIndex = run {
    fun geologicIndex(depth: Int, target: Pos, pos: Pos): Int =
        when {
            pos.x == 0 && pos.y == 0 -> 0
            pos == target -> 0
            pos.y == 0 -> 16807 * pos.x
            pos.x == 0 -> 48271 * pos.y
            else ->
                erosionLevel(depth, target, Pos(pos.x - 1, pos.y)) *
                    erosionLevel(depth, target, Pos(pos.x, pos.y - 1))
        }

    val table: MutableMap<Triple<Int, Pos, Pos>, Int> = mutableMapOf()

    fun(depth: Int, target: Pos, pos: Pos): Int =
        table.getOrPut(Triple(depth, target, pos)) { geologicIndex(depth, target, pos) }
}

fun caveTile(depth: Int, target: Pos, pos: Pos): CaveTile =
    when (erosionLevel(depth, target, pos) % 3) {
        0 -> CaveTile.Rocky
        1 -> CaveTile.Wet
        2 -> CaveTile.Narrow
        else -> throw Exception("Mod 3 out of expected range")
    }

fun riskLevel(caveTile: CaveTile): Int =
    when (caveTile) {
        CaveTile.Rocky -> 0
        CaveTile.Narrow -> 2
        CaveTile.Wet -> 1
    }

fun canUse(caveTile: CaveTile, equipment: Equipment): Unit? =
    when (caveTile) {
        CaveTile.Rocky -> if (equipment == Equipment.ClimbingGear || equipment == Equipment.Torch) Unit else null
        CaveTile.Narrow -> if (equipment == Equipment.Neither || equipment == Equipment.Torch) Unit else null
        CaveTile.Wet -> if (equipment == Equipment.ClimbingGear || equipment == Equipment.Neither) Unit else null
    }


//fun switchEquipment(equipment: Equipment, tile: CaveTile): List<Triple<Equipment, Pos, Int>> =
//    when (tile) {
//        CaveTile.Rocky -> when (equipment) {
//            Equipment.ClimbingGear -> listOf(
//                Triple(Equipment.ClimbingGear, pos, 0),
//                Triple(Equipment.Torch, pos, 7)
//            )
//            Equipment.Torch -> listOf(
//                Triple(Equipment.ClimbingGear, pos, 7),
//                Triple(Equipment.Torch, pos, 0)
//            )
//        }
//        CaveTile.Narrow -> when (equipment) {
//            Equipment.ClimbingGear -> listOf(
//                Triple(Equipment.ClimbingGear, pos, 0),
//                Triple(Equipment.Torch, pos, 7),
//                Triple(Equipment.Neither, pos, 7)
//            )
//            Equipment.Torch -> listOf(
//                Triple(Equipment.ClimbingGear, pos, 7),
//                Triple(Equipment.Torch, pos, 0),
//                Triple(Equipment.Neither, pos, 7)
//            )
//            Equipment.Neither -> listOf(
//                Triple(Equipment.ClimbingGear, pos, 7),
//                Triple(Equipment.Torch, pos, 7),
//                Triple(Equipment.Neither, pos, 0)
//            )
//        }
//        CaveTile.Wet -> when (equipment) {
//            Equipment.ClimbingGear -> listOf(
//                Triple(Equipment.ClimbingGear, pos, 0),
//                Triple(Equipment.Torch, pos, 7),
//                Triple(Equipment.Neither, pos, 7)
//            )
//            Equipment.Torch -> listOf(
//                Triple(Equipment.ClimbingGear, pos, 7),
//                Triple(Equipment.Torch, pos, 0),
//                Triple(Equipment.Neither, pos, 7)
//            )
//            Equipment.Neither -> listOf(
//                Triple(Equipment.ClimbingGear, pos, 7),
//                Triple(Equipment.Torch, pos, 7),
//                Triple(Equipment.Neither, pos, 0)
//            )
//        }
//    }

fun switchEquipment(equipment: Equipment, caveTile: CaveTile): Equipment =
    when (caveTile) {
        CaveTile.Rocky -> when (equipment) {
            Equipment.ClimbingGear -> Equipment.Torch
            Equipment.Torch -> Equipment.ClimbingGear
            Equipment.Neither -> throw Exception("Should not happen")
        }
        CaveTile.Narrow -> when (equipment) {
            Equipment.ClimbingGear -> throw Exception("Should not happen")
            Equipment.Torch -> Equipment.Neither
            Equipment.Neither -> Equipment.Torch
        }
        CaveTile.Wet -> when (equipment) {
            Equipment.ClimbingGear -> Equipment.Neither
            Equipment.Torch -> throw Exception("Should not happen")
            Equipment.Neither -> Equipment.ClimbingGear
        }
    }

fun validPos(pos: Pos): Unit? = if (pos.x >= 0 && pos.y >= 0) Unit else null

fun search(depth: Int, target: Pos, mouth: Pos): Int {
    val reachedPositions: MutableMap<Pair<Equipment, Pos>, Int> = mutableMapOf()

    tailrec fun search(ps: Set<Triple<Equipment, Pos, Int>>, t: Int): Int {
        if (reachedPositions[Pair(Equipment.Torch, target)]?.let { t >= it } == true) {
            return t
        }

        return search(
            ps
                .map { (equipment, pos, countdown) ->
                    if (countdown == 0) {
                        fun move(pos: Pos): List<Triple<Equipment, Pos, Int>> =
                            validPos(pos)
                                ?.let { caveTile(depth, target, pos) }
                                ?.let { tile ->
                                    canUse(tile, equipment)
                                        ?.let {
                                            listOf(
                                                Triple(equipment, pos, 0),
                                                Triple(
                                                    switchEquipment(equipment, tile),
                                                    pos,
                                                    7
                                                )
                                            )
                                        }
                                }
                                ?: emptyList()


                        val additionalPs = listOf(
                            move(Pos(pos.x, pos.y - 1)),
                            move(Pos(pos.x, pos.y + 1)),
                            move(Pos(pos.x - 1, pos.y)),
                            move(Pos(pos.x + 1, pos.y))
                        )
                            .flatten()
                            .filter { (equipment, pos, countdown) ->
                                reachedPositions[Pair(equipment, pos)]
                                    ?.let { previousTime -> previousTime > t + countdown }
                                    ?: true
                            }

                        additionalPs
                            .forEach { (equipment, pos, countdown) ->
                                reachedPositions[Pair(equipment, pos)] = t + countdown
                            }

                        additionalPs
                    } else {
                        listOf(Triple(equipment, pos, countdown - 1))
                    }
                }
                .flatten()
                .toSet(),
            t + 1
        )
    }

    reachedPositions[Pair(Equipment.Torch, mouth)] = 0
    reachedPositions[Pair(Equipment.ClimbingGear, mouth)] = 7
    reachedPositions[Pair(Equipment.Neither, mouth)] = 7

    return search(
        setOf(
            Triple(Equipment.Torch, mouth, 0),
            Triple(Equipment.ClimbingGear, mouth, 7),
            Triple(Equipment.Neither, mouth, 7)
        ),
        0
    )
}

fun main() {
    val depth = 11739
    val target = Pos(11, 718)
    val mouth = Pos(0, 0)

    (mouth.x..target.x)
        .flatMap { x -> (mouth.y..target.y).map { y -> Pos(x, y) } }
        .map { pos -> riskLevel(caveTile(depth, target, pos)) }
        .sum()
        .let { println("part1: $it") }

    println("part2: ${search(depth, target, mouth)}")
}
