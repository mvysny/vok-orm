package com.github.vokorm.dataloader

import com.github.mvysny.vokdataloader.*
import com.github.vokorm.*
import org.sql2o.Query

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
class EntityDataLoader<T : Entity<*>>(val clazz: Class<T>) : DataLoader<T> {
    override fun toString() = "EntityDataLoader($clazz)"

    override fun getCount(filter: Filter<T>?): Long = db {
        val sql = filter?.toParametrizedSql(clazz) ?: ParametrizedSql("", mapOf())
        var where = sql.sql92
        if (!where.isBlank()) where = "where $where"
        val count = con.createQuery("select count(*) from ${clazz.entityMeta.databaseTableName} $where")
                .fillInParamsFromFilters(sql)
                .executeScalar(Long::class.java)!!
        count
    }

    override fun fetch(filter: Filter<T>?, sortBy: List<SortClause>, range: LongRange): List<T> {
        require(range.start >= 0) { "range.start: ${range.start} must be 0 or more" }
        require(range.endInclusive >= 0) { "limit: ${range.endInclusive} must be 0 or more" }
        val sqlw = filter?.toParametrizedSql(clazz) ?: ParametrizedSql("", mapOf())
        val where: String = sqlw.sql92
        val orderBy: String = sortBy.joinToString { "${it.getNativeColumnName(clazz)} ${if (it.asc) "ASC" else "DESC"}" }
        val sql = buildString {
            append("select * from ${clazz.entityMeta.databaseTableName}")
            if (where.isNotBlank()) append(" where $where")
            if (orderBy.isNotBlank()) append(" order by $orderBy")
            // MariaDB requires LIMIT first, then OFFSET: https://mariadb.com/kb/en/library/limit/
            if (range != (0L..Long.MAX_VALUE)) append(" LIMIT ${range.length.coerceAtMost(Int.MAX_VALUE.toLong())} OFFSET ${range.start}")
        }
        val dbnameToJavaFieldName = clazz.entityMeta.getSql2oColumnMappings()
        return db {
            con.createQuery(sql)
                .fillInParamsFromFilters(sqlw)
                .setColumnMappings(dbnameToJavaFieldName)
                .executeAndFetch(clazz)
        }
    }

    private fun Query.fillInParamsFromFilters(filter: ParametrizedSql): Query {
        filter.sql92Parameters.entries.forEach { (name, value) -> addParameter(name, value) }
        return this
    }
}

val LongRange.length: Long get() = if (isEmpty()) 0 else endInclusive - start + 1

/**
 * Converts the data loader property name to underlying database column name. However, if there is no such
 * property then it assumes that the data loader property name is already the column name and simply returns this.
 */
internal fun DataLoaderPropertyName.toNativeColumnName(clazz: Class<*>): NativePropertyName {
    val property = clazz.entityMeta.properties.firstOrNull { it.name == this } ?: return this
    return property.dbColumnName
}

internal fun SortClause.getNativeColumnName(clazz: Class<*>): NativePropertyName = propertyName.toNativeColumnName(clazz)

/**
 * Provides instances of this entity from a database. Does not support joins on any of the like; supports filtering
 * and sorting. Only supports simple views over one database table (one entity) - for anything more complex please use [SqlDataLoader].
 *
 * Example of use: `grid.setDataLoader(Person.dataLoader)`.
 */
inline val <reified T: Entity<*>> Dao<T>.dataLoader: DataLoader<T>
    get() = EntityDataLoader(T::class.java)
