package com.github.vokorm

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.sql2o.Connection
import org.sql2o.Sql2o
import org.sql2o.converters.Converter
import org.sql2o.converters.ConverterException
import org.sql2o.converters.ConvertersProvider
import org.sql2o.quirks.Quirks
import org.sql2o.quirks.QuirksDetector
import java.io.Closeable
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Initializes the ORM in the current JVM. Just fill in [dataSourceConfig] properly and then call [init] once per JVM. When the database services
 * are no longer needed, call [destroy] to release all JDBC connections and close the pool.
 */
object VokOrm {
    /**
     * First, fill in [dataSourceConfig] properly and then call this function once per JVM.
     */
    fun init() {
        check(!dataSourceConfig.jdbcUrl.isNullOrBlank()) { "Please set your database JDBC url, username and password into the VaadinOnKotlin.dataSourceConfig field prior initializing VoK. " }
        dataSource = HikariDataSource(dataSourceConfig)
    }

    /**
     * Closes
     */
    fun destroy() {
        dataSource?.closeQuietly()
        dataSource = null
    }

    @Volatile
    var dataSource: HikariDataSource? = null

    /**
     * Configure this before calling [init]. At minimum you need to set [HikariConfig.dataSource], or
     * [HikariConfig.driverClassName], [HikariConfig.jdbcUrl], [HikariConfig.username] and [HikariConfig.password].
     */
    val dataSourceConfig = HikariConfig()
}

/**
 * Provides access to a single JDBC connection and its [Connection], and several utility methods.
 *
 * The [db] function executes block in context of this class.
 * @property em the entity manager reference
 */
class PersistenceContext(val con: Connection) : Closeable {
    /**
     * The underlying JDBC connection.
     */
    val jdbcConnection: java.sql.Connection get() = con.jdbcConnection

    override fun close() {
        con.close()
    }
}

private val contexts: ThreadLocal<PersistenceContext> = ThreadLocal()

private val HikariConfig.quirks: Quirks get() = when {
    !jdbcUrl.isNullOrBlank() -> QuirksDetector.forURL(jdbcUrl)
    dataSource != null -> QuirksDetector.forObject(dataSource)
    else -> throw IllegalStateException("HikariConfig: both jdbcUrl and dataSource is null! $this")
}

/**
 * Makes sure given block is executed in a DB transaction. When the block finishes normally, the transaction commits;
 * if the block throws any exception, the transaction is rolled back.
 *
 * Example of use: `db { con.query() }`
 * @param block the block to run in the transaction. Builder-style provides helpful methods and values, e.g. [PersistenceContext.con]
 */
fun <R> db(block: PersistenceContext.()->R): R {
    var context = contexts.get()
    // if we're already running in a transaction, just run the block right away.
    if (context != null) return context.block()

    val dataSource = checkNotNull(VokOrm.dataSource) { "The VokOrm.dataSource has not yet been initialized. Please call VokOrm.init()" }
    val sql2o = Sql2o(dataSource, VokOrm.dataSourceConfig.quirks)
    context = PersistenceContext(sql2o.beginTransaction())
    try {
        contexts.set(context)
        return context.use {
            try {
                val result: R = context.block()
                context.con.commit()
                result
            } catch (t: Throwable) {
                try {
                    context.con.rollback()
                } catch (rt: Throwable) {
                    t.addSuppressed(rt)
                }
                throw t
            }
        }
    } finally {
        contexts.set(null)
    }
}

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
