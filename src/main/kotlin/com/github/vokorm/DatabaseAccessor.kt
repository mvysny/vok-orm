package com.github.vokorm

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.sql2o.Sql2o
import org.sql2o.quirks.Quirks
import org.sql2o.quirks.QuirksDetector
import java.io.Closeable

/**
 * Provides access to a SQL database. By default the [PooledDataSourceAccessor] is used, but if you're already using a container
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
     * Example of use: `db { con.query() }`
     * @param block the block to run in the transaction. Builder-style provides helpful methods and values, e.g. [PersistenceContext.con]
     */
    fun <R> runInTransaction(block: PersistenceContext.() -> R): R
}

/**
 * Accesses the database via a [javax.sql.DataSource], using connection pooling as provided by Hikari-CP.
 *
 * It's important to close this accessor properly, since that will clean up the connection pool and close all JDBC connections properly.
 */
class PooledDataSourceAccessor(val cfg: HikariConfig) : DatabaseAccessor {
    val dataSource = HikariDataSource(cfg)
    override fun close() {
        dataSource.close()
    }

    private val HikariConfig.quirks: Quirks
        get() = when {
            !jdbcUrl.isNullOrBlank() -> QuirksDetector.forURL(jdbcUrl)
            dataSource != null -> QuirksDetector.forObject(dataSource)
            else -> throw IllegalStateException("HikariConfig: both jdbcUrl and dataSource is null! $this")
        }

    override fun <R> runInTransaction(block: PersistenceContext.() -> R): R {
        var context = contexts.get()
        // if we're already running in a transaction, just run the block right away.
        if (context != null) return context.block()

        val sql2o = Sql2o(dataSource, cfg.quirks)
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
