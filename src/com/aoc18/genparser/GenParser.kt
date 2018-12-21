package com.aoc18.genparser

typealias GenParser<T, A> = (Sequence<T>) -> GenResult<T, A>

fun <T, A> GenParser<T, A>.parse(s: Sequence<T>) = this(s)

sealed class GenResult<out T, out A> {
    data class OK<out T, out A>(val source: Sequence<T>, val value: A) : GenResult<T, A>()
    data class Error<out T>(val source: Sequence<T>) : GenResult<T, Nothing>()
}

fun <T, A, B> GenParser<T, A>.andThen(f: (A) -> GenParser<T, B>): GenParser<T, B> =
    fun(source: Sequence<T>): GenResult<T, B> {
        val result = this(source)

        return when (result) {
            is GenResult.OK -> f(result.value).invoke(result.source)
            is GenResult.Error -> result
        }
    }

fun <T, A> GenParser<T, A>.orElse(parser: GenParser<T, A>): GenParser<T, A> =
    fun(source: Sequence<T>): GenResult<T, A> {
        val result = this(source)

        return when (result) {
            is GenResult.OK -> result
            is GenResult.Error -> parser(source)
        }
    }

fun <T, A> List<GenParser<T, A>>.ofThese(): GenParser<T, A> =
    fun(source: Sequence<T>): GenResult<T, A> {
        forEach { parser ->
            val result = parser(source)

            if (result is GenResult.OK) {
                return result
            }
        }

        return GenResult.Error(source)
    }

//fun parseString(str: String): GenParser<Char, String> =
//    fun(source: Sequence<Char>): GenResult<Char, String> =
//        if (source.take(str.length).joinToString("") == str) {
//            GenResult.OK(source.drop(str.length), str)
//        } else {
//            GenResult.Error(source)
//        }

fun <T> parseTokens(tokens: Array<T>): GenParser<T, Array<T>> =
    fun(source: Sequence<T>): GenResult<T, Array<T>> {
        val s = source.take(tokens.size)

        if (s.count() != tokens.size) {
            return GenResult.Error(source)
        }

        for ((i, t) in s.withIndex()) {
            if (tokens[i] != t) {
                return GenResult.Error(source)
            }
        }

        return GenResult.OK(source.drop(tokens.size), tokens)
    }

fun <T, A> GenParser<T, A>.zeroOrMoreTimes(): GenParser<T, List<A>> =
    fun(source: Sequence<T>): GenResult<T, List<A>> {
        val results = mutableListOf<A>()
        var lastSource = source

        loop@ while (true) {
            val nextResult = this(lastSource)

            when (nextResult) {
                is GenResult.OK -> {
                    results.add(nextResult.value)
                    lastSource = nextResult.source
                }

                is GenResult.Error ->
                    break@loop
            }
        }

        return GenResult.OK(lastSource, results)
    }

fun <T, A> GenParser<T, A>.oneOrMoreTimes(): GenParser<T, List<A>> =
    fun(source: Sequence<T>): GenResult<T, List<A>> {
        val results = mutableListOf<A>()
        var lastSource = source

        val nextResult = this(lastSource)

        when (nextResult) {
            is GenResult.OK -> {
                results.add(nextResult.value)
                lastSource = nextResult.source
            }

            is GenResult.Error ->
                return GenResult.Error(lastSource)
        }

        loop@ while (true) {
            val nextResult = this(lastSource)

            when (nextResult) {
                is GenResult.OK -> {
                    results.add(nextResult.value)
                    lastSource = nextResult.source
                }

                is GenResult.Error ->
                    break@loop
            }
        }

        return GenResult.OK(lastSource, results)
    }

fun <T> parseOneOfTokens(tokens: Array<T>): GenParser<T, T> =
    fun(source: Sequence<T>): GenResult<T, T> =
        source
            .firstOrNull()
            ?.let { c -> if (tokens.contains(c)) GenResult.OK(source.drop(1), c) else null }
            ?: GenResult.Error(source)

fun <T> parseToken(token: T): GenParser<T, T> =
    fun(source: Sequence<T>): GenResult<T, T> =
        source
            .firstOrNull()
            ?.let { c -> if (token == c) GenResult.OK(source.drop(1), c) else null }
            ?: GenResult.Error(source)

fun <T> parseIf(p: (T) -> Boolean): GenParser<T, T> =
    fun(source: Sequence<T>): GenResult<T, T> =
        source
            .firstOrNull()
            ?.let { t -> if (p(t)) GenResult.OK(source.drop(1), t) else null }
            ?: GenResult.Error(source)

fun <T, A> A.parseLift(): GenParser<T, A> =
    fun(source: Sequence<T>): GenResult<T, A> =
        GenResult.OK(source, this)

fun <T, A, B> GenParser<T, A>.keepPrevious(nextParser: GenParser<T, B>) =
    fun(source: Sequence<T>): GenResult<T, A> {
        val result = this(source)

        return when (result) {
            is GenResult.OK -> {
                val nextResult = nextParser(result.source)

                when (nextResult) {
                    is GenResult.OK -> GenResult.OK(nextResult.source, result.value)
                    is GenResult.Error -> nextResult
                }
            }

            is GenResult.Error ->
                result
        }
    }

fun <T, A, B> GenParser<T, A>.map(f: (A) -> B): GenParser<T, B> =
    fun(source: Sequence<T>): GenResult<T, B> {
        val result = this(source)

        return when (result) {
            is GenResult.OK -> GenResult.OK(result.source, f(result.value))
            is GenResult.Error -> result
        }
    }

fun <T, A, B> GenParser<T, A>.becomes(newValue: B): GenParser<T, B> =
    fun(source: Sequence<T>): GenResult<T, B> {
        val result = this(source)

        return when (result) {
            is GenResult.OK -> GenResult.OK(result.source, newValue)
            is GenResult.Error -> result
        }
    }

fun <T, A, B> GenParser<T, A>.keepNext(parser: GenParser<T, B>): GenParser<T, B> =
    fun(source: Sequence<T>): GenResult<T, B> {
        val result = this(source)

        return when (result) {
            is GenResult.OK -> parser(result.source)
            is GenResult.Error -> result
        }
    }
