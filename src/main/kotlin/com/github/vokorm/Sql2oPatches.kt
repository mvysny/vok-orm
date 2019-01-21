package com.github.vokorm

import org.sql2o.converters.Converter
import org.sql2o.converters.ConverterException
import org.sql2o.converters.ConvertersProvider
import org.sql2o.quirks.NoQuirks
import org.sql2o.quirks.Quirks
import org.sql2o.quirks.QuirksProvider
import org.sql2o.reflection.PojoMetadata
import java.lang.RuntimeException
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.*

private class LocalDateConverter : Converter<LocalDate> {
    override fun toDatabaseParam(`val`: LocalDate?): Any? = `val`  // jdbc 4.2 supports LocalDate fully
    override fun convert(value: Any?): LocalDate? = when (value) {
        null -> null
        is LocalDate -> value
        is java.sql.Date -> value.toLocalDate()
        is java.util.Date -> value.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
        else -> throw ConverterException("Failed to convert $value of type ${value.javaClass} to LocalDate")
    }
}

private class InstantConverter : Converter<Instant> {
    override fun toDatabaseParam(`val`: Instant?): Any? = when (`val`) {
        null -> null
        else -> Timestamp(`val`.toEpochMilli())
    }
    override fun convert(value: Any?): Instant? = when (value) {
        null -> null
        is Instant -> value
        is java.util.Date -> value.toInstant()
        else -> throw ConverterException("Failed to convert $value of type ${value.javaClass} to Instant")
    }
}

class VokConvertersProvider : ConvertersProvider {
    override fun fill(mapToFill: MutableMap<Class<*>, Converter<*>>) {
        mapToFill.apply {
            put(LocalDate::class.java, LocalDateConverter())
            put(Instant::class.java, InstantConverter())
        }
    }
}

/**
 * Converts  [UUID] to [ByteArray] to be able to store UUID into binary(16).
 * See https://github.com/mvysny/vok-orm/issues/8 for more details.
 */
private class MysqlUuidConverter : Converter<UUID> {
    override fun toDatabaseParam(`val`: UUID?): Any? = `val`?.toByteArray()

    override fun convert(`val`: Any?): UUID? = when(`val`) {
        null -> null
        is ByteArray -> uuidFromByteArray(`val`)
        is UUID -> `val`
        else -> throw IllegalArgumentException("Failed to convert $`val` of type ${`val`.javaClass} to UUID")
    }
}

/**
 * Works around MySQL drivers not able to convert [UUID] to [ByteArray].
 * See https://github.com/mvysny/vok-orm/issues/8 for more details.
 */
object MysqlQuirks : NoQuirks(mapOf(UUID::class.java to MysqlUuidConverter())) {

    override fun <E : Any?> converterOf(ofClass: Class<E>): Converter<E>? {
        if (ofClass.implements(Entity::class.java)) {
            // we need to know the current entity being read from the ResultSet (by the
            // DefaultResultSetHandlerFactory:144), in order to reliably detect whether the column asked
            // in getRSVal() is the ID column with mis-detected type. See getRSVal() below for more details.
            currentEntity.set(ofClass)
        }
        return super.converterOf(ofClass)
    }

    override fun getRSVal(rs: ResultSet, idx: Int): Any? {
        val rsval: Any? = super.getRSVal(rs, idx)
        // here the issue is that Sql2o may misdetect the type of the ID column as Object
        // that is because there are two setId() methods on every Entity:
        // 1. setId(Object); and
        // 2. setId(T);
        // depending on the order of methods in Java reflection, Sql2o may pick the incorrect one
        // and may store it into its PojoMetadata.
        // I failed to hook into PojoMetadata and fix the type there.
        //
        // This is just a dumb workaround: I'll simply run the converter myself.
        if (rsval != null) {
            val entityClass = currentEntity.get()!!
            val dbColumnName = getColumnName(rs.metaData, idx)
            val idProperty = entityClass.entityMeta.idProperty
            val isIdColumn = idProperty.dbColumnName == dbColumnName
            if (isIdColumn) {
                val metadata = PojoMetadata(entityClass, false, false, mapOf(), true)
                val isIdTypeMisdetected = metadata.getPropertySetter(idProperty.name).type == Object::class.java
                if (isIdTypeMisdetected) {
                    val converter = converterOf(idProperty.valueType)
                    if (converter != null) {
                        return try {
                            converter.convert(rsval)
                        } catch (e: Exception) {
                            throw RuntimeException("Failed to convert $rsval for entity $e ID column $idProperty idx=$idx colName=$dbColumnName")
                        }
                    }
                }
            }
        }
        return rsval
    }

    private val currentEntity = ThreadLocal<Class<*>>()
}

/**
 * Provides specialized quirks for MySQL. See [MysqlQuirks] for more details.
 */
class VokOrmQuirksProvider : QuirksProvider {
    override fun isUsableForClass(className: String): Boolean = className.startsWith("com.mysql.") || className.startsWith("org.mariadb.jdbc.")
    override fun provide(): Quirks = MysqlQuirks
    override fun isUsableForUrl(url: String): Boolean = url.startsWith("jdbc:mysql:") || url.startsWith("jdbc:mariadb:")
}
