package com.aoc18.day19

import com.aoc18.day16.parseEndLine
import com.aoc18.day16.parsePosNum
import com.aoc18.day16.parseSpace
import com.aoc18.parser.ParseResult
import com.aoc18.parser.Parser
import com.aoc18.parser.andThen
import com.aoc18.parser.keepPrevious
import com.aoc18.parser.becomes
import com.aoc18.parser.map
import com.aoc18.parser.ofThese
import com.aoc18.parser.oneOrMoreTimes
import com.aoc18.parser.parse
import com.aoc18.parser.parseString
import com.aoc18.utils.readFile

data class Registers(val r0: Int, val r1: Int, val r2: Int, val r3: Int, val r4: Int, val r5: Int) {
    operator fun get(i: Int): Int =
        when (i) {
            0 -> r0
            1 -> r1
            2 -> r2
            3 -> r3
            4 -> r4
            5 -> r5
            else -> throw Exception("Invalid register $i")
        }

    fun set(i: Int, v: Int): Registers =
        when (i) {
            0 -> copy(r0 = v)
            1 -> copy(r1 = v)
            2 -> copy(r2 = v)
            3 -> copy(r3 = v)
            4 -> copy(r4 = v)
            5 -> copy(r5 = v)
            else -> throw Exception("Invalid register $i")
        }
}

fun addr(registers: Registers, in1: Int, in2: Int, out: Int): Registers =
    registers.set(out, registers[in1] + registers[in2])

fun addi(registers: Registers, in1: Int, in2: Int, out: Int): Registers =
    registers.set(out, registers[in1] + in2)

fun mulr(registers: Registers, in1: Int, in2: Int, out: Int): Registers =
    registers.set(out, registers[in1] * registers[in2])

fun muli(registers: Registers, in1: Int, in2: Int, out: Int): Registers =
    registers.set(out, registers[in1] * in2)

fun banr(registers: Registers, in1: Int, in2: Int, out: Int): Registers =
    registers.set(out, registers[in1] and registers[in2])

fun bani(registers: Registers, in1: Int, in2: Int, out: Int): Registers =
    registers.set(out, registers[in1] and in2)

fun borr(registers: Registers, in1: Int, in2: Int, out: Int): Registers =
    registers.set(out, registers[in1] or registers[in2])

fun bori(registers: Registers, in1: Int, in2: Int, out: Int): Registers =
    registers.set(out, registers[in1] or in2)

fun setr(registers: Registers, in1: Int, in2: Int, out: Int): Registers =
    registers.set(out, registers[in1])

fun seti(registers: Registers, in1: Int, in2: Int, out: Int): Registers =
    registers.set(out, in1)

fun gtir(registers: Registers, in1: Int, in2: Int, out: Int): Registers =
    registers.set(out, if (in1 > registers[in2]) 1 else 0)

fun gtri(registers: Registers, in1: Int, in2: Int, out: Int): Registers =
    registers.set(out, if (registers[in1] > in2) 1 else 0)

fun gtrr(registers: Registers, in1: Int, in2: Int, out: Int): Registers =
    registers.set(out, if (registers[in1] > registers[in2]) 1 else 0)

fun eqir(registers: Registers, in1: Int, in2: Int, out: Int): Registers =
    registers.set(out, if (in1 == registers[in2]) 1 else 0)

fun eqri(registers: Registers, in1: Int, in2: Int, out: Int): Registers =
    registers.set(out, if (registers[in1] == in2) 1 else 0)

fun eqrr(registers: Registers, in1: Int, in2: Int, out: Int): Registers =
    registers.set(out, if (registers[in1] == registers[in2]) 1 else 0)

enum class Operation(val f: (Registers, Int, Int, Int) -> Registers) {
    Addr(::addr),
    Addi(::addi),
    Mulr(::mulr),
    Muli(::muli),
    Banr(::banr),
    Bani(::bani),
    Borr(::borr),
    Bori(::bori),
    Setr(::setr),
    Seti(::seti),
    Gtir(::gtir),
    Gtri(::gtri),
    Gtrr(::gtrr),
    Eqir(::eqir),
    Eqri(::eqri),
    Eqrr(::eqrr),
}

data class Instruction(val operation: Operation, val in1: Int, val in2: Int, val out: Int)
data class File(val instructionPointer: Int, val instructions: List<Instruction>)

val parseInstruction: Parser<Instruction> =
    listOf(
        parseString("addr").becomes(Operation.Addr),
        parseString("addi").becomes(Operation.Addi),
        parseString("mulr").becomes(Operation.Mulr),
        parseString("muli").becomes(Operation.Muli),
        parseString("banr").becomes(Operation.Banr),
        parseString("bani").becomes(Operation.Bani),
        parseString("borr").becomes(Operation.Borr),
        parseString("bori").becomes(Operation.Bori),
        parseString("setr").becomes(Operation.Setr),
        parseString("seti").becomes(Operation.Seti),
        parseString("gtir").becomes(Operation.Gtir),
        parseString("gtri").becomes(Operation.Gtri),
        parseString("gtrr").becomes(Operation.Gtrr),
        parseString("eqir").becomes(Operation.Eqir),
        parseString("eqri").becomes(Operation.Eqri),
        parseString("eqrr").becomes(Operation.Eqrr)
    )
        .ofThese()
        .andThen { op ->
            parseSpace
                .andThen { parsePosNum }
                .andThen { in1 ->
                    parseSpace
                        .andThen { parsePosNum }
                        .andThen { in2 ->
                            parseSpace
                                .andThen { parsePosNum }
                                .map { out -> Instruction(op, in1, in2, out) }
                        }
                }
        }
        .keepPrevious(parseEndLine)

val parseFile: Parser<Pair<Int, List<Instruction>>> =
    parseString("#ip ")
        .andThen { parsePosNum }
        .keepPrevious(parseEndLine)
        .andThen { instructionPointer ->
            parseInstruction
                .oneOrMoreTimes()
                .map { instructions -> Pair(instructionPointer, instructions) }
        }

fun makeProgram(
    ipRegister: Int,
    program: Array<Instruction>
): (Registers) -> Registers? =
    fun(registers: Registers): Registers? {
        if (registers[ipRegister] >= program.size) return null
        val ip = registers[ipRegister]
        val instruction = program[ip]

        val nextRegisters = instruction.operation.f(
            registers,
            instruction.in1,
            instruction.in2,
            instruction.out
        )

        return nextRegisters.set(ipRegister, nextRegisters[ipRegister] + 1)
    }

fun main() {
    val file = readFile("day19.txt")

    val result = parseFile.parse(file)
    result as ParseResult.OK
    val (ipRegister, instructionList) = result.value
    val instructions = instructionList.toTypedArray()
    val program = makeProgram(ipRegister, instructions)

    var machine = Registers(r0 = 1, r1 = 0, r2 = 0, r3 = 0, r4 = 0, r5 = 0)
    var i = 0

    while (true) {
        val m1 = program(machine) ?: break
        i += 1
//        if (i > 100) break
        if (i % 100000000 == 0)
            println(m1)
        machine = m1
    }

    println(machine)
}
