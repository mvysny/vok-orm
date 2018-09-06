package com.github.vokorm

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.sql2o.Sql2o
import org.sql2o.quirks.Quirks
import org.sql2o.quirks.QuirksDetector
import java.io.Closeable
import javax.sql.DataSource

/**
 * Provides access to a SQL database. By default the [HikariDataSourceAccessor] is used, but if you're already using a container
 * which manages the transactions (e.g. Spring or JavaEE) you'll want a custom implementation of this.
 *
 * Closeable - will be closed when [VokOrm.destroy] is called.
 */
interface DatabaseAccessor : Closeable {
    /**
     * Makes sure given block is executed in a DB transaction. When the block finishes normally, the transaction commits;
     * if the block throws any exception, the transaction is rolled back.
     *
     * Doesn't start new transaction if there already is transaction ongoing.
     *
     * ## Implementation instructions
     *
     * To obtain the Sql2o's Connection from a [DataSource] and the JDBC URL, you'll need to do the following:
     *
     * 1. Obtain the [Quirks] instance, either from a JDBC URL ([QuirksDetector.forURL]) or from the DataSource itself
     * ([QuirksDetector.forObject]).
     * 2. Create the [Sql2o] instance: `Sql2o(dataSource, quirks)`
     * 3. Call [Sql2o.open] to get the Sql2o [org.sql2o.Connection] without doing any transactional management.
     * 4. Pass the [org.sql2o.Connection] to the constructor of the [PersistenceContext].
     *
     * @param block the block to run in the transaction. Builder-style provides helpful methods and values, e.g. [PersistenceContext.con]
     */
    fun <R> runInTransaction(block: PersistenceContext.() -> R): R
}

val HikariConfig.quirks: Quirks
    get() = when {
        !jdbcUrl.isNullOrBlank() -> QuirksDetector.forURL(jdbcUrl)
        dataSource != null -> QuirksDetector.forObject(dataSource)
        else -> throw IllegalStateException("HikariConfig: both jdbcUrl and dataSource is null! $this")
    }

/**
 * Accesses the database via a [javax.sql.DataSource], using connection pooling as provided by Hikari-CP.
 *
 * It's important to close this accessor properly, since that will clean up the connection pool and close all JDBC connections properly.
 */
class HikariDataSourceAccessor(val dataSource: HikariDataSource) : DatabaseAccessor {
    private val delegate = DataSourceAccessor(dataSource, dataSource.quirks)
    override fun close() {
        delegate.close()
        dataSource.close()
    }

    override fun <R> runInTransaction(block: PersistenceContext.() -> R): R =
            delegate.runInTransaction(block)
}

/**
 * Accesses the database via given [dataSource]. Does not use any pooling and does not close the data source.
 * @param quirks the database access quirks to use, defaults to auto-detected.
 */
class DataSourceAccessor(val dataSource: DataSource, val quirks: Quirks = QuirksDetector.forObject(dataSource)) : DatabaseAccessor {

    /**
     * Auto-detects quirks from given [jdbcURL].
     */
    constructor(dataSource: DataSource, jdbcURL: String) : this(dataSource, QuirksDetector.forURL(jdbcURL))

    override fun close() {}

    override fun <R> runInTransaction(block: PersistenceContext.() -> R): R {
        var context = contexts.get()
        // if we're already running in a transaction, just run the block right away.
        if (context != null) return context.block()

        val sql2o = Sql2o(dataSource, quirks)
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

    companion object {
        /**
         * Holds the [PersistenceContext] for this thread, so that we know that a transaction has already been started in this thread.
         */
        private val contexts: ThreadLocal<PersistenceContext> = ThreadLocal()
    }
}
