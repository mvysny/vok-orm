package com.github.vokorm

import com.github.mvysny.vokdataloader.Filter
import com.github.mvysny.vokdataloader.SqlWhereBuilder
import com.gitlab.mvysny.jdbiorm.Dao
import com.gitlab.mvysny.jdbiorm.DaoOfAny
import com.gitlab.mvysny.jdbiorm.Entity
import org.jdbi.v3.core.Handle

/**
 * Finds all instances of given entity. Fails if there is no table in the database with the name of [EntityMeta.databaseTableName]. The list is eager
 * and thus it's useful for smallish tables only.
 */
@Deprecated("use DaoOfAny")
fun <T : Any> Handle.findAll(clazz: Class<T>): List<T> = DaoOfAny(clazz).findAll()

/**
 * Retrieves entity with given [id]. Returns null if there is no such entity.
 */
@Deprecated("use Dao")
fun <ID, T : Entity<ID>> Handle.findById(clazz: Class<T>, id: ID): T? = Dao<T, ID>(clazz).findById(id)

/**
 * Retrieves entity with given [id]. Fails if there is no such entity.
 * @throws IllegalArgumentException if there is no entity with given id.
 */
@Deprecated("use Dao")
fun <ID, T : Entity<ID>> Handle.getById(clazz: Class<T>, id: ID): T = Dao<T, ID>(clazz).getById(id)

/**
 * Deletes all rows from given database table.
 */
@Deprecated("use DaoOfAny")
fun <T: Any> Handle.deleteAll(clazz: Class<T>) {
    DaoOfAny<T>(clazz).deleteAll()
}

/**
 * Counts all rows in given table [clazz].
 */
@Deprecated("use DaoOfAny")
fun <T: Any> Handle.getCount(clazz: Class<T>): Long =
        DaoOfAny<T>(clazz).count()

/**
 * Counts all rows in given table [clazz] satisfying given [filter].
 */
@Deprecated("use DaoOfAny")
fun <T: Any> Handle.getCount(clazz: Class<T>, filter: Filter<T>): Long =
        DaoOfAny<T>(clazz).count(filter)

/**
 * Retrieves specific entity matching given criteria [filter]. Fails if there are two or more entities matching the criteria.
 *
 * This function returns `null` if there is no such entity. Use [getBy] if you wish an exception to be thrown in case that
 * the entity does not exist.
 * @throws IllegalArgumentException if there are two or more matching entities.
 */
@Deprecated("use DaoOfAny.findOneBy()")
fun <T: Any> Handle.findSpecificBy(clazz: Class<T>, filter: Filter<T>): T? =
        DaoOfAny(clazz).findOneBy(filter)

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
    query.columnMappings = clazz.entityMeta.getSql2oColumnMappings()
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
