package com.github.vokorm

import com.github.mvysny.vokdataloader.DataLoaderPropertyName
import com.github.mvysny.vokdataloader.NativePropertyName
import com.github.mvysny.vokdataloader.SortClause
import com.gitlab.mvysny.jdbiorm.EntityMeta
import com.gitlab.mvysny.jdbiorm.PropertyMeta
import com.gitlab.mvysny.jdbiorm.quirks.DatabaseVariant
import com.gitlab.mvysny.jdbiorm.quirks.Quirks
import org.jdbi.v3.core.Handle

/**
 * Checks whether this class implements given interface [intf].
 */
public fun Class<*>.implements(intf: Class<*>): Boolean {
    require(intf.isInterface) { "$intf is not an interface" }
    return intf.isAssignableFrom(this)
}

public val Handle.databaseVariant: DatabaseVariant get() = DatabaseVariant.from(this)
public val Handle.quirks: Quirks get() = Quirks.from(this)

/**
 * Converts [SortClause] to something like "name ASC".
 */
private fun SortClause.toSql92OrderByClause(clazz: Class<*>): String = "${getNativeColumnName(clazz)} ${if (asc) "ASC" else "DESC"}"
private fun SortClause.getNativeColumnName(clazz: Class<*>): NativePropertyName = propertyName.toNativeColumnName(clazz)

/**
 * Converts a list of [SortClause] to something like "name DESC, age ASC". If the list is empty, returns an empty string.
 */
internal fun List<SortClause>.toSql92OrderByClause(entityClass: Class<*>): String? = when {
    isEmpty() -> null
    else -> joinToString { it.toSql92OrderByClause(entityClass) }
}

/**
 * Converts the data loader property name to underlying database column name. However, if there is no such
 * property then it assumes that the data loader property name is already the column name and simply returns this.
 */
internal fun DataLoaderPropertyName.toNativeColumnName(clazz: Class<*>): NativePropertyName {
    val property: PropertyMeta = EntityMeta(clazz).properties.firstOrNull { it.name == this } ?: return this
    return property.dbColumnName
}
