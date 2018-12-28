package com.aoc18.parser

import java.util.Optional

sealed class Either<out A, out E> {
    data class Ok<out A>(val value: A) : Either<A, Nothing>()
    data class Err<out E>(val error: E) : Either<Nothing, E>()
}

fun <A, B, E> Either<A, E>.map(f: (A) -> B): Either<B, E> =
    when (this) {
        is Either.Ok -> Either.Ok(f(value))
        is Either.Err -> this
    }

fun <A, B, E> Either<A, E>.flatMap(f: (A) -> Either<B, E>): Either<B, E> =
    when (this) {
        is Either.Ok -> f(value)
        is Either.Err -> this
    }

typealias Parser<A> = (String, MutIndex) -> A?

data class MutIndex(var start: Int)

fun <A : Any> Parser<A>.parse(s: String): Either<A, Int> {
    val index = MutIndex(0)
    val value = this(s, index)

    return if (value != null && index.start == s.length) {
        Either.Ok(value)
    } else {
        Either.Err(index.start)
    }
}

fun <A : Any> fixPoint(f: (Parser<A>) -> Parser<A>): Parser<A> =
    fun(source: String, mutIndex: MutIndex): A? =
        f(fixPoint(f)).invoke(source, mutIndex)

fun <A : Any, B : Any> Parser<A>.andThen(f: (A) -> Parser<B>): Parser<B> =
    fun(source: String, mutIndex: MutIndex): B? =
        this(source, mutIndex)?.let(f)?.invoke(source, mutIndex)

fun <A> Parser<A>.orElse(parser: Parser<A>): Parser<A> =
    fun(source: String, mutIndex: MutIndex): A? {
        val start = mutIndex.start

        return this(source, mutIndex)
            ?: run {
                mutIndex.start = start
                parser(source, mutIndex)
            }
    }

fun <A> List<Parser<A>>.ofThese(): Parser<A> =
    fun(source: String, mutIndex: MutIndex): A? {
        val start = mutIndex.start

        for (parser in this) {
            mutIndex.start = start
            parser(source, mutIndex)?.let { return it }
        }

        return null
    }

fun parseString(str: String): Parser<String> =
    fun(source: String, mutIndex: MutIndex): String? {
        val start = mutIndex.start

        for (i in 0 until str.length) {
            val j = start + i

            if (j >= source.length) {
                return null
            }

            if (source[j] != str[i]) {
                return null
            }
        }

        mutIndex.start = start + str.length
        return str
    }

fun <A : Any> Parser<A>.zeroOrOneTime(): Parser<Optional<A>?> =
    fun(source: String, mutIndex: MutIndex): Optional<A>? {
        val start = mutIndex.start
        val value = this(source, mutIndex)

        if (value == null) {
            mutIndex.start = start
            return Optional.empty()
        }

        return Optional.of(value)
    }

fun <A : Any> Parser<A>.zeroOrMoreTimes(): Parser<List<A>> =
    fun(source: String, mutIndex: MutIndex): List<A>? {
        val values = mutableListOf<A>()
        var start = mutIndex.start

        while (true) {
            val value = this(source, mutIndex)

            if (value == null) {
                mutIndex.start = start
                return values
            } else {
                start = mutIndex.start
                values.add(value)
            }
        }
    }

fun <A : Any, B : Any> Parser<A>.zeroOrMoreTimes(separator: Parser<B>): Parser<List<A>> =
    fun(source: String, mutIndex: MutIndex): List<A>? {
        val values = mutableListOf<A>()

        values.add(this(source, mutIndex) ?: return values)
        var start = mutIndex.start

        while (true) {
            if (separator(source, mutIndex) == null) {
                mutIndex.start = start
                return values
            }

            val value = this(source, mutIndex)

            if (value == null) {
                mutIndex.start = start
                return values
            } else {
                start = mutIndex.start
                values.add(value)
            }
        }
    }

fun <A : Any> Parser<A>.oneOrMoreTimes(): Parser<List<A>> =
    fun(source: String, mutIndex: MutIndex): List<A>? {
        val values = mutableListOf(this(source, mutIndex) ?: return null)
        var start = mutIndex.start

        while (true) {
            val value = this(source, mutIndex)

            if (value == null) {
                mutIndex.start = start
                return values
            }

            values.add(value)
            start = mutIndex.start
        }
    }

fun <A : Any, B : Any> Parser<A>.oneOrMoreTimes(separator: Parser<B>): Parser<List<A>> =
    fun(source: String, mutIndex: MutIndex): List<A>? {
        val values = mutableListOf(this(source, mutIndex) ?: return null)
        var start = mutIndex.start

        while (true) {
            if (separator(source, mutIndex) == null) {
                mutIndex.start = start
                return values
            }

            val value = this(source, mutIndex)

            if (value == null) {
                mutIndex.start = start
                return values
            }

            values.add(value)
            start = mutIndex.start
        }
    }

fun parseOneOfChars(chars: String): Parser<Char> =
    fun(source: String, mutIndex: MutIndex): Char? {
        if (mutIndex.start >= source.length) {
            return null
        }

        val c = source[mutIndex.start]

        if (chars.contains(c)) {
            mutIndex.start += 1
            return c
        }

        return null
    }

fun parseChar(char: Char): Parser<Char> =
    fun(source: String, mutIndex: MutIndex): Char? {
        if (mutIndex.start >= source.length) {
            return null
        }

        val c = source[mutIndex.start]

        if (char == c) {
            mutIndex.start += 1
            return char
        }

        return null
    }

fun <A : Any> A.parseLift(): Parser<A> =
    fun(_: String, mutIndex: MutIndex): A =
        this

fun <A : Any, B : Any> Parser<A>.keepPrevious(nextParser: Parser<B>) =
    fun(source: String, mutIndex: MutIndex): A? {
        val value = this(source, mutIndex) ?: return null
        nextParser(source, mutIndex) ?: return null
        return value
    }

fun <A : Any, B : Any> Parser<A>.map(f: (A) -> B): Parser<B> =
    fun(source: String, mutIndex: MutIndex): B? =
        this(source, mutIndex)?.let(f)

fun <A : Any, B : Any> Parser<A>.becomes(newValue: B): Parser<B> =
    fun(source: String, mutIndex: MutIndex): B? {
        this(source, mutIndex) ?: return null
        return newValue
    }

fun <A : Any> Parser<A>.otherwise(default: A): Parser<A> =
    fun(source: String, mutMutIndex: MutIndex): A? {
        val start = mutMutIndex.start
        val value = this(source, mutMutIndex)

        if (value == null) {
            mutMutIndex.start = start
            return default
        }

        return value
    }

fun <A : Any, B : Any> Parser<A>.keepNext(parser: Parser<B>): Parser<B> =
    fun(source: String, mutIndex: MutIndex): B? {
        this(source, mutIndex) ?: return null
        return parser(source, mutIndex)
    }

val parseDigits: Parser<List<Char>> = parseOneOfChars("0123456789")
    .oneOrMoreTimes()

val parsePosInt: Parser<Int> =
    parseDigits.andThen { digits -> digits.joinToString("").toInt().parseLift() }

val parseInt: Parser<Int> =
    parseChar('-')
        .zeroOrOneTime()
        .andThen { signPrefix ->
            parseDigits
                .andThen { digits ->
                    digits.joinToString("", signPrefix?.let { "-" } ?: "").toInt().parseLift()
                }
        }

val parsePosLong: Parser<Long> =
    parseDigits.andThen { digits -> digits.joinToString("").toLong().parseLift() }
