package com.github.vokorm

import com.github.mvysny.vokdataloader.*
import com.github.vokorm.dataloader.toNativeColumnName
import java.lang.IllegalArgumentException

/**
 * Attempts to convert this filter into a SQL 92 WHERE-clause representation (omitting the `WHERE` keyword). There are two types of filters:
 * * Filters which do not match column to a value, for example [AndFilter] which produces something like `(filter1 and filter2)`
 * * Filters which do match column to a value, for example [LikeFilter] which produces things like `name LIKE :name`. All [BeanFilter]s are expected
 * to match a database column to a value; that value is automatically prefilled into the JDBC query string under the [BeanFilter.databaseColumnName].
 *
 * Examples of returned values:
 * * `name = :name`
 * * `(age >= :age AND name ILIKE :name)`
 */
data class ParametrizedSql(val sql92: String, var sql92Parameters: Map<String, Any?>) {
    override fun toString(): String = "$sql92:$sql92Parameters"
}

fun Filter<*>.toParametrizedSql(clazz: Class<*>): ParametrizedSql {
    val databaseColumnName: String = if (this is BeanFilter) propertyName.toNativeColumnName(clazz) else ""
    val parameterName = "p${System.identityHashCode(this).toString(36)}"
    return when (this) {
        is EqFilter -> ParametrizedSql( "$databaseColumnName = :$parameterName", mapOf(parameterName to value))
        is OpFilter -> ParametrizedSql("$databaseColumnName ${operator.sql92Operator} :$parameterName", mapOf(parameterName to value))
        is IsNullFilter -> ParametrizedSql("$databaseColumnName is null", mapOf())
        is IsNotNullFilter -> ParametrizedSql("$databaseColumnName is not null", mapOf())
        is LikeFilter -> ParametrizedSql("$databaseColumnName LIKE :$parameterName", mapOf(parameterName to value))
        is ILikeFilter -> ParametrizedSql("$databaseColumnName ILIKE :$parameterName", mapOf(parameterName to value))
        is AndFilter -> {
            val c = children.map { it.toParametrizedSql(clazz) }
            val sql92 = c.joinToString(" and ", "(", ")") { it.sql92 }
            val map = mutableMapOf<String, Any?>()
            c.forEach { map.putAll(it.sql92Parameters) }
            ParametrizedSql(sql92, map)
        }
        is OrFilter -> {
            val c = children.map { it.toParametrizedSql(clazz) }
            val sql92 = c.joinToString(" or ", "(", ")") { it.sql92 }
            val map = mutableMapOf<String, Any?>()
            c.forEach { map.putAll(it.sql92Parameters) }
            ParametrizedSql(sql92, map)
        }
        is NativeSqlFilter -> ParametrizedSql(where, params)
        else -> throw IllegalArgumentException("Unsupported: cannot convert filter $this to SQL92")
    }
}
