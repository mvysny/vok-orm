package com.github.vokorm

import org.sql2o.Connection

/**
 * Finds all instances of given entity. Fails if there is no table in the database with the name of [databaseTableName]. The list is eager
 * and thus it's useful for smallish tables only.
 */
fun <T : Any> Connection.findAll(clazz: Class<T>): List<T> = createQuery("select * from ${clazz.entityMeta.databaseTableName}").executeAndFetch(clazz)

/**
 * Retrieves entity with given [id]. Returns null if there is no such entity.
 */
fun <T : Any> Connection.findById(clazz: Class<T>, id: Any): T? =
    createQuery("select * from ${clazz.entityMeta.databaseTableName} where id = :id")
        .addParameter("id", id)
        .executeAndFetchFirst(clazz)

/**
 * Retrieves entity with given [id]. Fails if there is no such entity.
 * @throws IllegalArgumentException if there is no entity with given id.
 */
fun <T : Any> Connection.getById(clazz: Class<T>, id: Any): T =
    requireNotNull(findById(clazz, id)) { "There is no $clazz for id $id" }

/**
 * Deletes all rows from given database table.
 */
fun <T: Any> Connection.deleteAll(clazz: Class<T>) {
    createQuery("delete from ${clazz.entityMeta.databaseTableName}").executeUpdate()
}

/**
 * Counts all rows in given table [clazz].
 */
fun <T: Any> Connection.getCount(clazz: Class<T>): Long {
    val count = createQuery("select count(*) from ${clazz.entityMeta.databaseTableName}").executeScalar()
    return (count as Number).toLong()
}

/**
 * Counts all rows in given table [clazz] satisfying given [filter].
 */
fun <T: Any> Connection.getCount(clazz: Class<T>, filter: Filter<T>): Long {
    val count = createQuery("select count(*) from ${clazz.entityMeta.databaseTableName} where ${filter.toSQL92()}").executeScalar()
    return (count as Number).toLong()
}

/**
 * Retrieves specific entity matching given criteria [filter]. Fails if there are two or more entities matching the criteria.
 *
 * This function returns `null` if there is no such entity. Use [getBy] if you wish an exception to be thrown in case that
 * the entity does not exist.
 * @throws IllegalArgumentException if there are two or more matching entities.
 */
fun <T: Any> Connection.findSpecificBy(clazz: Class<T>, filter: Filter<T>): T? {
    val result = findBy(clazz, 2, filter)
    require(result.size < 2) { "too many ${clazz.simpleName} satisfying ${filter.toSQL92()}:${filter.getSQL92Parameters()}: $result and perhaps more" }
    return result.firstOrNull()
}

fun <T: Any> Connection.getBy(clazz: Class<T>, filter: Filter<T>): T =
    findSpecificBy(clazz, filter) ?: throw IllegalArgumentException("no ${clazz.simpleName} satisfying ${filter.toSQL92()}:${filter.getSQL92Parameters()}")

fun <T: Any> Connection.findBy(clazz: Class<T>, limit: Int, filter: Filter<T>): List<T> {
    require (limit >= 0) { "$limit is less than 0" }
    val query = createQuery("select * from ${clazz.entityMeta.databaseTableName} where ${filter.toSQL92()} limit $limit")
    filter.getSQL92Parameters().entries.forEach { (name, value) -> query.addParameter(name, value) }
    return query.executeAndFetch(clazz)
}

fun <T: Any> Connection.deleteBy(clazz: Class<T>, block: SqlWhereBuilder<T>.()-> Filter<T>) {
    val filter = block(SqlWhereBuilder())
    val query = createQuery("delete from ${clazz.entityMeta.databaseTableName} where ${filter.toSQL92()}")
    filter.getSQL92Parameters().entries.forEach { (name, value) -> query.addParameter(name, value) }
    query.executeUpdate()
}

fun <T: Any> Connection.deleteById(clazz: Class<T>, id: Any) {
    createQuery("delete from ${clazz.entityMeta.databaseTableName} where ${clazz.entityMeta.idDbname}=:id")
        .addParameter("id", id)
        .executeUpdate()
}
