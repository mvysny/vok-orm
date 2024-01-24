package com.github.vokorm

import com.gitlab.mvysny.jdbiorm.condition.Condition
import com.gitlab.mvysny.jdbiorm.condition.ParametrizedSql

/**
 * Just write any native SQL into [where], e.g. `age > 25 and name like :name`; don't forget to properly fill in the [params] map.
 *
 * Does not support in-memory filtering and will throw an exception.
 */
public data class NativeSQLCondition(val where: String, val params: Map<String, Any?>) : Condition {
    override fun toSql(): ParametrizedSql = ParametrizedSql(where, params)
    override fun test(row: Any): Boolean = throw UnsupportedOperationException("Does not support in-memory filtering")
    override fun toString(): String = "'$where'$params"
}
