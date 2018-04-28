package com.github.vokorm.dataloader

import com.github.vokorm.*
import org.sql2o.Query

/**
 * Provides instances of entities of given [clazz] from a database. Does not support joins on any of the like; supports filtering
 * and sorting. Only supports simple views over one database table (one entity) - for anything more complex please use [SqlDataProvider].
 */
class EntityDataLoader<T : Entity<*>>(val clazz: Class<T>) : DataLoader<T> {
    override fun toString() = "EntityDataLoader($clazz)"

    override fun getCount(filter: Filter<T>?): Int = db {
        var where = filter?.toSQL92() ?: ""
        if (!where.isBlank()) where = "where $where"
        val count = con.createQuery("select count(*) from ${clazz.entityMeta.databaseTableName} $where")
                .fillInParamsFromFilters(filter)
                .executeScalar()
        (count as Number).toInt()
    }

    private val IntRange.length: Int get() = if (isEmpty()) 0 else endInclusive - start + 1

    override fun getItems(filter: Filter<T>?, sortBy: List<SortClause>, range: IntRange): List<T> {
        require(range.start >= 0) { "range.start: ${range.start} must be 0 or more" }
        require(range.endInclusive >= 0) { "limit: ${range.endInclusive} must be 0 or more" }
        val where: String? = filter?.toSQL92()
        val orderBy: String = sortBy.map { "${it.property} ${if (it.asc) "ASC" else "DESC"}" }.joinToString()
        val sql = buildString {
            append("select * from ${clazz.entityMeta.databaseTableName}")
            if (where != null && where.isNotBlank()) append(" where $where")
            if (orderBy.isNotBlank()) append(" order by $orderBy")
            // MariaDB requires LIMIT first, then OFFSET: https://mariadb.com/kb/en/library/limit/
            if (range != 0..Int.MAX_VALUE) append(" LIMIT ${range.length} OFFSET ${range.start}")
        }
        return db {
            con.createQuery(sql)
                .fillInParamsFromFilters(filter)
                .executeAndFetch(clazz)
        }
    }

    private fun Query.fillInParamsFromFilters(filter: Filter<T>?): Query {
        val filters = filter ?: return this
        val params = filters.getSQL92Parameters()
        params.entries.forEach { (name, value) -> addParameter(name, value) }
        return this
    }
}
