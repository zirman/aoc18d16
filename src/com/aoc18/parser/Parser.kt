package com.aoc18.parser

typealias Parser<A> = (String, Int) -> ParseResult<A>

fun <A> Parser<A>.parse(s: String) = this(s, 0)

sealed class ParseResult<out A> {
    data class OK<out A>(val index: Int, val value: A) : ParseResult<A>()
    data class Error(val index: Int) : ParseResult<Nothing>()
}

fun <A> fixPoint(f: (Parser<A>) -> Parser<A>): Parser<A> =
    fun(source: String, index: Int): ParseResult<A> =
        f(fixPoint(f)).invoke(source, index)

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

fun <A> List<Parser<A>>.ofThese(): Parser<A> =
    fun(source: String, index: Int): ParseResult<A> {
        forEach { parser ->
            val result = parser(source, index)

            if (result is ParseResult.OK) {
                return result
            }
        }

        return ParseResult.Error(index)
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

fun <A : Any> Parser<A>.zeroOrOneTime(): Parser<A?> =
    fun(source: String, index: Int): ParseResult<A?> =
        this(source, index)
            .let { it as? ParseResult.OK }
            ?: ParseResult.OK(index, null)

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

fun <A, B> Parser<A>.zeroOrMoreTimes(separator: Parser<B>): Parser<List<A>> =
    fun(source: String, index: Int): ParseResult<List<A>> {
        val values = mutableListOf<A>()
        var lastIndex = index

        loop@ while (true) {
            this(source, lastIndex)
                .let { it as? ParseResult.OK }
                ?.let { (nextIndex, nextValue) ->
                    values.add(nextValue)
                    lastIndex = nextIndex
                    separator(source, nextIndex)
                }
                ?.let { it as? ParseResult.OK }
                ?.let { (nextIndex, _) -> lastIndex = nextIndex }
                ?: return ParseResult.OK(lastIndex, values)
        }
    }

fun <A> Parser<A>.oneOrMoreTimes(): Parser<List<A>> =
    fun(source: String, index: Int): ParseResult<List<A>> {
        val firstResult = this(source, index)

        when (firstResult) {
            is ParseResult.OK -> {
                val results = mutableListOf(firstResult.value)
                var lastIndex = firstResult.index

                while (true) {
                    this(source, lastIndex)
                        .let { result -> result as? ParseResult.OK }
                        ?.let { (nextIndex, nextValue) ->
                            results.add(nextValue)
                            lastIndex = nextIndex
                        }
                        ?: return ParseResult.OK(lastIndex, results)
                }
            }

            is ParseResult.Error ->
                return ParseResult.Error(firstResult.index)
        }
    }

fun <A, B> Parser<A>.oneOrMoreTimes(separator: Parser<B>): Parser<List<A>> =
    fun(source: String, index: Int): ParseResult<List<A>> {
        val firstResult = this(source, index)

        if (firstResult !is ParseResult.OK) {
            firstResult as ParseResult.Error
            return ParseResult.Error(firstResult.index)
        }

        val results = mutableListOf(firstResult.value)
        var lastIndex = firstResult.index

        while (true) {
            (separator(source, lastIndex) as? ParseResult.OK)
                ?.index
                ?.let { nextIndex -> this(source, nextIndex) }
                ?.let { result -> result as? ParseResult.OK }
                ?.let { (nextIndex, nextValue) ->
                    results.add(nextValue)
                    lastIndex = nextIndex
                }
                ?: return ParseResult.OK(lastIndex, results)
        }
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

fun <A, B> Parser<A>.keepPrevious(nextParser: Parser<B>) =
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

fun <A, B> Parser<A>.map(f: (A) -> B): Parser<B> =
    fun(source: String, index: Int): ParseResult<B> {
        val result = this(source, index)

        return when (result) {
            is ParseResult.OK -> ParseResult.OK(result.index, f(result.value))
            is ParseResult.Error -> result
        }
    }

fun <A, B> Parser<A>.becomes(newValue: B): Parser<B> =
    fun(source: String, index: Int): ParseResult<B> {
        val result = this(source, index)

        return when (result) {
            is ParseResult.OK -> ParseResult.OK(result.index, newValue)
            is ParseResult.Error -> result
        }
    }

fun <A> Parser<A>.otherwise(default: A): Parser<A> =
    fun(source: String, index: Int): ParseResult<A> {
        val result = this(source, index)

        return when (result) {
            is ParseResult.OK -> result
            is ParseResult.Error -> ParseResult.OK(index, default)
        }
    }

fun <A, B> Parser<A>.keepNext(parser: Parser<B>): Parser<B> =
    fun(source: String, index: Int): ParseResult<B> {
        val result = this(source, index)

        return when (result) {
            is ParseResult.OK -> parser(source, result.index)
            is ParseResult.Error -> result
        }
    }
