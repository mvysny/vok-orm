package com.github.vokorm.dataloader

import com.github.mvysny.vokdataloader.*
import com.github.vokorm.*
import com.gitlab.mvysny.jdbiorm.Dao
import com.gitlab.mvysny.jdbiorm.Entity
import com.gitlab.mvysny.jdbiorm.JdbiOrm
import com.gitlab.mvysny.jdbiorm.spi.AbstractEntity

/**
 * Provides instances of entities of given class from a database. Does not support joins on any of the like; supports filtering
 * and sorting. Only supports simple views over one database table (one entity) - for anything more complex please use [SqlDataLoader].
 *
 * The `NativePropertyName` is the database column name of the database table linked to by this entity (using the
 * [com.gitlab.mvysny.jdbiorm.Table] mapping). The `DataLoaderPropertyName` is the Java Bean Property Name or the Kotlin Property name of the [Entity],
 * but it also accepts the database column name.
 *
 * The database column name is mapped 1:1 to the Java Bean Property Name. If you however use `UPPER_UNDERSCORE` naming scheme
 * in your database, you can map it to `camelCase` using the [org.jdbi.v3.core.mapper.reflect.ColumnName] annotation.
 * @property dao DAO to use when loading instances of [T]
 * @param T the entity type
 */
public class EntityDataLoader<T : AbstractEntity<*>>(public val dao: Dao<T, *>) : DataLoader<T> {
    public val clazz: Class<T> get() = dao.entityClass
    override fun toString(): String = "EntityDataLoader($clazz)"

    override fun getCount(filter: Filter<T>?): Long =
            if (filter == null) dao.count() else dao.count(filter)

    override fun fetch(filter: Filter<T>?, sortBy: List<SortClause>, range: LongRange): List<T> {
        require(range.first >= 0) { "range.start: ${range.first} must be 0 or more" }
        require(range.last >= 0) { "limit: ${range.last} must be 0 or more" }

        val sqlw: ParametrizedSql = filter?.toParametrizedSql(clazz, JdbiOrm.databaseVariant!!) ?: ParametrizedSql("1=1", mapOf())
        val orderBy: String? = sortBy.toSql92OrderByClause(clazz)
        val where: String = sqlw.sql92

        val limit: Long? = if (range != (0L..Long.MAX_VALUE)) range.length.coerceAtMost(Int.MAX_VALUE.toLong()) else null
        val offset: Long? = if (range != (0L..Long.MAX_VALUE)) range.first else null
        return dao.findAllBy(where, orderBy, offset, limit) { query -> query.bind(sqlw) }
    }
}

public fun <T: AbstractEntity<ID>, ID> EntityDataLoader(clazz: Class<T>): EntityDataLoader<T> =
        EntityDataLoader(Dao<T, ID>(clazz))

public val LongRange.length: Long get() = if (isEmpty()) 0 else endInclusive - start + 1

/**
 * Provides instances of this entity from a database. Does not support joins on any of the like; supports filtering
 * and sorting. Only supports simple views over one database table (one entity) - for anything more complex please use [SqlDataLoader].
 *
 * Example of use: `grid.setDataLoader(Person.dataLoader)`.
 */
public val <T: AbstractEntity<*>> Dao<T, *>.dataLoader: DataLoader<T>
    get() = EntityDataLoader(this)
