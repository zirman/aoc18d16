import java.io.File
import java.io.InputStream
import java.lang.Exception

typealias Parser<A> = (String, Int) -> ParseResult<A>

fun <A> Parser<A>.parse(s: String) = this(s, 0)

sealed class ParseResult<out A> {
    class OK<out A>(val index: Int, val value: A) : ParseResult<A>()
    class Error(val index: Int) : ParseResult<Nothing>()
}

fun <A, B> Parser<A>.andThen(f: (A) -> Parser<B>): Parser<B> =
    fun(source: String, index: Int): ParseResult<B> {
        val result = this(source, index)

        return when (result) {
            is ParseResult.OK -> f(result.value).invoke(source, result.index)
            is ParseResult.Error -> result
        }
    }

fun parseString(str: String): Parser<String> =
    fun(source: String, index: Int): ParseResult<String> {
        for (j in 0 until str.length) {
            val k = index + j

            if (k >= source.length) {
                return ParseResult.Error(k)
            }

            if (source[k] != str[j]) {
                return ParseResult.Error(k)
            }
        }

        return ParseResult.OK(index + str.length, str)
    }

fun <A> Parser<A>.zeroOrMoreTimes(): Parser<List<A>> =
    fun(source: String, index: Int): ParseResult<List<A>> {
        val results = mutableListOf<A>()
        var lastIndex = index

        loop@ while (true) {
            val result = this(source, lastIndex)

            when (result) {
                is ParseResult.OK -> {
                    results.add(result.value)
                    lastIndex = result.index
                }

                is ParseResult.Error -> {
                    break@loop
                }
            }
        }

        return ParseResult.OK(lastIndex, results)
    }

fun <A> Parser<A>.oneOrMoreTimes(): Parser<List<A>> =
    fun(source: String, index: Int): ParseResult<List<A>> {
        val firstResult = this(source, index)

        if (firstResult !is ParseResult.OK) {
            firstResult as ParseResult.Error
            return ParseResult.Error(firstResult.index)
        }

        val results = mutableListOf(firstResult.value)
        var lastIndex = firstResult.index

        loop@ while (true) {
            val result = this(source, lastIndex)

            when (result) {
                is ParseResult.OK -> {
                    results.add(result.value)
                    lastIndex = result.index
                }

                is ParseResult.Error -> {
                    break@loop
                }
            }
        }

        return ParseResult.OK(lastIndex, results)
    }

fun parseOneOfChars(chars: String): Parser<Char> =
    fun(source: String, index: Int): ParseResult<Char> {
        if (index >= source.length) {
            return ParseResult.Error(index)
        }

        val c = source[index]

        return when {
            chars.contains(c) -> ParseResult.OK(index + 1, c)
            else -> ParseResult.Error(index)
        }
    }

fun parseChar(char: Char): Parser<Char> =
    fun(source: String, index: Int): ParseResult<Char> =
        when {
            index >= source.length -> ParseResult.Error(index)
            source[index] == char -> ParseResult.OK(index + 1, char)
            else -> ParseResult.Error(index)
        }

fun <A> A.parseLift(): Parser<A> =
    fun(_: String, index: Int): ParseResult<A> = ParseResult.OK(index, this)

fun <A, B> Parser<A>.apLeft(nextParser: Parser<B>) =
    fun(source: String, index: Int): ParseResult<A> {
        val result = this(source, index)

        return when (result) {
            is ParseResult.OK -> {
                val nextResult = nextParser(source, result.index)

                when (nextResult) {
                    is ParseResult.OK -> ParseResult.OK(nextResult.index, result.value)
                    is ParseResult.Error -> ParseResult.Error(nextResult.index)
                }
            }

            is ParseResult.Error ->
                result
        }
    }

fun readFile(filename: String): String {
    val inputStream: InputStream = File(filename).inputStream()

    val inputString = inputStream
        .bufferedReader()
        .use { bufferedReader -> bufferedReader.readText() }

    inputStream.close()

    return inputString
}

data class Registers(val r0: Int, val r1: Int, val r2: Int, val r3: Int) {
    operator fun get(i: Int): Int =
        when (i) {
            0 -> r0
            1 -> r1
            2 -> r2
            3 -> r3
            else -> throw Exception("Invalid register $i")
        }

    fun set(i: Int, v: Int): Registers =
        when (i) {
            0 -> copy(r0 = v)
            1 -> copy(r1 = v)
            2 -> copy(r2 = v)
            3 -> copy(r3 = v)
            else -> throw Exception("Invalid register $i")
        }
}

data class Operation(val opCode: Int, val in1: Int, val in2: Int, val out: Int)
data class ExampleOp(val before: Registers, val operation: Operation, val after: Registers)
typealias Op = (Registers, Int, Int, Int) -> Registers

val parseSpace: Parser<Char> = parseChar(' ')
val parseEndLine: Parser<Char> = parseChar('\n')
val parseSpaces: Parser<Char> = parseOneOfChars(" \t")
val parseDigits: Parser<List<Char>> = parseOneOfChars("0123456789").oneOrMoreTimes()

val parsePosNum: Parser<Int> =
    parseDigits.andThen { digits -> digits.joinToString("").toInt().parseLift() }

val parseCommaSpaces: Parser<Char> = parseChar(',').apLeft(parseSpaces)

var parseRegisters: Parser<Registers> =
    parseChar('[')
        .andThen { parsePosNum }
        .andThen { r0 ->
            parseCommaSpaces
                .andThen { parsePosNum }
                .andThen { r1 ->
                    parseCommaSpaces
                        .andThen { parsePosNum }
                        .andThen { r2 ->
                            parseCommaSpaces
                                .andThen { parsePosNum }
                                .andThen { r3 ->
                                    parseChar(']')
                                        .andThen { Registers(r0, r1, r2, r3).parseLift() }
                                }
                        }
                }
        }

val parseBefore: Parser<Registers> =
    parseString("Before: ")
        .andThen { parseRegisters }
        .apLeft(parseEndLine)

val parseOperation: Parser<Operation> =
    parsePosNum
        .andThen { opCode ->
            parseSpace
                .andThen { parsePosNum }
                .andThen { in1 ->
                    parseSpace
                        .andThen { parsePosNum }
                        .andThen { in2 ->
                            parseSpace
                                .andThen { parsePosNum }
                                .andThen { out ->
                                    Operation(opCode, in1, in2, out).parseLift()
                                }
                        }
                }
        }
        .apLeft(parseEndLine)

val parseAfter: Parser<Registers> =
    parseString("After:  ")
        .andThen { parseRegisters }
        .apLeft(parseEndLine)

val parseExampleOp: Parser<ExampleOp> =
    parseBefore
        .andThen { before ->
            parseOperation
                .andThen { operation ->
                    parseAfter
                        .andThen { after ->
                            ExampleOp(before, operation, after).parseLift()
                        }
                }
        }
        .apLeft(parseEndLine)

val parseFile: Parser<Pair<List<ExampleOp>, List<Operation>>> =
    parseExampleOp
        .zeroOrMoreTimes()
        .andThen { exampleOps ->
            parseEndLine
                .apLeft(parseEndLine)
                .andThen { parseOperation.zeroOrMoreTimes() }
                .andThen { operations ->
                    Pair(exampleOps, operations).parseLift()
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

fun main() {
    val file = readFile("day16.txt")

    val result = parseFile.parse(file)
    result as ParseResult.OK

    val (exampleOps, operations) = result.value

    val ops = listOf(
        ::addr,
        ::addi,
        ::mulr,
        ::muli,
        ::banr,
        ::bani,
        ::borr,
        ::bori,
        ::setr,
        ::seti,
        ::gtir,
        ::gtri,
        ::gtrr,
        ::eqir,
        ::eqri,
        ::eqrr
    )

    exampleOps
        .map { exampleOp ->
            ops
                .filter { op ->
                    exampleOp.after == op(
                        exampleOp.before,
                        exampleOp.operation.in1,
                        exampleOp.operation.in2,
                        exampleOp.operation.out
                    )
                }
                .count()
        }
        .filter { numMatching -> numMatching >= 3 }
        .count()
        .let { x -> println("part1: $x") }

    val examplesByOpCode: Map<Int, List<ExampleOp>> = exampleOps.groupBy { it.operation.opCode }

    val possibleMapping: Set<Pair<Int, Op>> = ops
        .flatMap { op ->
            examplesByOpCode.entries
                .flatMap { (opCode, examples) ->
                    if (examples.all { example ->
                            example.after == op(
                                example.before,
                                example.operation.in1,
                                example.operation.in2,
                                example.operation.out
                            )
                        }) {
                        listOf(Pair(opCode, op))
                    } else {
                        emptyList()
                    }
                }
        }
        .toSet()

    fun testOp(op: Op): (Map<Int, Op>, List<Int>) -> List<Pair<Map<Int, Op>, List<Int>>> =
        fun(
            opToOpCode: Map<Int, Op>,
            opCodesRemaining: List<Int>
        ): List<Pair<Map<Int, Op>, List<Int>>> =
            opCodesRemaining
                .filter { opCode -> possibleMapping.contains(Pair(opCode, op)) }
                .map { opCode ->
                    Pair(opToOpCode.plus(Pair(opCode, op)), opCodesRemaining.minus(opCode))
                }

    fun ((Map<Int, Op>, List<Int>) -> List<Pair<Map<Int, Op>, List<Int>>>).apNext(
        f: (Map<Int, Op>, List<Int>) -> List<Pair<Map<Int, Op>, List<Int>>>
    ): (Map<Int, Op>, List<Int>) -> List<Pair<Map<Int, Op>, List<Int>>> =
        fun(m: Map<Int, Op>, o: List<Int>): List<Pair<Map<Int, Op>, List<Int>>> =
            this(m, o).flatMap { (m1, o1) -> f(m1, o1) }

    testOp(::addi)
        .apNext(testOp(::addr))
        .apNext(testOp(::muli))
        .apNext(testOp(::mulr))
        .apNext(testOp(::banr))
        .apNext(testOp(::bani))
        .apNext(testOp(::borr))
        .apNext(testOp(::bori))
        .apNext(testOp(::setr))
        .apNext(testOp(::seti))
        .apNext(testOp(::gtir))
        .apNext(testOp(::gtri))
        .apNext(testOp(::gtrr))
        .apNext(testOp(::eqir))
        .apNext(testOp(::eqri))
        .apNext(testOp(::eqrr))
        .invoke(emptyMap(), (0..15).toList())
        .forEach { (mapping, _) ->
            operations
                .fold(Registers(0, 0, 0, 0)) { registers, operation ->
                    mapping[operation.opCode]!!(
                        registers,
                        operation.in1,
                        operation.in2,
                        operation.out
                    )
                }
                .r0
                .let { r0 -> println("part2: $r0") }
        }
}
