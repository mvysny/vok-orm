package com.github.vokorm

import com.github.mvysny.vokdataloader.DataLoaderPropertyName
import com.github.mvysny.vokdataloader.NativePropertyName
import com.github.mvysny.vokdataloader.SortClause
import com.gitlab.mvysny.jdbiorm.*
import com.gitlab.mvysny.jdbiorm.quirks.DatabaseVariant
import com.gitlab.mvysny.jdbiorm.quirks.Quirks
import org.jdbi.v3.core.Handle
import kotlin.reflect.KProperty1

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
    val property: PropertyMeta = EntityMeta.of(clazz).properties.firstOrNull { it.name.name == this } ?: return this
    return property.dbName.unqualifiedName
}

/**
 * Converts Kotlin [KProperty1] to JDBI-ORM [TableProperty], allowing you to construct JDBI-ORM Conditions easily:
 * ```kotlin
 * dao.findAll(Person::id.exp.eq(25))
 * ```
 */
public inline val <reified T, V> KProperty1<T, V>.exp: TableProperty<T, V> get() = TableProperty.of(T::class.java, name)

/**
 * Produces [OrderBy] suitable to be passed into [Dao.findAll]
 * ```kotlin
 * dao.findAll(Person::id.asc)
 * ```
 */
public val KProperty1<*, *>.asc: OrderBy get() = OrderBy(Property.Name(name), OrderBy.Order.ASC)

/**
 * Produces [OrderBy] suitable to be passed into [Dao.findAll]
 * ```kotlin
 * dao.findAll(Person::id.asc)
 * ```
 */
public val KProperty1<*, *>.desc: OrderBy get() = OrderBy(Property.Name(name), OrderBy.Order.DESC)
