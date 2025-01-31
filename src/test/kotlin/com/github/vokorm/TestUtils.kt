package com.github.vokorm

import com.fatboyindustrial.gsonjavatime.Converters
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.assertThrows
import org.testcontainers.DockerClientFactory
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.Date
import kotlin.test.expect

val gson: Gson = GsonBuilder().registerJavaTimeAdapters().create()

private fun GsonBuilder.registerJavaTimeAdapters(): GsonBuilder = apply {
    Converters.registerAll(this)
}

/**
 * Expects that [actual] list of objects matches [expected] list of objects. Fails otherwise.
 */
fun <T> expectList(vararg expected: T, actual: ()->List<T>) {
    expect(expected.toList(), actual)
}

inline fun <reified E: Throwable> expectThrows(msg: String, block: () -> Unit) {
    val ex = assertThrows<E>(block)
    expect(true) { ex.message!!.contains(msg) }
}

/**
 * Clones this object by serialization and returns the deserialized clone.
 * @return the clone of this
 */
fun <T : Serializable> T.cloneBySerialization(): T = javaClass.cast(serializeToBytes().deserialize())

inline fun <reified T: Serializable> ByteArray.deserialize(): T? = T::class.java.cast(
    ObjectInputStream(inputStream()).readObject())

/**
 * Serializes the object to a byte array
 * @return the byte array containing this object serialized form.
 */
fun Serializable?.serializeToBytes(): ByteArray = ByteArrayOutputStream().also { ObjectOutputStream(it).writeObject(this) }.toByteArray()

fun assumeDockerAvailable() {
    Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker not available")
}

// MSSQL nulls out millis for some reason when running on CI
val Date.withZeroMillis: Date get() {
    val result = Timestamp((this as Timestamp).time / 1000 * 1000)
    result.nanos = 0
    return result
}

val Instant.withZeroNanos: Instant get() = Instant.ofEpochMilli(toEpochMilli())
val <T> List<T>.plusNull: List<T?> get() = toList<T?>() + listOf(null)
