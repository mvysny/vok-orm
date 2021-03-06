package com.github.vokorm

import com.gitlab.mvysny.jdbiorm.JdbiOrm.jdbi
import org.jdbi.v3.core.Handle
import java.sql.Connection

/**
 * Provides access to a single JDBC connection and its [Handle], and several utility methods.
 *
 * The [db] function executes block in context of this class.
 * @property handle the reference to the JDBI's [Handle]. Typically you'd want to call [Handle.createQuery]
 * or [Handle.createUpdate] on the connection.
 * @property jdbcConnection the old-school, underlying JDBC connection.
 */
public class PersistenceContext(public val handle: Handle) {
    /**
     * The underlying JDBC connection.
     */
    public val jdbcConnection: Connection get() = handle.connection
}

/**
 * Makes sure given block is executed in a DB transaction. When the block finishes normally, the transaction commits;
 * if the block throws any exception, the transaction is rolled back.
 *
 * Example of use: `db { con.query() }`
 * @param block the block to run in the transaction. Builder-style provides helpful methods and values, e.g. [PersistenceContext.handle]
 */
public fun <R> db(block: PersistenceContext.()->R): R = jdbi().inTransaction<R, Exception> { handle ->
    PersistenceContext(handle).block()
}
