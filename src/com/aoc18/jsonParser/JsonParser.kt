package com.aoc18.jsonParser

import com.aoc18.parser.Either
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
    data class JObject(val properties: Map<String, Json>) : Json()

    data class JArray(val items: Array<Json>) : Json() {
        override fun equals(other: Any?): kotlin.Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as JArray

            if (!items.contentEquals(other.items)) return false

            return true
        }

        override fun hashCode(): Int {
            return items.contentHashCode()
        }
    }

    data class JString(val value: String) : Json()
    data class JNumber(val value: Double) : Json()

    sealed class JBoolean : Json() {
        object JTrue : JBoolean() {
            override fun toBoolean(): Boolean = true
        }

        object JFalse : JBoolean() {
            override fun toBoolean(): Boolean = false
        }

        abstract fun toBoolean(): Boolean
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

sealed class Property {
    data class PObject(
        val title: String?,
        val description: String?,
        val properties: Map<String, Property>,
        val required: List<String>
    ) : Property()

    data class PArray(
        val title: String?,
        val description: String?,
        val items: Property,
        val minItems: Int?,
        val uniqueItems: Boolean?
    ) : Property()

    data class PString(
        val title: String?,
        val description: String?,
        val enum: List<String>?,
        val minLength: Int?
    ) : Property()

    data class PNumber(
        val title: String?,
        val description: String?,
        val exclusiveMinimum: Double?
    ) : Property()

    data class PInteger(
        val title: String?,
        val description: String?,
        val minimum: Int?,
        val maximum: Int?
    ) : Property()
}

data class Schema(
    val schema: String?,
    val id: String?,
    val title: String,
    val property: Property
)

fun Json.toSchema(): Either<Schema, String> =
    try {
        this as Json.JObject

        Either.Ok(
            Schema(
                schema = (properties["\$schema"] as? Json.JString)?.value,
                id = (properties["\$id"] as? Json.JString)?.value,
                title = (properties["title"] as Json.JString).value,
                property = toProperty()
            )
        )
    } catch (exception: Exception) {
        exception.printStackTrace()
        Either.Err(exception.message ?: "Error converting to JSON Schema")
    }

fun (Json.JObject).toProperty(): Property {
    val sType = (properties["type"] as Json.JString).value

    return when (sType) {
        "object" -> {
            val prop = (properties["items"] as Json.JObject).properties
                .mapValues { (_, v) -> (v as Json.JObject).toProperty() }

//            val oneOf = (properties["oneOf"] as Json.JArray).items
//                .map {
//
//                }
//                .mapValues { (_, v) -> (v as Json.JObject).toProperty() }

            val req = (properties["required"] as? Json.JArray)?.items
                ?.map { (it as Json.JString).value }
                ?: emptyList()

            if (prop.keys.containsAll(req).not()) {
                throw Exception("Required property does not exist in items. ${req} ${prop.keys}")
            }

            Property.PObject(
                title = (properties["title"] as? Json.JString)?.value,
                description = (properties["description"] as? Json.JString)?.value,
                properties = prop,
                required = req
            )
        }

        "array" -> {
            Property.PArray(
                title = (properties["title"] as? Json.JString)?.value,
                description = (properties["items"] as? Json.JString)?.value,
                items = (properties["items"] as Json.JObject).toProperty(),
                minItems = (properties["items"] as? Json.JNumber)?.value?.toInt() ?: 0,
                uniqueItems = (properties["items"] as? Json.JBoolean)
                    ?.toBoolean()
                    ?: false
            )
        }

        "string" ->
            Property.PString(
                title = (properties["title"] as? Json.JString)?.value,
                description = (properties["description"] as? Json.JString)?.value,
                enum = (properties["enum"] as? Json.JArray)?.items
                    ?.map { (it as Json.JString).value },
                minLength = (properties["minLength"] as? Json.JNumber)?.value?.toInt()
            )

        "number" ->
            Property.PNumber(
                title = (properties["title"] as? Json.JString)?.value,
                description = (properties["description"] as? Json.JString)?.value,
                exclusiveMinimum = (properties["exclusiveMinimum"] as? Json.JNumber)?.value
            )

        "integer" ->
            Property.PInteger(
                title = (properties["title"] as? Json.JString)?.value,
                description = (properties["description"] as? Json.JString)?.value,
                minimum = (properties["minimum"] as? Json.JNumber)?.value?.toInt(),
                maximum = (properties["maximum"] as? Json.JNumber)?.value?.toInt()
            )

        else -> {
            throw Exception("invalid type \"$sType\"")
        }
    }
}

fun Property.toType(name: String): String {
    return when (this) {
        is Property.PObject -> name.first().toUpperCase() + name.drop(1)
        is Property.PArray -> "List<${items.toType(name)}?>"
        is Property.PString -> "String"
        is Property.PNumber -> "Double"
        is Property.PInteger -> "Int"
    }
}

fun Property.toSubDataClasses(name: String, level: Int): List<String> =
    when (this) {
        is Property.PObject ->
            listOf(toDataClass(name, level))

        is Property.PArray ->
            items.toSubDataClasses(name, level)

        else ->
            emptyList()
    }

private fun indent(intentLevel: Int): String = "    ".repeat(intentLevel)

fun Property.PObject.toDataClass(name: String, level: Int): String =
    properties
        .map { (k, v) -> indent(level + 1) + "val $k: ${v.toType(k)}?" }
        .joinToString(
            separator = ",\n",
            prefix = indent(level) + "data class $name(\n",
            postfix = run {
                val subDataClasses = properties
                    .flatMap { (k, v) ->
                        v.toSubDataClasses(k.first().toUpperCase() + k.drop(1), level + 1)
                    }

                if (subDataClasses.isEmpty()) {
                    "\n" + indent(level) + ")"
                } else {
                    subDataClasses.joinToString(
                        separator = "\n",
                        prefix = "\n" + indent(level) + ") {\n",
                        postfix = "\n" + indent(level) + "}"
                    )
                }
            }
        )

fun main() {
    val schemaJson = (parseJson
        .parse(
            "{\n" +
                "  \"title\": \"Person\",\n" +
                "  \"type\": \"object\",\n" +
                "  \"items\": {\n" +
                "    \"name\": {\n" +
                "      \"type\": \"string\",\n" +
                "      \"description\": \"First and Last name\",\n" +
                "      \"minLength\": 4,\n" +
                "      \"default\": \"Jeremy Dorn\"\n" +
                "    },\n" +
                "    \"age\": {\n" +
                "      \"type\": \"integer\",\n" +
                "      \"default\": 25,\n" +
                "      \"minimum\": 18,\n" +
                "      \"maximum\": 99\n" +
                "    },\n" +
                "    \"favorite_color\": {\n" +
                "      \"type\": \"string\",\n" +
                "      \"format\": \"color\",\n" +
                "      \"title\": \"favorite color\",\n" +
                "      \"default\": \"#ffa500\"\n" +
                "    },\n" +
                "    \"gender\": {\n" +
                "      \"type\": \"string\",\n" +
                "      \"enum\": [\n" +
                "        \"male\",\n" +
                "        \"female\"\n" +
                "      ]\n" +
                "    },\n" +
                "    \"location\": {\n" +
                "      \"type\": \"object\",\n" +
                "      \"title\": \"Location\",\n" +
                "      \"items\": {\n" +
                "        \"city\": {\n" +
                "          \"type\": \"string\",\n" +
                "          \"default\": \"San Francisco\"\n" +
                "        },\n" +
                "        \"state\": {\n" +
                "          \"type\": \"string\",\n" +
                "          \"default\": \"CA\"\n" +
                "        },\n" +
                "        \"citystate\": {\n" +
                "          \"type\": \"string\",\n" +
                "          \"description\": \"This is generated automatically from the previous two fields\",\n" +
                "          \"template\": \"{{city}}, {{state}}\",\n" +
                "          \"watch\": {\n" +
                "            \"city\": \"location.city\",\n" +
                "            \"state\": \"location.state\"\n" +
                "          }\n" +
                "        }\n" +
                "      }\n" +
                "    },\n" +
                "    \"pets\": {\n" +
                "      \"type\": \"array\",\n" +
                "      \"format\": \"table\",\n" +
                "      \"title\": \"Pets\",\n" +
                "      \"uniqueItems\": true,\n" +
                "      \"items\": {\n" +
                "    \"description\" : \"schema validating people and vehicles\",\n" +
                "    \"type\" : \"object\",\n" +
                "    \"oneOf\" : [{\n" +
                "        \"items\" : {\n" +
                "            \"firstName\" : {\n" +
                "                \"type\" : \"string\"\n" +
                "            },\n" +
                "            \"lastName\" : {\n" +
                "                \"type\" : \"string\"\n" +
                "            },\n" +
                "            \"sport\" : {\n" +
                "                \"type\" : \"string\"\n" +
                "            }\n" +
                "        },\n" +
                "        \"required\" : [\"firstName\"]\n" +
                "    }, {\n" +
                "        \"items\" : {\n" +
                "            \"vehicle\" : {\n" +
                "                \"type\" : \"string\"\n" +
                "            },\n" +
                "            \"price\" : {\n" +
                "                \"type\" : \"integer\"\n" +
                "            }\n" +
                "        },\n" +
                "        \"additionalProperties\":false\n" +
                "    }\n" +
                "]\n" +
                "}\n" +
                "    }\n" +
                "  }\n" +
                "}"
        ) as Either.Ok)
        .value

    (schemaJson.toSchema() as Either.Ok).value
        .let { schema ->
            (schema.property as Property.PObject).toDataClass(schema.title, 0)
        }
        .let { println(it) }
}
