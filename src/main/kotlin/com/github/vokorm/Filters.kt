package com.github.vokorm

import com.github.mvysny.vokdataloader.*
import com.github.vokorm.dataloader.toNativeColumnName
import org.jdbi.v3.core.statement.SqlStatement
import java.lang.IllegalArgumentException

/**
 * Contains a native SQL-92 query or just a query part (e.g. only the part after WHERE), possibly referencing named parameters.
 *
 * @property sql92 for example `name = :name`, references [NativePropertyName] database column names.
 * All named parameters must be present in the [sql92Parameters] map.
 * @property sql92Parameters maps [NativePropertyName] to its value.
 */
data class ParametrizedSql(val sql92: String, var sql92Parameters: Map<NativePropertyName, Any?>) {
    override fun toString(): String = "$sql92:$sql92Parameters"
}

interface FilterToSqlConverter {
    /**
     * Attempts to convert this filter into a SQL 92 WHERE-clause representation (omitting the `WHERE` keyword). There are two types of filters:
     * * Filters which do not match column to a value, for example [AndFilter] which produces something like `(filter1 and filter2)`
     * * Filters which do match column to a value, for example [LikeFilter] which produces things like `name LIKE :name`. All [BeanFilter]s are expected
     * to match a [NativePropertyName] database column to a value; that value is automatically prefilled into the JDBC query string.
     *
     * Examples of returned values:
     * * `name = :name`
     * * `(age >= :age AND name ILIKE :name)`
     */
    fun convert(filter: Filter<*>, clazz: Class<*>): ParametrizedSql
}

/**
 * Attempts to convert this filter into a SQL 92 WHERE-clause representation (omitting the `WHERE` keyword). There are two types of filters:
 * * Filters which do not match column to a value, for example [AndFilter] which produces something like `(filter1 and filter2)`
 * * Filters which do match column to a value, for example [LikeFilter] which produces things like `name LIKE :name`. All [BeanFilter]s are expected
 * to match a [NativePropertyName] database column to a value; that value is automatically prefilled into the JDBC query string.
 *
 * Examples of returned values:
 * * `name = :name`
 * * `(age >= :age AND name ILIKE :name)`
 *
 * To override the behavior of this function, set a different converter to [VokOrm.filterToSqlConverter].
 */
fun Filter<*>.toParametrizedSql(clazz: Class<*>): ParametrizedSql = VokOrm.filterToSqlConverter.convert(this, clazz)

class DefaultFilterToSqlConverter : FilterToSqlConverter {
    override fun convert(filter: Filter<*>, clazz: Class<*>): ParametrizedSql {
        val databaseColumnName: String = if (filter is BeanFilter) filter.propertyName.toNativeColumnName(clazz) else ""
        val parameterName = "p${System.identityHashCode(this).toString(36)}"
        return when (filter) {
            is EqFilter -> ParametrizedSql("$databaseColumnName = :$parameterName", mapOf(parameterName to filter.value))
            is OpFilter -> ParametrizedSql("$databaseColumnName ${filter.operator.sql92Operator} :$parameterName", mapOf(parameterName to filter.value))
            is IsNullFilter -> ParametrizedSql("$databaseColumnName is null", mapOf())
            is IsNotNullFilter -> ParametrizedSql("$databaseColumnName is not null", mapOf())
            is LikeFilter -> ParametrizedSql("$databaseColumnName LIKE :$parameterName", mapOf(parameterName to filter.value))
            is ILikeFilter -> ParametrizedSql("$databaseColumnName ILIKE :$parameterName", mapOf(parameterName to filter.value))
            is AndFilter -> {
                val c: List<ParametrizedSql> = filter.children.map { it.toParametrizedSql(clazz) }
                val sql92: String = c.joinToString(" and ", "(", ")") { it.sql92 }
                val map: MutableMap<String, Any?> = mutableMapOf<String, Any?>()
                c.forEach { map.putAll(it.sql92Parameters) }
                ParametrizedSql(sql92, map)
            }
            is OrFilter -> {
                val c: List<ParametrizedSql> = filter.children.map { it.toParametrizedSql(clazz) }
                val sql92: String = c.joinToString(" or ", "(", ")") { it.sql92 }
                val map: MutableMap<String, Any?> = mutableMapOf<String, Any?>()
                c.forEach { map.putAll(it.sql92Parameters) }
                ParametrizedSql(sql92, map)
            }
            is NativeSqlFilter -> ParametrizedSql(filter.where, filter.params)
            is SubstringFilter -> ParametrizedSql("$databaseColumnName LIKE :$parameterName", mapOf(parameterName to filter.value))
            is ISubstringFilter -> ParametrizedSql("$databaseColumnName ILIKE :$parameterName", mapOf(parameterName to filter.value))
            else -> throw IllegalArgumentException("Unsupported: cannot convert filter $this to SQL92")
        }
    }
}

/**
 * Binds all [ParametrizedSql.sql92Parameters] to the receiver Query.
 */
fun <T: SqlStatement<*>> T.bind(sql: ParametrizedSql): T {
    sql.sql92Parameters.entries.forEach { (name: NativePropertyName, value: Any?) -> bind(name, value) }
    return this
}
