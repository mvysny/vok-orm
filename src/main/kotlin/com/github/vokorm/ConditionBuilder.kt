package com.github.vokorm

import com.github.mvysny.vokdataloader.*
import com.gitlab.mvysny.jdbiorm.condition.Condition
import java.io.Serializable
import kotlin.reflect.KProperty1
import com.gitlab.mvysny.jdbiorm.condition.Expression

/**
 * Creates a [Condition] programmatically: `buildCondition { Person::age lt 25 }`
 */
public inline fun <reified T : Any> buildCondition(block: FilterBuilder<T>.() -> Filter<T>): Filter<T> =
    block(FilterBuilder(T::class.java))

/**
 * Running block with this class as its receiver will allow you to write expressions like this:
 * `Person::age lt 25`. Ultimately, the [Condition] is built.
 *
 * Containing these functions in this class will prevent polluting of the KProperty1 interface and also makes it type-safe.
 * @param clazz builds the query for this class.
 */
public class ConditionBuilder<T : Any>(public val clazz: Class<T>) {
    /**
     * Creates an condition where this property should be equal to [value]. Calls [Expression.eq].
     */
    public infix fun <R : Serializable?> KProperty1<T, R>.eq(value: R): Condition = toProperty(clazz).eq(value)

    /**
     * This property value must be less-than or equal to given [value]. Calls [Expression.le].
     */
    public infix fun <R> KProperty1<T, R?>.le(value: R): Condition = toProperty(clazz).le(value)

    /**
     * Creates an [OpFilter] with [CompareOperator.lt], requesting given property value to be less-than given [value].
     */
    @Suppress("UNCHECKED_CAST")
    public infix fun <R> KProperty1<T, R?>.lt(value: R): Filter<T> =
        OpFilter(name, value as Comparable<Any>, CompareOperator.lt)

    /**
     * Creates an [OpFilter] with [CompareOperator.ge], requesting given property value
     * to be greater-than or equal to given [value].
     */
    @Suppress("UNCHECKED_CAST")
    public infix fun <R> KProperty1<T, R?>.ge(value: R): Filter<T> =
        OpFilter(name, value as Comparable<Any>, CompareOperator.ge)

    /**
     * Creates an [OpFilter] with [CompareOperator.gt], requesting given
     * property value to be greater-than given [value].
     */
    @Suppress("UNCHECKED_CAST")
    public infix fun <R> KProperty1<T, R?>.gt(value: R): Filter<T> =
        OpFilter(name, value as Comparable<Any>, CompareOperator.gt)

    /**
     * Creates an [OpFilter] with [CompareOperator.ne], requesting given
     * property value to be not equal given [value].
     */
    @Suppress("UNCHECKED_CAST")
    public infix fun <R> KProperty1<T, R?>.ne(value: R): Filter<T> =
        OpFilter(name, value as Comparable<Any>, CompareOperator.ne)

    /**
     * Creates an [InFilter], requesting given property value to be one of the values
     * provided in the [value] collection.
     */
    @Suppress("UNCHECKED_CAST")
    public infix fun <C : Comparable<*>, R : Collection<C>> KProperty1<T, C>.`in`(value: R): Filter<T> = InFilter(name, value)

    @Deprecated("use startsWith", ReplaceWith("this.startsWith(prefix)"))
    public infix fun KProperty1<T, String?>.like(prefix: String): Filter<T> = startsWith(prefix)

    /**
     * A [StartsWithFilter]. It performs the 'starts-with' matching, case-sensitive.
     *
     * SQL: This filter tends to perform quite well on indexed columns. If you need a substring
     * matching, then you actually need to employ full text search
     * capabilities of your database. For example [PostgreSQL full-text search](https://www.postgresql.org/docs/9.5/static/textsearch.html).
     *
     * See [contains] for substring matching.
     * @param prefix the prefix, automatically appended with `%` when the SQL query is constructed. The 'starts-with' is matched
     * case-sensitive.
     */
    public infix fun KProperty1<T, String?>.startsWith(prefix: String): Filter<T> =
        StartsWithFilter(name, prefix, false)

    @Deprecated("use istartsWith", ReplaceWith("this.istartsWith(prefix)"))
    public infix fun KProperty1<T, String?>.ilike(prefix: String): Filter<T> =
        istartsWith(prefix)

    /**
     * A [StartsWithFilter]. It performs the 'starts-with' matching, case-insensitive.
     *
     * SQL: This filter tends to perform quite well on indexed columns. If you need a substring
     * matching, then you actually need to employ full text search
     * capabilities of your database. For example [PostgreSQL full-text search](https://www.postgresql.org/docs/9.5/static/textsearch.html).
     *
     * See [icontains] for substring matching.
     * @param prefix the prefix, automatically appended with `%` when the SQL query is constructed. The 'starts-with' is matched
     * case-sensitive.
     */
    public infix fun KProperty1<T, String?>.istartsWith(prefix: String): Filter<T> =
        StartsWithFilter(name, prefix)

    /**
     * Matches only values contained in given range.
     * @param range the range
     */
    public infix fun <R> KProperty1<T, R?>.between(range: ClosedRange<R>): Filter<T> where R : Number, R : Comparable<R> =
        this.ge(range.start as Number) and this.le(range.endInclusive as Number)

    /**
     * Matches only when the property is null. Uses [IsNullFilter].
     */
    public val KProperty1<T, *>.isNull: Filter<T> get() = IsNullFilter(name)

    /**
     * Matches only when the property is not null. Uses [IsNotNullFilter].
     */
    public val KProperty1<T, *>.isNotNull: Filter<T> get() = IsNotNullFilter(name)

    /**
     * Matches only when the property is true. Uses [EqFilter] with the value of `true`.
     */
    public val KProperty1<T, Boolean?>.isTrue: Filter<T> get() = EqFilter(name, true)

    /**
     * Matches only when the property is false. Uses [EqFilter] with the value of `false`.
     */
    public val KProperty1<T, Boolean?>.isFalse: Filter<T> get() = EqFilter(name, false)

    /**
     * Allows for a native SQL query: `"age < :age_p"("age_p" to 60)`
     */
    public operator fun String.invoke(vararg params: Pair<String, Any?>): Filter<T> =
        NativeSqlFilter(this, mapOf(*params))

    /**
     * [SubstringFilter] which performs the case-sensitive 'substring' matching. Usually only used for in-memory
     * filtering since it performs quite poorly on SQL databases.
     *
     * *SQL WARNING:* The database performance is very poor, even on indexed columns - the database effectively performs full
     * table scan. Instead you should use the [FullTextFilter].
     */
    public infix fun KProperty1<T, String?>.contains(prefix: String): Filter<T> =
        SubstringFilter(name, prefix, false)

    /**
     * [SubstringFilter] which performs the case-insensitive 'substring' matching. Usually only used for in-memory
     * filtering since it performs quite poorly on SQL databases.
     *
     * *SQL WARNING:* The database performance is very poor, even on indexed columns - the database effectively performs full
     * table scan. Instead you should use the [FullTextFilter].
     */
    public infix fun KProperty1<T, String?>.icontains(prefix: String): Filter<T> =
        SubstringFilter(name, prefix)
}

@Deprecated("Use FilterBuilder", replaceWith = ReplaceWith("FilterBuilder<T>"))
public typealias SqlWhereBuilder<T> = FilterBuilder<T>
