package com.github.vokorm.dataloader

import com.github.mvysny.vokdataloader.*
import com.github.vokorm.*
import com.gitlab.mvysny.jdbiorm.Dao
import com.gitlab.mvysny.jdbiorm.Entity
import com.gitlab.mvysny.jdbiorm.EntityMeta
import com.gitlab.mvysny.jdbiorm.PropertyMeta
import org.jdbi.v3.core.statement.Query

/**
 * Provides instances of entities of given [clazz] from a database. Does not support joins on any of the like; supports filtering
 * and sorting. Only supports simple views over one database table (one entity) - for anything more complex please use [SqlDataLoader].
 *
 * The `NativePropertyName` is the database column name of the database table linked to by this entity (using the
 * [Table] mapping). The `DataLoaderPropertyName` is the Java Bean Property Name or the Kotlin Property name of the [Entity],
 * but it also accepts the database column name.
 *
 * The database column name is mapped 1:1 to the Java Bean Property Name. If you however use `UPPER_UNDERSCORE` naming scheme
 * in your database, you can map it to `camelCase` using the [As] annotation.
 */
class EntityDataLoader<T : Entity<*>>(val dao: Dao<T, *>) : DataLoader<T> {
    val clazz: Class<T> get() = dao.entityClass
    override fun toString() = "EntityDataLoader($clazz)"

    override fun getCount(filter: Filter<T>?): Long = db {
        val sql: ParametrizedSql = filter?.toParametrizedSql(clazz) ?: ParametrizedSql("", mapOf())
        var where: String = sql.sql92
        if (!where.isBlank()) where = "where $where"
        val count: Long = handle.createQuery("select count(*) from <TABLE> <WHERE>")
                .define("TABLE", dao.meta.databaseTableName)
                .define("WHERE", where)
                .fillInParamsFromFilters(sql)
                .mapTo(Long::class.java).one()
        count
    }

    override fun fetch(filter: Filter<T>?, sortBy: List<SortClause>, range: LongRange): List<T> {
        require(range.start >= 0) { "range.start: ${range.start} must be 0 or more" }
        require(range.endInclusive >= 0) { "limit: ${range.endInclusive} must be 0 or more" }
        val sqlw: ParametrizedSql = filter?.toParametrizedSql(clazz) ?: ParametrizedSql("", mapOf())
        val where: String = sqlw.sql92
        val orderBy: String = sortBy.joinToString { "${it.getNativeColumnName(clazz)} ${if (it.asc) "ASC" else "DESC"}" }
        val sql = buildString {
            append("select * from ${dao.meta.databaseTableName}")
            if (where.isNotBlank()) append(" where $where")
            if (orderBy.isNotBlank()) append(" order by $orderBy")
            // MariaDB requires LIMIT first, then OFFSET: https://mariadb.com/kb/en/library/limit/
            if (range != (0L..Long.MAX_VALUE)) append(" LIMIT ${range.length.coerceAtMost(Int.MAX_VALUE.toLong())} OFFSET ${range.start}")
        }
        return db {
            handle.createQuery(sql)
                .fillInParamsFromFilters(sqlw)
                .map(dao.getRowMapper())
                .list()
        }
    }

    private fun Query.fillInParamsFromFilters(filter: ParametrizedSql): Query {
        filter.sql92Parameters.entries.forEach { (name, value) -> bind(name, value) }
        return this
    }
}

fun <T: Entity<ID>, ID> EntityDataLoader(clazz: Class<T>): EntityDataLoader<T> =
        EntityDataLoader(Dao<T, ID>(clazz))

val LongRange.length: Long get() = if (isEmpty()) 0 else endInclusive - start + 1

/**
 * Converts the data loader property name to underlying database column name. However, if there is no such
 * property then it assumes that the data loader property name is already the column name and simply returns this.
 */
internal fun DataLoaderPropertyName.toNativeColumnName(clazz: Class<*>): NativePropertyName {
    val property: PropertyMeta = EntityMeta(clazz).properties.firstOrNull { it.name == this } ?: return this
    return property.dbColumnName
}

internal fun SortClause.getNativeColumnName(clazz: Class<*>): NativePropertyName = propertyName.toNativeColumnName(clazz)

/**
 * Provides instances of this entity from a database. Does not support joins on any of the like; supports filtering
 * and sorting. Only supports simple views over one database table (one entity) - for anything more complex please use [SqlDataLoader].
 *
 * Example of use: `grid.setDataLoader(Person.dataLoader)`.
 */
val <T: Entity<*>> Dao<T, *>.dataLoader: DataLoader<T>
    get() = EntityDataLoader(this)
