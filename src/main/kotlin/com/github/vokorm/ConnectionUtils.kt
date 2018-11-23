package com.github.vokorm

import com.github.mvysny.vokdataloader.Filter
import com.github.mvysny.vokdataloader.SqlWhereBuilder
import org.sql2o.Connection
import org.sql2o.Query
import org.sql2o.data.LazyTable
import org.sql2o.data.Row

/**
 * Finds all instances of given entity. Fails if there is no table in the database with the name of [EntityMeta.databaseTableName]. The list is eager
 * and thus it's useful for smallish tables only.
 */
fun <T : Any> Connection.findAll(clazz: Class<T>): List<T> =
    createQuery("select * from ${clazz.entityMeta.databaseTableName}")
        .setColumnMappings(clazz.entityMeta.getSql2oColumnMappings())
        .executeAndFetch(clazz)

/**
 * Retrieves entity with given [id]. Returns null if there is no such entity.
 */
fun <T : Any> Connection.findById(clazz: Class<T>, id: Any): T? =
    createQuery("select * from ${clazz.entityMeta.databaseTableName} where id = :id")
        .addParameter("id", id)
        .setColumnMappings(clazz.entityMeta.getSql2oColumnMappings())
        .executeAndFetchFirst(clazz)

/**
 * Retrieves entity with given [id]. Fails if there is no such entity.
 * @throws IllegalArgumentException if there is no entity with given id.
 */
fun <T : Any> Connection.getById(clazz: Class<T>, id: Any): T =
    requireNotNull(findById(clazz, id)) { "There is no ${clazz.simpleName} for id $id" }

/**
 * Deletes all rows from given database table.
 */
fun <T: Any> Connection.deleteAll(clazz: Class<T>) {
    createQuery("delete from ${clazz.entityMeta.databaseTableName}").executeUpdate()
}

/**
 * Counts all rows in given table [clazz].
 */
fun <T: Any> Connection.getCount(clazz: Class<T>): Long =
    createQuery("select count(*) from ${clazz.entityMeta.databaseTableName}").executeScalar(Long::class.java)

/**
 * Counts all rows in given table [clazz] satisfying given [filter].
 */
fun <T: Any> Connection.getCount(clazz: Class<T>, filter: Filter<T>): Long {
    val sql = filter.toParametrizedSql(clazz)
    val query: Query = createQuery("select count(*) from ${clazz.entityMeta.databaseTableName} where ${sql.sql92}")
    sql.sql92Parameters.entries.forEach { (name, value) -> query.addParameter(name, value) }
    return query.executeScalar(Long::class.java)
}

/**
 * Retrieves specific entity matching given criteria [filter]. Fails if there are two or more entities matching the criteria.
 *
 * This function returns `null` if there is no such entity. Use [getBy] if you wish an exception to be thrown in case that
 * the entity does not exist.
 * @throws IllegalArgumentException if there are two or more matching entities.
 */
fun <T: Any> Connection.findSpecificBy(clazz: Class<T>, filter: Filter<T>): T? {
    val result: List<T> = findBy(clazz, 2, filter)
    require(result.size < 2) { "too many ${clazz.simpleName} satisfying ${filter.toParametrizedSql(clazz)}: $result and perhaps more" }
    return result.firstOrNull()
}

/**
 * Returns the one entity with given [clazz] matching given [filter].
 * @throws IllegalArgumentException if there is none entity matching, or if there are two or more matching entities.
 */
fun <T: Any> Connection.getBy(clazz: Class<T>, filter: Filter<T>): T =
    findSpecificBy(clazz, filter) ?: throw IllegalArgumentException("no ${clazz.simpleName} satisfying ${filter.toParametrizedSql(clazz)}")

/**
 * Returns a list of entities with given [clazz] matching given [filter].
 * @param limit limit the number of returned entities. Must be 0 or greater.
 * @return a list, may be empty.
 */
fun <T: Any> Connection.findBy(clazz: Class<T>, limit: Int, filter: Filter<T>): List<T> {
    require (limit >= 0) { "$limit is less than 0" }
    val sql = filter.toParametrizedSql(clazz)
    val query = createQuery("select * from ${clazz.entityMeta.databaseTableName} where ${sql.sql92} limit $limit")
    sql.sql92Parameters.entries.forEach { (name, value) -> query.addParameter(name, value) }
    query.setColumnMappings(clazz.entityMeta.getSql2oColumnMappings())
    return query.executeAndFetch(clazz)
}

/**
 * Deletes all entities with given [clazz] matching given criteria [block].
 */
fun <T: Any> Connection.deleteBy(clazz: Class<T>, block: SqlWhereBuilder<T>.()-> Filter<T>) {
    deleteBy(clazz, block(SqlWhereBuilder(clazz)))
}

/**
 * Deletes all entities with given [clazz] matching given criteria [filter].
 */
fun <T: Any> Connection.deleteBy(clazz: Class<T>, filter: Filter<T>) {
    val sql = filter.toParametrizedSql(clazz)
    val query = createQuery("delete from ${clazz.entityMeta.databaseTableName} where ${sql.sql92}")
    sql.sql92Parameters.entries.forEach { (name, value) -> query.addParameter(name, value) }
    query.executeUpdate()
}

/**
 * Deletes the entity [clazz] with given [id].
 */
fun <T: Any> Connection.deleteById(clazz: Class<T>, id: Any) {
    createQuery("delete from ${clazz.entityMeta.databaseTableName} where ${clazz.entityMeta.idProperty.dbColumnName}=:id")
        .addParameter("id", id)
        .executeUpdate()
}

/**
 * Checks whether there exists any instance of [clazz].
 */
fun Connection.existsAny(clazz: Class<*>): Boolean = createQuery("select count(1) from ${clazz.entityMeta.databaseTableName}")
        .executeScalar(Long::class.java) > 0

/**
 * Checks whether there exists any instance of [clazz] matching given criteria [filter].
 */
fun <T: Any> Connection.existsBy(clazz: Class<T>, filter: Filter<T>): Boolean {
    val sql = filter.toParametrizedSql(clazz)
    val query = createQuery("select count(1) from ${clazz.entityMeta.databaseTableName} where ${sql.sql92}")
    sql.sql92Parameters.entries.forEach { (name, value) -> query.addParameter(name, value) }
    return query.executeScalar(Long::class.java) > 0
}

/**
 * Checks whether there exists any instance of [clazz] with given id.
 */
fun <T: Any> Connection.existsById(clazz: Class<T>, id: Any): Boolean {
    val query = createQuery("select count(1) from ${clazz.entityMeta.databaseTableName} where ${clazz.entityMeta.idProperty.dbColumnName}=:id")
            .addParameter("id", id)
    return query.executeScalar(Long::class.java) > 0
}

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
fun Query.dump(): String = executeAndFetchTableLazy().use { table: LazyTable ->
    fun Row.dump(): String = (0 until table.columns().size).joinToString { "${getObject(it)}" }
    buildString {

        // bug in sql2o: we need to ask for rows().iterator() otherwise columns() will be null :-(
        val rows: Iterator<Row> = table.rows().iterator()

        // draw the header and the separator
        val header: String = table.columns().joinToString { it.name }
        appendln(header)
        repeat(header.length) { append("-") }
        appendln()

        // draw the table body
        var rowCount = 0
        rows.forEach { row -> rowCount++; appendln(row.dump()) }

        // the bottom separator
        repeat(header.length) { append("-") }
        appendln("$rowCount row(s)")
    }
}
