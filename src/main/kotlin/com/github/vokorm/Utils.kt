package com.github.vokorm

import org.slf4j.LoggerFactory
import java.io.*
import java.util.*
import javax.validation.ConstraintViolation
import javax.validation.ValidationException
import javax.validation.Validator
import javax.validation.executable.ExecutableValidator

fun Closeable.closeQuietly() {
    try {
        close()
    } catch (e: Exception) {
        LoggerFactory.getLogger(javaClass).info("Failed to close $this", e)
    }
}

/**
 * No-op validator which always passes all objects.
 */
object NoopValidator : Validator {
    override fun <T : Any?> validate(`object`: T, vararg groups: Class<*>?): MutableSet<ConstraintViolation<T>> = mutableSetOf()

    override fun <T : Any?> validateValue(
        beanType: Class<T>?,
        propertyName: String?,
        value: Any?,
        vararg groups: Class<*>?
    ): MutableSet<ConstraintViolation<T>> = mutableSetOf()

    override fun <T : Any?> validateProperty(
        `object`: T,
        propertyName: String?,
        vararg groups: Class<*>?
    ): MutableSet<ConstraintViolation<T>> = mutableSetOf()

    override fun getConstraintsForClass(clazz: Class<*>?) = throw UnsupportedOperationException("unimplemented")

    override fun <T : Any?> unwrap(type: Class<T>?): T = throw ValidationException("unsupported $type")

    override fun forExecutables(): ExecutableValidator = throw UnsupportedOperationException("unimplemented")
}

/**
 * Converts UUID to a byte array with the length of 16. Writes [UUID.getMostSignificantBits] first, then
 * [UUID.getLeastSignificantBits].
 */
fun UUID.toByteArray(): ByteArray = ByteArrayOutputStream().apply {
    val dout = DataOutputStream(this)
    dout.writeLong(mostSignificantBits)
    dout.writeLong(leastSignificantBits)
}.toByteArray()

/**
 * Reads UUID from [bytes].
 * @param bytes 16-byte-long byte array; first is the [UUID.getMostSignificantBits], then [UUID.getLeastSignificantBits].
 */
fun uuidFromByteArray(bytes: ByteArray): UUID {
    check(bytes.size == 16) { "Expected 16-bytes array but got ${bytes.size}" }
    val din = DataInputStream(ByteArrayInputStream(bytes))
    return UUID(din.readLong(), din.readLong())
}
