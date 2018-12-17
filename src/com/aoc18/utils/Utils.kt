package com.aoc18.utils

import java.io.File
import java.io.InputStream

fun readFile(filename: String): String {
    val inputStream: InputStream = File(filename).inputStream()

    val inputString = inputStream
        .bufferedReader()
        .use { bufferedReader -> bufferedReader.readText() }

    inputStream.close()

    return inputString
}
