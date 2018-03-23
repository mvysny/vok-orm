package com.github.vokorm

import org.slf4j.LoggerFactory
import java.io.Closeable
import javax.validation.ConstraintViolation
import javax.validation.ValidationException
import javax.validation.Validator
import javax.validation.executable.ExecutableValidator
import javax.validation.metadata.BeanDescriptor

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

    override fun getConstraintsForClass(clazz: Class<*>?): BeanDescriptor {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun <T : Any?> unwrap(type: Class<T>?): T = throw ValidationException("unsupported $type")

    override fun forExecutables(): ExecutableValidator = throw UnsupportedOperationException("unimplemented")
}