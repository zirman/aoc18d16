package com.aoc18.day24

import com.aoc18.day16.parseEndLine
import com.aoc18.parser.ParseResult
import com.aoc18.parser.Parser
import com.aoc18.parser.andThen
import com.aoc18.parser.becomes
import com.aoc18.parser.keepNext
import com.aoc18.parser.keepPrevious
import com.aoc18.parser.map
import com.aoc18.parser.ofThese
import com.aoc18.parser.oneOrMoreTimes
import com.aoc18.parser.parse
import com.aoc18.parser.parseChar
import com.aoc18.parser.parsePosInt
import com.aoc18.parser.parseString
import com.aoc18.parser.zeroOrMoreTimes
import com.aoc18.parser.zeroOrOneTime
import com.aoc18.utils.readFile

enum class DamageType {
    Cold,
    Fire,
    Bludgeon,
    Radiation,
    Slash,
}

data class ArmyUnit(
    var army: Army,
    var unitId: Int,
    var size: Int,
    var hitPoints: Int,
    var weaknesses: List<DamageType>,
    var immunities: List<DamageType>,
    var attackDamage: Int,
    var damageType: DamageType,
    var initiative: Int,
    var isDefending: Boolean,
    var attackingUnitId: Int?
)

sealed class Army {
    object ImmuneSystem : Army()
    object Infection : Army()
}

sealed class DamageModifier {
    data class Immunity(val damageTypes: List<DamageType>) : DamageModifier()
    data class Weakness(val damageTypes: List<DamageType>) : DamageModifier()
}

val parseDamageType: Parser<DamageType> =
    listOf(
        parseString("cold").becomes(DamageType.Cold),
        parseString("fire").becomes(DamageType.Fire),
        parseString("bludgeoning").becomes(DamageType.Bludgeon),
        parseString("radiation").becomes(DamageType.Radiation),
        parseString("slashing").becomes(DamageType.Slash)
    )
        .ofThese()

val parseDamageModifiers: Parser<Pair<List<DamageType>, List<DamageType>>> =
    parseChar('(')
        .keepNext(
            listOf(
                parseString("immune to ")
                    .keepNext(parseDamageType.oneOrMoreTimes(parseString(", ")))
                    .map(DamageModifier::Immunity),
                parseString("weak to ")
                    .keepNext(parseDamageType.oneOrMoreTimes(parseString(", ")))
                    .map(DamageModifier::Weakness)
            )
                .ofThese()
                .oneOrMoreTimes(parseString("; "))
        )
        .keepPrevious(parseString(") "))
        .zeroOrOneTime()
        .map { damageModifiers ->
            if (damageModifiers != null) {
                val weaknessModifiers: List<DamageModifier.Weakness> =
                    damageModifiers.flatMap { if (it is DamageModifier.Weakness) listOf(it) else emptyList() }

                val immunityModifiers: List<DamageModifier.Immunity> =
                    damageModifiers.flatMap { if (it is DamageModifier.Immunity) listOf(it) else emptyList() }

                Pair(
                    weaknessModifiers.flatMap { it.damageTypes },
                    immunityModifiers.flatMap { it.damageTypes })
            } else {
                Pair(emptyList(), emptyList())
            }
        }

fun parseArmyUnit(army: Army): Parser<ArmyUnit> =
    parsePosInt
        .andThen { size ->
            parseString(" units each with ")
                .keepNext(parsePosInt)
                .andThen { hitPoints ->
                    parseString(" hit points ")
                        .keepNext(parseDamageModifiers)
                        .andThen { (weaknesses, immunities) ->
                            parseString("with an attack that does ")
                                .keepNext(parsePosInt)
                                .andThen { attackDamage ->
                                    parseChar(' ')
                                        .keepNext(parseDamageType)
                                        .andThen { damageType ->
                                            parseString(" damage at initiative ")
                                                .keepNext(parsePosInt)
                                                .map { initiative ->
                                                    ArmyUnit(
                                                        army = army,
                                                        unitId = 0,
                                                        size = size,
                                                        hitPoints = hitPoints,
                                                        weaknesses = weaknesses,
                                                        immunities = immunities,
                                                        attackDamage = attackDamage,
                                                        damageType = damageType,
                                                        initiative = initiative,
                                                        isDefending = false,
                                                        attackingUnitId = null
                                                    )
                                                }
                                        }
                                }
                        }
                }
        }
        .keepPrevious(parseEndLine)

val parseImmuneSystem: Parser<List<ArmyUnit>> =
    parseString("Immune System:\n").keepNext(parseArmyUnit(Army.ImmuneSystem).zeroOrMoreTimes())

val parseInfection: Parser<List<ArmyUnit>> =
    parseString("Infection:\n").keepNext(parseArmyUnit(Army.Infection).zeroOrMoreTimes())

val parseBiology: Parser<Pair<List<ArmyUnit>, List<ArmyUnit>>> =
    parseImmuneSystem
        .andThen { immuneSystem ->
            parseEndLine
                .keepNext(parseInfection)
                .map { infection -> Pair(immuneSystem, infection) }
        }

fun effectivePower(armyUnit: ArmyUnit): Int = armyUnit.size * armyUnit.attackDamage

fun effectiveDamage(defendingArmyUnit: ArmyUnit, attackingArmyUnit: ArmyUnit): Int =
    when {
        defendingArmyUnit.immunities.contains(attackingArmyUnit.damageType) ->
            0

        defendingArmyUnit.weaknesses.contains(attackingArmyUnit.damageType) ->
            effectivePower(attackingArmyUnit) * 2

        else ->
            effectivePower(attackingArmyUnit)
    }

fun runSimuation(
    initialImmuneSystem: List<ArmyUnit>,
    initialInfection: List<ArmyUnit>,
    boost: Int
): Pair<Array<ArmyUnit>, Array<ArmyUnit>> {
    val precedence = Comparator<ArmyUnit> { a, b ->
        val diff = effectivePower(b) - effectivePower(a)
        if (diff != 0) diff else b.initiative - a.initiative
    }

    fun resetArmyUnit(unitId: Int, armyUnit: ArmyUnit) {
        armyUnit.unitId = unitId
        armyUnit.isDefending = false
        armyUnit.attackingUnitId = null
    }

    val immuneSystemArmyUnits = initialImmuneSystem
        .map { it.copy(attackDamage = it.attackDamage + boost) }
        .toTypedArray()

    val infectionArmyUnits = initialInfection.map { it.copy() }.toTypedArray()

    fun allArmyUnits(): List<ArmyUnit> =
        listOf(immuneSystemArmyUnits.toList(), infectionArmyUnits.toList()).flatten()

    fun enemyArmyUnits(armyUnit: ArmyUnit): Array<ArmyUnit> =
        when (armyUnit.army) {
            Army.ImmuneSystem -> infectionArmyUnits
            Army.Infection -> immuneSystemArmyUnits
        }

    while (immuneSystemArmyUnits.any { it.size > 0 } && infectionArmyUnits.any { it.size > 0 }) {
        val immuneSize = immuneSystemArmyUnits.sumBy { it.size }
        val infectionSize = infectionArmyUnits.sumBy { it.size }

        immuneSystemArmyUnits.forEachIndexed(::resetArmyUnit)
        infectionArmyUnits.forEachIndexed(::resetArmyUnit)

        for (attackingArmyUnit in allArmyUnits().filter { it.size > 0 }.sortedWith(precedence)) {
            enemyArmyUnits(attackingArmyUnit)
                .filter { it.size > 0 && it.isDefending.not() }
                .minWith(Comparator { a, b ->
                    val diff =
                        effectiveDamage(b, attackingArmyUnit) - effectiveDamage(
                            a,
                            attackingArmyUnit
                        )

                    if (diff != 0) diff else precedence.compare(a, b)
                })
                ?.let { target ->
                    if (effectiveDamage(target, attackingArmyUnit) > 0) {
                        attackingArmyUnit.attackingUnitId = target.unitId
                        target.isDefending = true
                    }
                }
        }

        for (attackingArmyUnit in allArmyUnits().sortedBy { -it.initiative }) {
            if (attackingArmyUnit.size > 0) {
                attackingArmyUnit.attackingUnitId
                    ?.let { unitId ->
                        val target = enemyArmyUnits(attackingArmyUnit)[unitId]

                        target.size = Math.max(
                            target.size - (effectiveDamage(
                                target,
                                attackingArmyUnit
                            ) / target.hitPoints),
                            0
                        )
                    }
            }
        }

        if (immuneSize == immuneSystemArmyUnits.sumBy { it.size } &&
            infectionSize == infectionArmyUnits.sumBy { it.size }) {
            break
        }
    }

    return Pair(immuneSystemArmyUnits, infectionArmyUnits)
}

fun main() {
    val file = readFile("day24.txt")
    val result = parseBiology.parse(file)
    result as ParseResult.OK
    val (initialImmuneSystem, initialInfection) = result.value

    run {
        val (_, infectionArmyUnits) = runSimuation(initialImmuneSystem, initialInfection, 0)
        println("part1: ${infectionArmyUnits.sumBy { it.size }}")
    }

    var boost = 1

    while (true) {
        val (immuneSystemArmyUnits, infectionArmyUnits) = runSimuation(
            initialImmuneSystem,
            initialInfection,
            boost
        )

        if (infectionArmyUnits.sumBy { it.size } == 0) {
            println("part2: ${immuneSystemArmyUnits.sumBy { it.size }}")
            break
        }

        boost += 1
    }
}
