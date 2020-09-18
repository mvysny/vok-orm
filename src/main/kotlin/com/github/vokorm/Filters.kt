package com.github.vokorm

import com.github.mvysny.vokdataloader.*
import com.gitlab.mvysny.jdbiorm.EntityMeta
import com.gitlab.mvysny.jdbiorm.quirks.DatabaseVariant
import org.jdbi.v3.core.statement.SqlStatement

/**
 * Contains a native SQL-92 query or just a query part (e.g. only the part after WHERE), possibly referencing named parameters.
 *
 * @property sql92 for example `name = :name`, references [NativePropertyName] database column names.
 * All named parameters must be present in the [sql92Parameters] map.
 * @property sql92Parameters maps [NativePropertyName] to its value.
 */
public data class ParametrizedSql(val sql92: String, var sql92Parameters: Map<NativePropertyName, Any?>) {
    override fun toString(): String = "$sql92:$sql92Parameters"
}

public interface FilterToSqlConverter {
    /**
     * Attempts to convert this filter into a SQL 92 WHERE-clause representation
     * (omitting the `WHERE` keyword). There are two types of filters:
     * * Filters which do not match column to a value, for example [AndFilter]
     *   which produces something like `(filter1 and filter2)`
     * * Filters which do match column to a value, for example [StartsWithFilter]
     *   which produces things like `name LIKE :name`. All [BeanFilter]s are expected
     *   to match a [NativePropertyName] database column to a value; that value
     *   is automatically prefilled into the JDBC query string.
     *
     * Examples of returned values:
     * * `name = :name`
     * * `(age >= :age AND name ILIKE :name)`
     * @param filter the filter to convert
     * @param clazz the entity type for which the filter has been produced.
     * @return a converted filter or null if the filter is blank (e.g. full-text filter with just a space as input).
     */
    public fun convert(filter: Filter<*>, clazz: Class<*>, variant: DatabaseVariant): ParametrizedSql
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
public fun Filter<*>.toParametrizedSql(clazz: Class<*>, variant: DatabaseVariant): ParametrizedSql =
        VokOrm.filterToSqlConverter.convert(this, clazz, variant)

public class DefaultFilterToSqlConverter : FilterToSqlConverter {
    override fun convert(filter: Filter<*>, clazz: Class<*>, variant: DatabaseVariant): ParametrizedSql {
        val databaseColumnName: String = if (filter is BeanFilter) filter.propertyName.toNativeColumnName(clazz) else ""
        val parameterName = "p${System.identityHashCode(filter).toString(36)}"
        return when (filter) {
            is EqFilter -> ParametrizedSql("$databaseColumnName = :$parameterName", mapOf(parameterName to filter.value))
            is OpFilter -> ParametrizedSql("$databaseColumnName ${filter.operator.sql92Operator} :$parameterName", mapOf(parameterName to filter.value))
            is IsNullFilter -> ParametrizedSql("$databaseColumnName is null", mapOf())
            is IsNotNullFilter -> ParametrizedSql("$databaseColumnName is not null", mapOf())
            is StartsWithFilter -> ParametrizedSql("$databaseColumnName ${if (filter.ignoreCase) "ILIKE" else "LIKE"} :$parameterName", mapOf(parameterName to "${filter.value}%"))
            is AndFilter -> {
                val c: List<ParametrizedSql> = filter.children.map { it.toParametrizedSql(clazz, variant) }
                val sql92: String = c.joinToString(" and ", "(", ")") { it.sql92 }
                val map: MutableMap<String, Any?> = mutableMapOf()
                c.forEach { map.putAll(it.sql92Parameters) }
                ParametrizedSql(sql92, map)
            }
            is OrFilter -> {
                val c: List<ParametrizedSql> = filter.children.map { it.toParametrizedSql(clazz, variant) }
                val sql92: String = c.joinToString(" or ", "(", ")") { it.sql92 }
                val map: MutableMap<String, Any?> = mutableMapOf()
                c.forEach { map.putAll(it.sql92Parameters) }
                ParametrizedSql(sql92, map)
            }
            is NativeSqlFilter -> ParametrizedSql(filter.where, filter.params)
            is SubstringFilter -> ParametrizedSql("$databaseColumnName ${if (filter.ignoreCase) "ILIKE" else "LIKE"} :$parameterName", mapOf(parameterName to "%${filter.value}%"))
            is FullTextFilter -> convertFullTextFilter(filter, databaseColumnName, parameterName, clazz, variant)
            else -> throw IllegalArgumentException("Unsupported: cannot convert filter $filter to SQL92. Please provide a custom VokOrm.filterToSqlConverter which supports this filter type")
        }
    }

    public fun convertFullTextFilter(filter: FullTextFilter<*>, databaseColumnName: String,
                              parameterName: String, clazz: Class<*>, databaseVariant: DatabaseVariant): ParametrizedSql {
        if (filter.words.isEmpty()) {
            return MATCH_ALL
        }
        return when (databaseVariant) {
            DatabaseVariant.MySQLMariaDB -> {
                val booleanQuery: String = toMySQLFulltextBooleanQuery(filter.words)
                if (booleanQuery.isBlank()) {
                    return MATCH_ALL
                }
                ParametrizedSql("MATCH($databaseColumnName) AGAINST (:$parameterName IN BOOLEAN MODE)",
                        mapOf(parameterName to booleanQuery))
            }
            DatabaseVariant.PostgreSQL -> {
                // see https://www.postgresql.org/docs/9.5/textsearch-controls.html#TEXTSEARCH-PARSING-QUERIES for more documentation
                ParametrizedSql("to_tsvector('english', $databaseColumnName) @@ to_tsquery('english', :$parameterName)",
                        mapOf(parameterName to filter.words.joinToString(" & ") { "$it:*" }))
            }
            DatabaseVariant.H2 -> {
                val meta: EntityMeta<*> = EntityMeta(clazz)
                val idColumn: String = meta.idProperty[0].dbColumnName
                val query: String = filter.words.joinToString(" AND ") { "$it*" }
                // Need to CAST(FT.KEYS[1] AS BIGINT) otherwise IN won't match anything
                ParametrizedSql("$idColumn IN (SELECT CAST(FT.KEYS[1] AS BIGINT) AS ID FROM FTL_SEARCH_DATA(:$parameterName, 0, 0) FT WHERE FT.`TABLE`='${meta.databaseTableName.toUpperCase()}')",
                        mapOf(parameterName to query))
            }
            DatabaseVariant.MSSQL -> {
                val query: String = filter.words.joinToString { "\"$it*\"" }
                ParametrizedSql("CONTAINS($databaseColumnName, :$parameterName)",
                        mapOf(parameterName to query))
            }
            else -> {
                throw IllegalArgumentException("Unsupported FullText search for variant $databaseVariant. Either set proper variant to JdbiOrm.databaseVariant, or provide custom VokOrm.filterToSqlConverter which supports proper full-text search syntax: $filter")
            }
        }
    }

    public companion object {
        /**
         * Converts an user input into a MySQL BOOLEAN FULLTEXT search string, by
         * sanitizing input and appending with * to perform starts-with matching.
         *
         * In your SQL just use `WHERE ... AND MATCH(search_id) AGAINST (:searchId IN BOOLEAN MODE)`.
         * @param words
         * @return full-text query string, not null. If blank, there is nothing to search for,
         * and the SQL MATCH ... AGAINST clause must be omitted.
         */
        public fun toMySQLFulltextBooleanQuery(words: Collection<String>): String {
            val sb = StringBuilder()
            for (word in words) {
                if (word.isNotBlank() && mysqlAppearsInIndex(word)) {
                    sb.append(" +").append(word).append('*')
                }
            }
            return sb.toString().trim()
        }

        /**
         * Too short words do not appear in MySQL FULLTEXT index - we must not search for
         * such words otherwise MySQL will return nothing!
         */
        public fun mysqlAppearsInIndex(word: String): Boolean {
            // By default, words less than 3 characters in length or greater than 84 characters in length do not appear in an InnoDB full-text search index.
            // https://dev.mysql.com/doc/refman/8.0/en/fulltext-stopwords.html
            return word.trim().length in 3..84
        }

        private val MATCH_ALL = ParametrizedSql("1=1", mapOf())
    }
}

/**
 * Binds all [ParametrizedSql.sql92Parameters] to the receiver Query.
 */
public fun <T: SqlStatement<*>> T.bind(sql: ParametrizedSql): T {
    sql.sql92Parameters.entries.forEach { (name: NativePropertyName, value: Any?) -> bind(name, value) }
    return this
}
