package com.github.vokorm

import com.github.vokorm.VokOrm.dataSourceConfig
import com.github.vokorm.VokOrm.databaseAccessorProvider
import com.github.vokorm.VokOrm.destroy
import com.github.vokorm.VokOrm.init
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.slf4j.LoggerFactory
import org.sql2o.Connection
import org.sql2o.converters.Converter
import org.sql2o.converters.ConverterException
import org.sql2o.converters.ConvertersProvider
import org.sql2o.quirks.NoQuirks
import org.sql2o.quirks.Quirks
import org.sql2o.quirks.QuirksProvider
import java.io.Closeable
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.*
import javax.sql.DataSource
import javax.validation.NoProviderFoundException
import javax.validation.Validation
import javax.validation.Validator

/**
 * Initializes the ORM in the current JVM. By default uses the [HikariDataSourceAccessor] which uses [javax.sql.DataSource] pooled with HikariCP.
 * To configure this accessor, just fill in [dataSourceConfig] properly and then call [init] once per JVM. When the database services
 * are no longer needed, call [destroy] to release all JDBC connections and close the pool.
 *
 * If you're using a customized [DatabaseAccessor], you don't have to fill in the [dataSourceConfig]. Just set proper [databaseAccessorProvider]
 * and then call [init].
 */
object VokOrm {
    /**
     * First, fill in [dataSourceConfig] properly and then call this function once per JVM.
     */
    fun init() {
        databaseAccessor = databaseAccessorProvider()
    }

    /**
     * Closes the current [databaseAccessor]. Does nothing if [databaseAccessor] is null.
     */
    fun destroy() {
        databaseAccessor?.closeQuietly()
        databaseAccessor = null
    }

    @Volatile
    var databaseAccessorProvider: ()->DatabaseAccessor = {
        check(!dataSourceConfig.jdbcUrl.isNullOrBlank()) { "Please set your database JDBC url, username and password into the VaadinOnKotlin.dataSourceConfig field prior initializing VoK. " }
        HikariDataSourceAccessor(HikariDataSource(dataSourceConfig))
    }

    /**
     * After [init] has been called, this will be filled in. Used to run blocks in a transaction. Closed in [destroy].
     */
    @Volatile
    var databaseAccessor: DatabaseAccessor? = null

    val dataSource: DataSource? get() = databaseAccessor?.dataSource

    /**
     * Configure this before calling [init]. At minimum you need to set [HikariConfig.dataSource], or
     * [HikariConfig.driverClassName], [HikariConfig.jdbcUrl], [HikariConfig.username] and [HikariConfig.password].
     *
     * Only used by the [HikariDataSourceAccessor] - if you are using your own custom [DatabaseAccessor] you don't have to fill in anything here.
     */
    val dataSourceConfig = HikariConfig()

    /**
     * The validator used by [Entity.validate]. By default tries to build the default validation factory; if there is no provider, a no-op
     * validator is used instead.
     */
    var validator: Validator = try {
        Validation.buildDefaultValidatorFactory().validator
    } catch (ex: NoProviderFoundException) {
        LoggerFactory.getLogger(VokOrm::class.java).warn("vok-orm failed to build the default validator, using no-op", ex)
        NoopValidator
    }
}

/**
 * Provides access to a single JDBC connection and its [Connection], and several utility methods.
 *
 * The [db] function executes block in context of this class.
 * @property con the reference to the [Sql2o](https://www.sql2o.org)'s [Connection]. Typically you'd want to call one of the `createQuery()`
 * methods on the connection.
 * @property jdbcConnection the old-school, underlying JDBC connection.
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

/**
 * Makes sure given block is executed in a DB transaction. When the block finishes normally, the transaction commits;
 * if the block throws any exception, the transaction is rolled back.
 *
 * Example of use: `db { con.query() }`
 * @param block the block to run in the transaction. Builder-style provides helpful methods and values, e.g. [PersistenceContext.con]
 */
fun <R> db(block: PersistenceContext.()->R): R {
    val accessor = checkNotNull(VokOrm.databaseAccessor) { "The VokOrm.databaseAccessor has not yet been initialized. Please call VokOrm.init()" }
    return accessor.runInTransaction(block)
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
        else -> throw IllegalArgumentException("Failed to convert $`val` to UUID")
    }
}

/**
 * Works around MySQL drivers not able to convert [UUID] to [ByteArray].
 * See https://github.com/mvysny/vok-orm/issues/8 for more details.
 */
class MysqlQuirks : NoQuirks(mapOf(UUID::class.java to MysqlUuidConverter()))

/**
 * Provides specialized quirks for MySQL. See [MysqlQuirks] for more details.
 */
class VokOrmQuirksProvider : QuirksProvider {
    override fun forURL(jdbcUrl: String): Quirks? = when {
        jdbcUrl.startsWith("jdbc:mysql:") || jdbcUrl.startsWith("jdbc:mariadb:") -> MysqlQuirks()
        else -> null
    }

    override fun forObject(jdbcObject: Any): Quirks? {
        val className = jdbcObject.javaClass.canonicalName
        return when {
            className.startsWith("com.mysql.") || className.startsWith("org.mariadb.jdbc.") -> MysqlQuirks()
            else -> null
        }
    }
}
