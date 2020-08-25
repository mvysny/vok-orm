package com.github.vokorm

import org.jdbi.v3.core.result.ResultIterator
import org.jdbi.v3.core.statement.Query
import java.sql.ResultSet
import java.sql.ResultSetMetaData

/**
 * Dumps the result of the query and returns it as a string formatted as follows:
 * ```
 * id, name, age, dateOfBirth, created, modified, alive, maritalStatus
 * -------------------------------------------------------------------
 * 1, Chuck Norris, 25, null, 2018-11-23 20:41:07.143, 2018-11-23 20:41:07.145, null, null
 * -------------------------------------------------------------------1 row(s)
 * ```
 * @return a pretty-printed outcome of given select
 */
public fun Query.dump(): String {
    fun ResultSet.dumpCurrentRow(): String = (0 until metaData.columnCount).joinToString { "${getObject(it + 1)}" }

    val rows: ResultIterator<String> = map { rs: ResultSet, _ -> rs.dumpCurrentRow() }.iterator()
    val metadata: ResultSetMetaData = rows.context.statement.metaData
    return buildString {

        // draw the header and the separator
        val header: String = (0 until metadata.columnCount).joinToString { metadata.getColumnName(it + 1) }
        appendLine(header)
        repeat(header.length) { append("-") }
        appendLine()

        // draw the table body
        var rowCount = 0
        rows.forEach { row: String -> rowCount++; appendLine(row) }

        // the bottom separator
        repeat(header.length) { append("-") }
        appendLine("$rowCount row(s)")
    }
}
