import java.io.File
import java.io.InputStream
import java.lang.Exception

//fun <A> Parser<A>.orElse(p: Parser<A>): Parser<A> =
//    fun(source: String, index: Int): ParseResult<A> {
//        val result = this.invoke(source, index)
//
//        return when (result) {
//            is ParseResult.OK -> result
//            is ParseResult.Error -> {
//                val result2 = p(source, index)
//
//                when (result2) {
//                    is ParseResult.OK -> result2
//                    is ParseResult.Error -> ParseResult.Error(index) //, "source does not match orElse"
//                }
//            }
//        }
//    }

//fun <A> parseOneOf(vararg p: Parser<A>): Parser<A> =
//    fun(s: String, i: Int): ParseResult<A> {
//        val result = this.invoke(s, i)
//
//        return when (result) {
//            is ParseResult.OK -> result
//            is ParseResult.Error -> {
//                val result2 = p(s, i)
//
//                when (result2) {
//                    is ParseResult.OK -> result2
//                    is ParseResult.Error -> ParseResult.Error(i, "source does not match orElse")
//                }
//            }
//        }
//    }

//fun parseNotChar(char: Char): Parser<Char> =
//    fun(source: String, index: Int): ParseResult<Char> =
//        when {
//            index >= source.length -> ParseResult.Error(index)
//            source[index] != char -> ParseResult.OK(index + 1, source[index])
//            else -> ParseResult.Error(index)
//        }

//val parseNum: Parser<Int> = parseChar('-')
//    .optionally()
//    .andThen { sign ->
//        parseOneOfChars("0123456789")
//            .oneOrMoreTimes()
//            .andThen { digits ->
//                ((sign?.toString() ?: "") + digits.joinToString("")).toInt().parseLift()
//            }
//    }


//fun <A : Any> Parser<A>.optionally(): Parser<A?> =
//    fun(source: String, index: Int): ParseResult<A?> {
//        if (index >= source.length) {
//            return ParseResult.Error(index)
//        }
//
//        val result = this(source, index)
//
//        return when (result) {
//            is ParseResult.OK -> result
//            is ParseResult.Error -> ParseResult.OK(result.index, null)
//        }
//    }


//    val parseNum: Parser<Int> = parseChar('-')
//        .optionally()
//        .andThen { sign ->
//            parseOneOfChars("0123456789")
//                .oneOrMoreTimes()
//                .andThen { digits ->
//                    ((sign?.toString() ?: "") + digits.joinToString("")).toInt().parseLift()
//                }
//        }
//
//    val parseString: Parser<String> = parseChar('"')
//        .andThen { parseOneOfChars("abcdefghijklmnopqrstuvwxyz").zeroOrMoreTimes() }
//        .andThen { str -> parseChar('"').andThen { str.joinToString("").parseLift() } }
//
//    val result = parseString.parse(""""hello"""")

//    when (result) {
//        is ParseResult.OK ->
//            println(result.value)
//    }

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
        .use { it.readText() }

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

    fun testOp(op: Op): (Map<Int, Op>, List<Int>) -> List<Pair<Map<Int, Op>, List<Int>>> =
        fun(
            opToOpCode: Map<Int, Op>,
            opCodesRemaining: List<Int>
        ): List<Pair<Map<Int, Op>, List<Int>>> {
            return opCodesRemaining
                .filter { opCode ->
                    exampleOps
                        .filter { it.operation.opCode == opCode }
                        .all { exampleOp ->
                            exampleOp.after == op(
                                exampleOp.before,
                                exampleOp.operation.in1,
                                exampleOp.operation.in2,
                                exampleOp.operation.out
                            )
                        }
                }
                .map { opCode ->
                    Pair(opToOpCode.plus(Pair(opCode, op)), opCodesRemaining.minus(opCode))
                }
        }

    fun ((Map<Int, Op>, List<Int>) -> List<Pair<Map<Int, Op>, List<Int>>>).flatMap(
        f: (Map<Int, Op>, List<Int>) -> List<Pair<Map<Int, Op>, List<Int>>>
    ): (Map<Int, Op>, List<Int>) -> List<Pair<Map<Int, Op>, List<Int>>> =
        fun(m: Map<Int, Op>, o: List<Int>): List<Pair<Map<Int, Op>, List<Int>>> =
            this(m, o).flatMap { (m1, o1) -> f(m1, o1) }

    testOp(::addi)
        .flatMap(testOp(::addr))
        .flatMap(testOp(::muli))
        .flatMap(testOp(::mulr))
        .flatMap(testOp(::banr))
        .flatMap(testOp(::bani))
        .flatMap(testOp(::borr))
        .flatMap(testOp(::bori))
        .flatMap(testOp(::setr))
        .flatMap(testOp(::seti))
        .flatMap(testOp(::gtir))
        .flatMap(testOp(::gtri))
        .flatMap(testOp(::gtrr))
        .flatMap(testOp(::eqir))
        .flatMap(testOp(::eqri))
        .flatMap(testOp(::eqrr))
        .invoke(emptyMap(), listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15))
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
                .let { println(it) }
        }
}
