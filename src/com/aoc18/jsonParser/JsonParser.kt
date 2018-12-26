package com.aoc18.jsonParser

import com.aoc18.parser.MutIndex
import com.aoc18.parser.Parser
import com.aoc18.parser.andThen
import com.aoc18.parser.becomes
import com.aoc18.parser.fixPoint
import com.aoc18.parser.keepNext
import com.aoc18.parser.keepPrevious
import com.aoc18.parser.map
import com.aoc18.parser.ofThese
import com.aoc18.parser.parse
import com.aoc18.parser.parseChar
import com.aoc18.parser.parseDigits
import com.aoc18.parser.parseOneOfChars
import com.aoc18.parser.parseString
import com.aoc18.parser.zeroOrMoreTimes
import com.aoc18.parser.zeroOrOneTime
import java.lang.Exception

sealed class Json {
    data class JObject(val properties: Map<Json.JString, Json>) : Json()

    data class JArray(val properties: Array<Json>) : Json() {
        override fun equals(other: Any?): kotlin.Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as JArray

            if (!properties.contentEquals(other.properties)) return false

            return true
        }

        override fun hashCode(): Int {
            return properties.contentHashCode()
        }
    }

    data class JString(val value: String) : Json()
    data class JNumber(val value: Double) : Json()

    sealed class JBoolean : Json() {
        object JTrue : JBoolean()
        object JFalse : JBoolean()
    }

    object JNull : Json()
}

val parseWhitespace: Parser<Unit> =
    parseOneOfChars(" \t\n")
        .zeroOrMoreTimes()
        .becomes(Unit)

val parseBoolean: Parser<Json> =
    listOf(
        parseString("true").becomes(Json.JBoolean.JTrue),
        parseString("false").becomes(Json.JBoolean.JFalse)
    )
        .ofThese()
        .keepPrevious(parseWhitespace)

val parseNull: Parser<Json> =
    parseString("null")
        .becomes(Json.JNull)
        .keepPrevious(parseWhitespace)

val parseNumber: Parser<Json> =
    parseChar('-')
        .zeroOrOneTime()
        .andThen { integerSign ->
            val integerSignChars = integerSign.map(::listOf).orElse(emptyList())

            listOf(
                parseChar('0').map { zero -> listOf(integerSignChars, listOf(zero)) },
                parseOneOfChars("123456789")
                    .andThen { first ->
                        parseOneOfChars("0123456789")
                            .zeroOrMoreTimes()
                            .map { rest ->
                                listOf(
                                    integerSignChars,
                                    listOf(first),
                                    rest
                                )
                            }
                    }
            )
                .ofThese()
                .map { chars -> chars.flatten() }
        }
        .andThen { integerPart ->
            parseChar('.')
                .keepNext(parseDigits.map { listOf(listOf('.'), it).flatten() })
                .zeroOrOneTime()
                .andThen { fractionalPart ->
                    parseOneOfChars("eE")
                        .keepNext(parseOneOfChars("+-").zeroOrOneTime())
                        .andThen { expSign ->
                            parseDigits
                                .map { expDigits ->
                                    listOf(
                                        listOf('E'),
                                        expSign.map(::listOf).orElseGet { listOf('+') }!!,
                                        expDigits
                                    )
                                        .flatten()
                                }
                        }
                        .zeroOrOneTime()
                        .map { exponentPart ->
                            listOf(
                                integerPart,
                                fractionalPart.map(::listOf).orElse(emptyList()),
                                exponentPart.orElse(emptyList())
                            )
                                .flatten()
                        }
                }
                .map { chars -> Json.JNumber(chars.joinToString("").toDouble()) }
        }
        .keepPrevious(parseWhitespace)

val parseJsonString: Parser<Json.JString> =
    fun(source: String, mutIndex: MutIndex): Json.JString? {
        val chars = mutableListOf<Char>()

        if (mutIndex.start >= source.length || source[mutIndex.start] != '"') {
            return null
        }

        var start = mutIndex.start

        while (true) {
            start += 1

            if (start >= source.length) {
                mutIndex.start = start
                return null
            }

            val c = source[start]

            if (c == '"') {
                start += 1
                break
            }

            if (c != '\\') {
                chars.add(c)
                continue
            }

            start += 1

            if (start >= source.length) {
                mutIndex.start = start
                return null
            }

            val c2 = source[start]

            when (c2) {
                '"' -> chars.add('"')
                '\\' -> chars.add('\\')
                '/' -> chars.add('/')
                'b' -> chars.add('\b')
//                    'f' -> {}
                'n' -> chars.add('\n')
                'r' -> chars.add('\r')
                't' -> chars.add('\t')
                'u' -> {
                    start += 1

                    if (start + 4 >= source.length) {
                        mutIndex.start = start
                        return null
                    }

                    try {
                        Integer
                            .parseInt(source.slice(start..start + 3), 16)
                            .toChar()
                            .let(chars::add)
                    } catch (ignored: Exception) {
                        mutIndex.start = start
                        return null
                    }

                    start += 3
                }
                else -> {
                    mutIndex.start = start
                    return null
                }
            }
        }

        mutIndex.start = start
        return Json.JString(chars.joinToString(""))
    }
        .keepPrevious(parseWhitespace)

val parseJsonValue: Parser<Json> =
    fixPoint { parseJsonValue ->
        listOf(
            parseJsonString,
            parseNumber,
            parseChar('{')
                .keepNext(parseWhitespace)
                .keepNext(
                    parseJsonString
                        .andThen { key ->
                            parseChar(':')
                                .keepNext(parseWhitespace)
                                .keepNext(parseJsonValue)
                                .map { value -> Pair(key, value) }
                        }
                        .zeroOrMoreTimes(parseChar(',').keepNext(parseWhitespace))
                        .map { pairs -> Json.JObject(mapOf(*pairs.toTypedArray())) }
                )
                .keepPrevious(parseWhitespace)
                .keepPrevious(parseChar('}')),
            parseChar('[')
                .keepNext(parseWhitespace)
                .keepNext(parseJsonValue.zeroOrMoreTimes(parseChar(',').keepNext(parseWhitespace)))
                .map { Json.JArray(it.toTypedArray()) }
                .keepPrevious(parseWhitespace)
                .keepPrevious(parseChar(']')),
            parseBoolean,
            parseNull
        )
            .ofThese()
            .keepPrevious(parseWhitespace)
    }

val parseJson: Parser<Json> =
    parseWhitespace.keepNext(parseJsonValue)

fun main() {
    println(parseJson.parse(""""\u00411"""".trimMargin()))
}
