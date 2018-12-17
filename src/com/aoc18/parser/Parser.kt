package com.aoc18.parser

typealias Parser<A> = (String, Int) -> ParseResult<A>

fun <A> Parser<A>.parse(s: String) = this(s, 0)

sealed class ParseResult<out A> {
    data class OK<out A>(val index: Int, val value: A) : ParseResult<A>()
    data class Error(val index: Int) : ParseResult<Nothing>()
}

fun <A, B> Parser<A>.andThen(f: (A) -> Parser<B>): Parser<B> =
    fun(source: String, index: Int): ParseResult<B> {
        val result = this(source, index)

        return when (result) {
            is ParseResult.OK -> f(result.value).invoke(source, result.index)
            is ParseResult.Error -> result
        }
    }

fun <A> Parser<A>.orElse(parser: Parser<A>): Parser<A> =
    fun(source: String, index: Int): ParseResult<A> {
        val result = this(source, index)

        return when (result) {
            is ParseResult.OK -> result
            is ParseResult.Error -> parser(source, index)
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
    fun(_: String, index: Int): ParseResult<A> =
        ParseResult.OK(index, this)

fun <A, B> Parser<A>.apLeft(nextParser: Parser<B>) =
    fun(source: String, index: Int): ParseResult<A> {
        val result = this(source, index)

        return when (result) {
            is ParseResult.OK -> {
                val nextResult = nextParser(source, result.index)

                when (nextResult) {
                    is ParseResult.OK -> ParseResult.OK(
                        nextResult.index,
                        result.value
                    )
                    is ParseResult.Error -> ParseResult.Error(
                        nextResult.index
                    )
                }
            }

            is ParseResult.Error ->
                result
        }
    }
