package com.bysel.trader.data.api

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.ToNumberPolicy
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.lang.reflect.Type

/**
 * Gson that tolerates dirty market-data payloads:
 * - null / "" / "null" / blank → 0 (primitives) or null (boxed)
 * - numeric strings → parsed
 * - JSON doubles into Long fields → truncated safely
 *
 * Prevents Home crashes like:
 * NumberFormatException: For input string: ""
 * NumberFormatException: For input string: "null"
 * (common when /quotes returns `"timestamp": null`)
 */
object SafeGsonFactory {
    fun create(): Gson {
        return GsonBuilder()
            .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
            .setNumberToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
            // Boxed
            .registerTypeAdapter(java.lang.Long::class.java, NullableLongAdapter)
            .registerTypeAdapter(java.lang.Integer::class.java, NullableIntAdapter)
            .registerTypeAdapter(java.lang.Double::class.java, NullableDoubleAdapter)
            .registerTypeAdapter(java.lang.Float::class.java, NullableFloatAdapter)
            // Kotlin non-null / JVM primitives — never return null
            .registerTypeAdapter(Long::class.javaPrimitiveType, PrimitiveLongAdapter)
            .registerTypeAdapter(Int::class.javaPrimitiveType, PrimitiveIntAdapter)
            .registerTypeAdapter(Double::class.javaPrimitiveType, PrimitiveDoubleAdapter)
            .registerTypeAdapter(Float::class.javaPrimitiveType, PrimitiveFloatAdapter)
            // Kotlin Long/Int/Double without ? still resolve to boxed in some paths
            .registerTypeAdapter(Long::class.javaObjectType, NullableLongAdapter)
            .registerTypeAdapter(Int::class.javaObjectType, NullableIntAdapter)
            .registerTypeAdapter(Double::class.javaObjectType, NullableDoubleAdapter)
            .registerTypeAdapter(Float::class.javaObjectType, NullableFloatAdapter)
            .create()
    }
}

private object PrimitiveLongAdapter : TypeAdapter<Long>() {
    override fun write(out: JsonWriter, value: Long?) {
        out.value(value ?: 0L)
    }

    override fun read(reader: JsonReader): Long = readLong(reader) ?: 0L
}

private object NullableLongAdapter :
    TypeAdapter<Long?>(),
    JsonDeserializer<Long?>,
    JsonSerializer<Long?> {
    override fun write(out: JsonWriter, value: Long?) {
        if (value == null) out.nullValue() else out.value(value)
    }

    override fun read(reader: JsonReader): Long? = readLong(reader)

    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): Long? {
        return elementToLong(json)
    }

    override fun serialize(src: Long?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
        return if (src == null) JsonNull.INSTANCE else JsonPrimitive(src)
    }
}

private object PrimitiveIntAdapter : TypeAdapter<Int>() {
    override fun write(out: JsonWriter, value: Int?) {
        out.value(value ?: 0)
    }

    override fun read(reader: JsonReader): Int = readLong(reader)?.toInt() ?: 0
}

private object NullableIntAdapter :
    TypeAdapter<Int?>(),
    JsonDeserializer<Int?>,
    JsonSerializer<Int?> {
    override fun write(out: JsonWriter, value: Int?) {
        if (value == null) out.nullValue() else out.value(value)
    }

    override fun read(reader: JsonReader): Int? = readLong(reader)?.toInt()

    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): Int? {
        return elementToLong(json)?.toInt()
    }

    override fun serialize(src: Int?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
        return if (src == null) JsonNull.INSTANCE else JsonPrimitive(src)
    }
}

private object PrimitiveDoubleAdapter : TypeAdapter<Double>() {
    override fun write(out: JsonWriter, value: Double?) {
        out.value(value ?: 0.0)
    }

    override fun read(reader: JsonReader): Double = readDouble(reader) ?: 0.0
}

private object NullableDoubleAdapter :
    TypeAdapter<Double?>(),
    JsonDeserializer<Double?>,
    JsonSerializer<Double?> {
    override fun write(out: JsonWriter, value: Double?) {
        if (value == null) out.nullValue() else out.value(value)
    }

    override fun read(reader: JsonReader): Double? = readDouble(reader)

    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): Double? {
        return elementToDouble(json)
    }

    override fun serialize(src: Double?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
        return if (src == null) JsonNull.INSTANCE else JsonPrimitive(src)
    }
}

private object PrimitiveFloatAdapter : TypeAdapter<Float>() {
    override fun write(out: JsonWriter, value: Float?) {
        out.value(value ?: 0f)
    }

    override fun read(reader: JsonReader): Float = readDouble(reader)?.toFloat() ?: 0f
}

private object NullableFloatAdapter :
    TypeAdapter<Float?>(),
    JsonDeserializer<Float?>,
    JsonSerializer<Float?> {
    override fun write(out: JsonWriter, value: Float?) {
        if (value == null) out.nullValue() else out.value(value)
    }

    override fun read(reader: JsonReader): Float? = readDouble(reader)?.toFloat()

    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): Float? {
        return elementToDouble(json)?.toFloat()
    }

    override fun serialize(src: Float?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
        return if (src == null) JsonNull.INSTANCE else JsonPrimitive(src)
    }
}

private fun readLong(reader: JsonReader): Long? {
    return when (reader.peek()) {
        JsonToken.NULL -> {
            reader.nextNull()
            null
        }
        JsonToken.NUMBER -> reader.nextDouble().toLong()
        JsonToken.STRING -> parseLong(reader.nextString())
        JsonToken.BOOLEAN -> if (reader.nextBoolean()) 1L else 0L
        else -> {
            reader.skipValue()
            null
        }
    }
}

private fun readDouble(reader: JsonReader): Double? {
    return when (reader.peek()) {
        JsonToken.NULL -> {
            reader.nextNull()
            null
        }
        JsonToken.NUMBER -> reader.nextDouble()
        JsonToken.STRING -> parseDouble(reader.nextString())
        JsonToken.BOOLEAN -> if (reader.nextBoolean()) 1.0 else 0.0
        else -> {
            reader.skipValue()
            null
        }
    }
}

private fun elementToLong(json: JsonElement?): Long? {
    if (json == null || json.isJsonNull) return null
    if (!json.isJsonPrimitive) return null
    val p = json.asJsonPrimitive
    return when {
        p.isNumber -> p.asDouble.toLong()
        p.isString -> parseLong(p.asString)
        p.isBoolean -> if (p.asBoolean) 1L else 0L
        else -> null
    }
}

private fun elementToDouble(json: JsonElement?): Double? {
    if (json == null || json.isJsonNull) return null
    if (!json.isJsonPrimitive) return null
    val p = json.asJsonPrimitive
    return when {
        p.isNumber -> p.asDouble
        p.isString -> parseDouble(p.asString)
        p.isBoolean -> if (p.asBoolean) 1.0 else 0.0
        else -> null
    }
}

private fun parseLong(raw: String?): Long? {
    val cleaned = raw
        ?.trim()
        ?.removePrefix("+")
        ?.replace(",", "")
        ?.replace(" ", "")
        ?.removeSuffix("%")
        .orEmpty()
    if (cleaned.isEmpty() ||
        cleaned.equals("null", ignoreCase = true) ||
        cleaned.equals("nan", ignoreCase = true) ||
        cleaned.equals("undefined", ignoreCase = true)
    ) {
        return null
    }
    cleaned.toLongOrNull()?.let { return it }
    cleaned.toDoubleOrNull()?.let { return it.toLong() }
    return null
}

private fun parseDouble(raw: String?): Double? {
    val cleaned = raw
        ?.trim()
        ?.removePrefix("+")
        ?.replace(",", "")
        ?.replace(" ", "")
        ?.removeSuffix("%")
        .orEmpty()
    if (cleaned.isEmpty() ||
        cleaned.equals("null", ignoreCase = true) ||
        cleaned.equals("nan", ignoreCase = true) ||
        cleaned.equals("undefined", ignoreCase = true)
    ) {
        return null
    }
    return cleaned.toDoubleOrNull()
}
