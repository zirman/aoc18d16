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
import kotlin.system.measureTimeMillis

sealed class Json {
    data class JObject(val properties: Map<String, Json>) : Json()

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
                                fractionalPart.orElse(emptyList()),
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
                                .map { value -> Pair(key.value, value) }
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

//data class Schema(
//    val schema: String,
//    val id: String,
//    val title: String,
//    val description: String,
//    val type: String,
//    val properties: Map<String, String>,
//    val required: List<String>
//)

//fun validateJsonSchema(json: Json): Boolean {
//    return when (json) {
//        is Json.JObject -> {
//            Schema(
//                schema = (json.properties["\$schema"]!! as Json.JString).value,
//                id = json.properties["\$id"],
//                title = json.properties["title"],
//                description = json.properties["description"],
//                type = json.properties["type"],
//                properties = json.properties["properties"]
//            )
//
//            true
//        }
//        else -> false
//    }
//}

fun main() {

    measureTimeMillis {
        for (i in 0..100000) {

            parseJson.parse(
            "[\n" +
                "  {\n" +
                "    \"_id\": \"5c24d2cc021a529ab4e69deb\",\n" +
                "    \"index\": 0,\n" +
                "    \"guid\": \"0991d0ec-eaa2-4911-84aa-474f5843f739\",\n" +
                "    \"isActive\": true,\n" +
                "    \"balance\": \"\$1,448.20\",\n" +
                "    \"picture\": \"http://placehold.it/32x32\",\n" +
                "    \"age\": 29,\n" +
                "    \"eyeColor\": \"blue\",\n" +
                "    \"name\": \"Trudy Washington\",\n" +
                "    \"gender\": \"female\",\n" +
                "    \"company\": \"OVOLO\",\n" +
                "    \"email\": \"trudywashington@ovolo.com\",\n" +
                "    \"phone\": \"+1 (809) 413-3608\",\n" +
                "    \"address\": \"265 Kane Street, Brooktrails, Wyoming, 8464\",\n" +
                "    \"about\": \"In magna est ut non ad amet. Lorem ullamco tempor excepteur dolor officia nostrud reprehenderit pariatur velit. Consequat est Lorem nulla do et tempor consequat ad commodo sit ullamco incididunt minim eu. Consectetur culpa ut irure excepteur cupidatat minim exercitation dolor ea fugiat nulla ea veniam.\\r\\n\",\n" +
                "    \"registered\": \"2016-01-06T03:27:49 +05:00\",\n" +
                "    \"latitude\": 25.553189,\n" +
                "    \"longitude\": -167.376142,\n" +
                "    \"tags\": [\n" +
                "      \"ipsum\",\n" +
                "      \"dolor\",\n" +
                "      \"anim\",\n" +
                "      \"nulla\",\n" +
                "      \"exercitation\",\n" +
                "      \"consectetur\",\n" +
                "      \"exercitation\"\n" +
                "    ],\n" +
                "    \"friends\": [\n" +
                "      {\n" +
                "        \"id\": 0,\n" +
                "        \"name\": \"Lindsey Mcintyre\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"id\": 1,\n" +
                "        \"name\": \"Barr Cross\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"id\": 2,\n" +
                "        \"name\": \"Francisca Acevedo\"\n" +
                "      }\n" +
                "    ],\n" +
                "    \"greeting\": \"Hello, Trudy Washington! You have 1 unread messages.\",\n" +
                "    \"favoriteFruit\": \"apple\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"_id\": \"5c24d2cc89222993645057c4\",\n" +
                "    \"index\": 1,\n" +
                "    \"guid\": \"e6d2bd0b-9226-43e7-b301-2617e91bdb23\",\n" +
                "    \"isActive\": true,\n" +
                "    \"balance\": \"\$1,810.85\",\n" +
                "    \"picture\": \"http://placehold.it/32x32\",\n" +
                "    \"age\": 38,\n" +
                "    \"eyeColor\": \"blue\",\n" +
                "    \"name\": \"Grace Hess\",\n" +
                "    \"gender\": \"female\",\n" +
                "    \"company\": \"EXODOC\",\n" +
                "    \"email\": \"gracehess@exodoc.com\",\n" +
                "    \"phone\": \"+1 (978) 573-3028\",\n" +
                "    \"address\": \"697 Pine Street, Finderne, Florida, 8136\",\n" +
                "    \"about\": \"Fugiat pariatur ut anim deserunt irure ea sunt elit enim irure irure duis enim. Do minim aute id reprehenderit nisi voluptate ut enim excepteur cillum id mollit amet. Voluptate elit cillum sint non duis adipisicing et et nostrud anim. Elit est amet duis sunt. Tempor tempor do officia id. Laborum et et qui amet veniam quis nulla nostrud cillum. Nulla aliquip reprehenderit pariatur nisi officia adipisicing cupidatat qui.\\r\\n\",\n" +
                "    \"registered\": \"2018-05-01T09:55:55 +04:00\",\n" +
                "    \"latitude\": 0.612367,\n" +
                "    \"longitude\": 167.684126,\n" +
                "    \"tags\": [\n" +
                "      \"occaecat\",\n" +
                "      \"cillum\",\n" +
                "      \"magna\",\n" +
                "      \"anim\",\n" +
                "      \"Lorem\",\n" +
                "      \"duis\",\n" +
                "      \"nulla\"\n" +
                "    ],\n" +
                "    \"friends\": [\n" +
                "      {\n" +
                "        \"id\": 0,\n" +
                "        \"name\": \"Alexander Strickland\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"id\": 1,\n" +
                "        \"name\": \"Judy Dickson\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"id\": 2,\n" +
                "        \"name\": \"Leonor Kidd\"\n" +
                "      }\n" +
                "    ],\n" +
                "    \"greeting\": \"Hello, Grace Hess! You have 2 unread messages.\",\n" +
                "    \"favoriteFruit\": \"apple\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"_id\": \"5c24d2cc37c37ccf7d9108bf\",\n" +
                "    \"index\": 2,\n" +
                "    \"guid\": \"83fe6451-95c7-4553-85ed-9c7b57dab1cf\",\n" +
                "    \"isActive\": true,\n" +
                "    \"balance\": \"\$3,857.22\",\n" +
                "    \"picture\": \"http://placehold.it/32x32\",\n" +
                "    \"age\": 37,\n" +
                "    \"eyeColor\": \"brown\",\n" +
                "    \"name\": \"Jenkins Rios\",\n" +
                "    \"gender\": \"male\",\n" +
                "    \"company\": \"DYMI\",\n" +
                "    \"email\": \"jenkinsrios@dymi.com\",\n" +
                "    \"phone\": \"+1 (875) 500-3048\",\n" +
                "    \"address\": \"851 Baltic Street, Lindisfarne, Wisconsin, 4594\",\n" +
                "    \"about\": \"Ut voluptate nulla laborum exercitation ex ea eiusmod reprehenderit laborum est irure proident proident. Velit dolor nisi excepteur dolore ea. Commodo pariatur non commodo sunt amet adipisicing anim nisi aliqua.\\r\\n\",\n" +
                "    \"registered\": \"2016-09-15T06:48:45 +04:00\",\n" +
                "    \"latitude\": -52.701704,\n" +
                "    \"longitude\": 145.848498,\n" +
                "    \"tags\": [\n" +
                "      \"tempor\",\n" +
                "      \"duis\",\n" +
                "      \"adipisicing\",\n" +
                "      \"labore\",\n" +
                "      \"officia\",\n" +
                "      \"Lorem\",\n" +
                "      \"officia\"\n" +
                "    ],\n" +
                "    \"friends\": [\n" +
                "      {\n" +
                "        \"id\": 0,\n" +
                "        \"name\": \"Millicent Parks\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"id\": 1,\n" +
                "        \"name\": \"Angeline Bass\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"id\": 2,\n" +
                "        \"name\": \"Coffey Foreman\"\n" +
                "      }\n" +
                "    ],\n" +
                "    \"greeting\": \"Hello, Jenkins Rios! You have 3 unread messages.\",\n" +
                "    \"favoriteFruit\": \"apple\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"_id\": \"5c24d2ccf8466f4e279b5d78\",\n" +
                "    \"index\": 3,\n" +
                "    \"guid\": \"f638a59c-4c0b-4803-b870-ea32a9aebc48\",\n" +
                "    \"isActive\": true,\n" +
                "    \"balance\": \"\$1,805.49\",\n" +
                "    \"picture\": \"http://placehold.it/32x32\",\n" +
                "    \"age\": 34,\n" +
                "    \"eyeColor\": \"blue\",\n" +
                "    \"name\": \"Nolan Peters\",\n" +
                "    \"gender\": \"male\",\n" +
                "    \"company\": \"SCENTRIC\",\n" +
                "    \"email\": \"nolanpeters@scentric.com\",\n" +
                "    \"phone\": \"+1 (999) 541-3801\",\n" +
                "    \"address\": \"655 Alice Court, Soham, Washington, 2135\",\n" +
                "    \"about\": \"Cillum incididunt duis officia pariatur eu. Commodo minim dolore mollit amet ut elit laborum qui anim dolor. Officia ut laboris ipsum duis commodo tempor. Magna adipisicing culpa anim proident sunt non minim eiusmod amet veniam minim eu laborum. Proident eu cillum velit Lorem ea. Magna excepteur fugiat cupidatat nostrud. Officia excepteur quis eu ut eu cupidatat ex veniam ea quis ex consectetur proident.\\r\\n\",\n" +
                "    \"registered\": \"2018-09-21T07:11:03 +04:00\",\n" +
                "    \"latitude\": 75.669185,\n" +
                "    \"longitude\": 69.707785,\n" +
                "    \"tags\": [\n" +
                "      \"aute\",\n" +
                "      \"incididunt\",\n" +
                "      \"et\",\n" +
                "      \"sunt\",\n" +
                "      \"occaecat\",\n" +
                "      \"dolor\",\n" +
                "      \"nulla\"\n" +
                "    ],\n" +
                "    \"friends\": [\n" +
                "      {\n" +
                "        \"id\": 0,\n" +
                "        \"name\": \"Evangeline Graham\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"id\": 1,\n" +
                "        \"name\": \"Macdonald Park\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"id\": 2,\n" +
                "        \"name\": \"Jordan Benton\"\n" +
                "      }\n" +
                "    ],\n" +
                "    \"greeting\": \"Hello, Nolan Peters! You have 1 unread messages.\",\n" +
                "    \"favoriteFruit\": \"banana\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"_id\": \"5c24d2cccb1d4227b2292191\",\n" +
                "    \"index\": 4,\n" +
                "    \"guid\": \"6fb05f74-6f89-4718-9ba7-c616ecf5a5ba\",\n" +
                "    \"isActive\": true,\n" +
                "    \"balance\": \"\$2,319.94\",\n" +
                "    \"picture\": \"http://placehold.it/32x32\",\n" +
                "    \"age\": 30,\n" +
                "    \"eyeColor\": \"brown\",\n" +
                "    \"name\": \"Berta Browning\",\n" +
                "    \"gender\": \"female\",\n" +
                "    \"company\": \"RUBADUB\",\n" +
                "    \"email\": \"bertabrowning@rubadub.com\",\n" +
                "    \"phone\": \"+1 (824) 578-3990\",\n" +
                "    \"address\": \"545 Thomas Street, Dyckesville, Guam, 9853\",\n" +
                "    \"about\": \"Est elit tempor id laboris nostrud consectetur. Nisi aliqua occaecat laboris elit magna magna ea cillum labore laboris id esse ullamco ex. Aliqua in cillum culpa consequat ipsum tempor.\\r\\n\",\n" +
                "    \"registered\": \"2018-02-04T08:04:27 +05:00\",\n" +
                "    \"latitude\": -75.760998,\n" +
                "    \"longitude\": -1.83377,\n" +
                "    \"tags\": [\n" +
                "      \"mollit\",\n" +
                "      \"quis\",\n" +
                "      \"eiusmod\",\n" +
                "      \"tempor\",\n" +
                "      \"laborum\",\n" +
                "      \"occaecat\",\n" +
                "      \"cupidatat\"\n" +
                "    ],\n" +
                "    \"friends\": [\n" +
                "      {\n" +
                "        \"id\": 0,\n" +
                "        \"name\": \"Maria Kinney\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"id\": 1,\n" +
                "        \"name\": \"Vanessa Vaughan\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"id\": 2,\n" +
                "        \"name\": \"Claudia Lucas\"\n" +
                "      }\n" +
                "    ],\n" +
                "    \"greeting\": \"Hello, Berta Browning! You have 7 unread messages.\",\n" +
                "    \"favoriteFruit\": \"banana\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"_id\": \"5c24d2cc82d2c524f0b50fa4\",\n" +
                "    \"index\": 5,\n" +
                "    \"guid\": \"ac65e64c-c5cf-4e1b-9d5c-a59a6ee13fb5\",\n" +
                "    \"isActive\": true,\n" +
                "    \"balance\": \"\$2,518.76\",\n" +
                "    \"picture\": \"http://placehold.it/32x32\",\n" +
                "    \"age\": 26,\n" +
                "    \"eyeColor\": \"green\",\n" +
                "    \"name\": \"Duffy Ballard\",\n" +
                "    \"gender\": \"male\",\n" +
                "    \"company\": \"INRT\",\n" +
                "    \"email\": \"duffyballard@inrt.com\",\n" +
                "    \"phone\": \"+1 (927) 441-3833\",\n" +
                "    \"address\": \"330 Balfour Place, Hayden, Oregon, 1053\",\n" +
                "    \"about\": \"Cupidatat duis veniam tempor voluptate. Deserunt est dolore esse nulla est do id ullamco ullamco culpa id reprehenderit. Eiusmod magna quis magna commodo enim amet reprehenderit officia excepteur proident duis veniam ullamco. Deserunt dolor do occaecat reprehenderit duis. Cillum fugiat proident sunt aliquip. Laborum fugiat laborum laboris deserunt cupidatat aliqua. Id id pariatur ea veniam tempor magna aliqua tempor culpa cillum anim deserunt ad duis.\\r\\n\",\n" +
                "    \"registered\": \"2017-09-03T06:55:35 +04:00\",\n" +
                "    \"latitude\": -9.656284,\n" +
                "    \"longitude\": -70.824484,\n" +
                "    \"tags\": [\n" +
                "      \"velit\",\n" +
                "      \"ut\",\n" +
                "      \"magna\",\n" +
                "      \"sunt\",\n" +
                "      \"eiusmod\",\n" +
                "      \"ea\",\n" +
                "      \"consequat\"\n" +
                "    ],\n" +
                "    \"friends\": [\n" +
                "      {\n" +
                "        \"id\": 0,\n" +
                "        \"name\": \"Knapp Armstrong\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"id\": 1,\n" +
                "        \"name\": \"Lauri Moore\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"id\": 2,\n" +
                "        \"name\": \"West Stafford\"\n" +
                "      }\n" +
                "    ],\n" +
                "    \"greeting\": \"Hello, Duffy Ballard! You have 1 unread messages.\",\n" +
                "    \"favoriteFruit\": \"apple\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"_id\": \"5c24d2ccbf141fe001a3228b\",\n" +
                "    \"index\": 6,\n" +
                "    \"guid\": \"bb008784-1989-4885-ba71-b49925240792\",\n" +
                "    \"isActive\": true,\n" +
                "    \"balance\": \"\$3,122.37\",\n" +
                "    \"picture\": \"http://placehold.it/32x32\",\n" +
                "    \"age\": 34,\n" +
                "    \"eyeColor\": \"brown\",\n" +
                "    \"name\": \"Vasquez Gay\",\n" +
                "    \"gender\": \"male\",\n" +
                "    \"company\": \"TETAK\",\n" +
                "    \"email\": \"vasquezgay@tetak.com\",\n" +
                "    \"phone\": \"+1 (816) 587-2340\",\n" +
                "    \"address\": \"292 Verona Place, Harmon, Illinois, 4337\",\n" +
                "    \"about\": \"Minim exercitation elit et anim esse velit enim velit pariatur aliquip veniam. Occaecat dolor velit anim anim magna consectetur consequat do laboris sit ex ut. Incididunt mollit id qui qui amet mollit id.\\r\\n\",\n" +
                "    \"registered\": \"2015-07-23T04:19:12 +04:00\",\n" +
                "    \"latitude\": -7.156248,\n" +
                "    \"longitude\": 103.701895,\n" +
                "    \"tags\": [\n" +
                "      \"ad\",\n" +
                "      \"minim\",\n" +
                "      \"excepteur\",\n" +
                "      \"deserunt\",\n" +
                "      \"tempor\",\n" +
                "      \"adipisicing\",\n" +
                "      \"officia\"\n" +
                "    ],\n" +
                "    \"friends\": [\n" +
                "      {\n" +
                "        \"id\": 0,\n" +
                "        \"name\": \"Blevins Mckay\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"id\": 1,\n" +
                "        \"name\": \"Farley Aguilar\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"id\": 2,\n" +
                "        \"name\": \"Erna Lambert\"\n" +
                "      }\n" +
                "    ],\n" +
                "    \"greeting\": \"Hello, Vasquez Gay! You have 7 unread messages.\",\n" +
                "    \"favoriteFruit\": \"apple\"\n" +
                "  }\n" +
                "]"
            )
        }
    }
        .let { println(it) }
}
