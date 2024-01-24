package com.github.vokorm

import com.gitlab.mvysny.jdbiorm.condition.Condition
import java.io.Serializable
import kotlin.reflect.KProperty1
import com.gitlab.mvysny.jdbiorm.condition.Expression

/**
 * Creates a [Condition] programmatically: `buildCondition { Person::age lt 25 }`
 */
public inline fun <reified T : Any> buildCondition(block: ConditionBuilder<T>.() -> Condition): Condition =
    block(ConditionBuilder(T::class.java))

/**
 * Running block with this class as its receiver will allow you to write expressions like this:
 * `Person::age lt 25`. Ultimately, the [Condition] is built.
 *
 * Containing these functions in this class will prevent polluting of the KProperty1 interface and also makes it type-safe.
 * @param clazz builds the query for this class.
 */
public class ConditionBuilder<T : Any>(public val clazz: Class<T>) {
    /**
     * Creates a condition where this property should be equal to [value]. Calls [Expression.eq].
     */
    public infix fun <R : Serializable?> KProperty1<T, R>.eq(value: R): Condition = toProperty(clazz).eq(value)

    /**
     * This property value must be less-than or equal to given [value]. Calls [Expression.le].
     */
    public infix fun <R> KProperty1<T, R?>.le(value: R): Condition = toProperty(clazz).le(value)

    /**
     * This property value must be less-than given [value]. Calls [Expression.lt].
     */
    public infix fun <R> KProperty1<T, R?>.lt(value: R): Condition = toProperty(clazz).lt(value)

    /**
     * This property value must be greater-than or equal to given [value]. Calls [Expression.ge].
     */
    public infix fun <R> KProperty1<T, R?>.ge(value: R): Condition = toProperty(clazz).ge(value)

    /**
     * This property value must be greater-than given [value]. Calls [Expression.gt].
     */
    public infix fun <R> KProperty1<T, R?>.gt(value: R): Condition = toProperty(clazz).gt(value)

    /**
     * This property value must not be equal to given [value]. Calls [Expression.ne].
     */
    public infix fun <R> KProperty1<T, R?>.ne(value: R): Condition = toProperty(clazz).ne(value)

    /**
     * This property value must be one of the values
     * provided in the [value] collection. Calls [Expression.in].
     */
    public infix fun <R> KProperty1<T, R>.`in`(value: Collection<R>): Condition = toProperty(clazz).`in`(value)

    /**
     * The <code>LIKE</code> operator.
     * @param pattern e.g. "%foo%"
     * @return the condition, not null.
     */
    public infix fun KProperty1<T, String?>.like(pattern: String?): Condition = toProperty(clazz).like(pattern)

    /**
     * The <code>ILIKE</code> operator.
     * @param pattern e.g. "%foo%"
     * @return the condition, not null.
     */
    public infix fun KProperty1<T, String?>.likeIgnoreCase(pattern: String?): Condition = toProperty(clazz).likeIgnoreCase(pattern)

    /**
     * Matches only values contained in given range.
     * @param range the range
     */
    public infix fun <R : Comparable<R>> KProperty1<T, R?>.between(range: ClosedRange<R>): Condition =
        toProperty(clazz).between(range.start, range.endInclusive)

    /**
     * Matches only when the property is null.
     */
    public val KProperty1<T, *>.isNull: Condition get() = toProperty(clazz).isNull

    /**
     * Matches only when the property is not null.
     */
    public val KProperty1<T, *>.isNotNull: Condition get() = toProperty(clazz).isNotNull

    /**
     * Matches only when the property is true. See [Expression.isTrue] for more details.
     */
    public val KProperty1<T, Boolean?>.isTrue: Condition get() = toProperty(clazz).isTrue

    /**
     * Matches only when the property is false. See [Expression.isFalse] for more details.
     */
    public val KProperty1<T, Boolean?>.isFalse: Condition get() = toProperty(clazz).isFalse
}

public infix fun Condition.and(other: Condition): Condition = and(other)
public infix fun Condition.or(other: Condition): Condition = or(other)
