package com.github.vokorm

import com.gitlab.mvysny.jdbiorm.Dao
import com.gitlab.mvysny.jdbiorm.OrderBy
import com.gitlab.mvysny.jdbiorm.Property
import com.gitlab.mvysny.jdbiorm.TableProperty
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
 * Converts Kotlin [KProperty1] to JDBI-ORM Expression ([TableProperty]). That allows you to construct JDBI-ORM Conditions easily:
 * ```kotlin
 * dao.findAll(Person::id.exp.eq(25))
 * ```
 * However, it's also possible to use [buildCondition] for a more Kotlin-like Condition construction.
 */
public inline val <reified T, V> KProperty1<T, V>.exp: TableProperty<T, V> get() = toExpression(T::class.java)

/**
 * Converts Kotlin [KProperty1] to JDBI-ORM Expression ([TableProperty]).
 */
public fun <T, V> KProperty1<T, V>.toExpression(receiverClass: Class<T>): TableProperty<T, V> = TableProperty.of(receiverClass, name)

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

public val LongRange.length: Long get() = if (isEmpty()) 0 else endInclusive - start + 1
public val IntRange.length: Int get() = if (isEmpty()) 0 else endInclusive - start + 1
