package com.aoc18.day19

import com.aoc18.day16.parseEndLine
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
import com.aoc18.parser.parsePosLong
import com.aoc18.parser.parseString
import com.aoc18.utils.readFile

data class Registers(val r0: Long, val r1: Long, val r2: Long, val r3: Long, val r4: Long, val r5: Long) {
    operator fun get(i: Long): Long =
        when (i) {
            0L -> r0
            1L -> r1
            2L -> r2
            3L -> r3
            4L -> r4
            5L -> r5
            else -> throw Exception("Invalid register $i")
        }

    fun set(i: Long, v: Long): Registers =
        when (i) {
            0L -> copy(r0 = v)
            1L -> copy(r1 = v)
            2L -> copy(r2 = v)
            3L -> copy(r3 = v)
            4L -> copy(r4 = v)
            5L -> copy(r5 = v)
            else -> throw Exception("Invalid register $i")
        }
}

fun addr(registers: Registers, in1: Long, in2: Long, out: Long): Registers =
    registers.set(out, registers[in1] + registers[in2])

fun addi(registers: Registers, in1: Long, in2: Long, out: Long): Registers =
    registers.set(out, registers[in1] + in2)

fun mulr(registers: Registers, in1: Long, in2: Long, out: Long): Registers =
    registers.set(out, registers[in1] * registers[in2])

fun muli(registers: Registers, in1: Long, in2: Long, out: Long): Registers =
    registers.set(out, registers[in1] * in2)

fun banr(registers: Registers, in1: Long, in2: Long, out: Long): Registers =
    registers.set(out, registers[in1] and registers[in2])

fun bani(registers: Registers, in1: Long, in2: Long, out: Long): Registers =
    registers.set(out, registers[in1] and in2)

fun borr(registers: Registers, in1: Long, in2: Long, out: Long): Registers =
    registers.set(out, registers[in1] or registers[in2])

fun bori(registers: Registers, in1: Long, in2: Long, out: Long): Registers =
    registers.set(out, registers[in1] or in2)

fun setr(registers: Registers, in1: Long, in2: Long, out: Long): Registers =
    registers.set(out, registers[in1])

fun seti(registers: Registers, in1: Long, in2: Long, out: Long): Registers =
    registers.set(out, in1)

fun gtir(registers: Registers, in1: Long, in2: Long, out: Long): Registers =
    registers.set(out, if (in1 > registers[in2]) 1 else 0)

fun gtri(registers: Registers, in1: Long, in2: Long, out: Long): Registers =
    registers.set(out, if (registers[in1] > in2) 1 else 0)

fun gtrr(registers: Registers, in1: Long, in2: Long, out: Long): Registers =
    registers.set(out, if (registers[in1] > registers[in2]) 1 else 0)

fun eqir(registers: Registers, in1: Long, in2: Long, out: Long): Registers =
    registers.set(out, if (in1 == registers[in2]) 1 else 0)

fun eqri(registers: Registers, in1: Long, in2: Long, out: Long): Registers =
    registers.set(out, if (registers[in1] == in2) 1 else 0)

fun eqrr(registers: Registers, in1: Long, in2: Long, out: Long): Registers =
    registers.set(out, if (registers[in1] == registers[in2]) 1 else 0)

enum class Operation(val f: (Registers, Long, Long, Long) -> Registers) {
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

data class Instruction(val operation: Operation, val in1: Long, val in2: Long, val out: Long)
data class File(val instructionPointer: Long, val instructions: List<Instruction>)

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
                .andThen { parsePosLong }
                .andThen { in1 ->
                    parseSpace
                        .andThen { parsePosLong }
                        .andThen { in2 ->
                            parseSpace
                                .andThen { parsePosLong }
                                .map { out -> Instruction(op, in1, in2, out) }
                        }
                }
        }
        .keepPrevious(parseEndLine)

val parseFile: Parser<Pair<Long, List<Instruction>>> =
    parseString("#ip ")
        .andThen { parsePosLong }
        .keepPrevious(parseEndLine)
        .andThen { instructionPointer ->
            parseInstruction
                .oneOrMoreTimes()
                .map { instructions -> Pair(instructionPointer, instructions) }
        }

fun makeProgram(
    ipRegister: Long,
    program: Array<Instruction>
): (Registers) -> Registers? =
    fun(registers: Registers): Registers? {
        if (registers[ipRegister] >= program.size) return null
        val ip = registers[ipRegister]
        val instruction = program[ip.toInt()]

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

    instructionList.forEach { println(it) }
    val program = makeProgram(ipRegister, instructions)

//    var machine = Registers(r0=31, r1=30000000, r2=10551427, r3=10551425, r4=1, r5=13)
//    var machine = Registers(r0=6, r1=25, r2=180613, r3=10551425, r4=0, r5=9)
//    var machine = Registers(r0=472, r1=10550000, r2=10551427, r3=10551425, r4=1, r5=12)
    var machine = Registers(r0=1, r1=0, r2=0, r3=0, r4=0, r5=0)
    var i = 0

    var r0 = machine.r0
    var r1 = machine.r1
    var r4 = machine.r4

    while (true) {
        val m1 = program(machine) ?: break
        i += 1

        if (r0 != m1.r0 || r1 != m1.r1) {
            r0 = m1.r0
            r1 = m1.r1
            println("$machine $m1")
        }

//        if (r4 < m1.r4) {
//            println("$machine $m1 --")
//            r4 = m1.r4
//        }

        machine = m1
    }

    println(machine)
}
