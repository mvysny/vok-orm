package com.github.vokorm

import java.io.Serializable
import java.util.function.BiPredicate
import java.util.function.Predicate
import kotlin.reflect.KProperty1

/**
 * A generic filter which filters items of type [T]. Implementors must define how exactly the items are filtered. The filter is retrofitted as a serializable
 * predicate so that in-memory filtering is also supported.
 *
 * Implementor detail: [Any.equals]/[Any.hashCode]/[Any.toString] must be implemented properly, so that the filter type, the property name and the value which we compare against must be
 * taken into consideration.
 * @param T the bean type upon which we will perform the filtering. This is not used by the filter directly, but it's required by [Predicate] which this
 * interface extends.
 */
interface Filter<T: Any> : Serializable {
    infix fun and(other: Filter<in T>): Filter<T> = AndFilter(setOf(this, other))
    infix fun or(other: Filter<in T>): Filter<T> = OrFilter(setOf(this, other))
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
    fun toSQL92(): String
    fun getSQL92Parameters(): Map<String, Any?>
}

/**
 * Filters beans by comparing given [databaseColumnName] to some expected [value]. Check out implementors for further details.
 */
abstract class BeanFilter<T: Any> : Filter<T> {
    abstract val databaseColumnName: String
    abstract val value: Any?
    /**
     * A simple way on how to make parameter names unique and tie them to filter instances ;)
     */
    val parameterName get() = "p${System.identityHashCode(this).toString(36)}"
    override fun getSQL92Parameters(): Map<String, Any?> = mapOf(parameterName to value)
}

/**
 * A filter which tests for value equality. Allows nulls.
 */
data class EqFilter<T: Any>(override val databaseColumnName: String, override val value: Any?) : BeanFilter<T>() {
    override fun toString() = "$databaseColumnName = $value"
    override fun toSQL92() = "$databaseColumnName = :$parameterName"
}

enum class CompareOperator(val sql92Operator: String) : BiPredicate<Comparable<Any>?, Comparable<Any>> {
    eq("=") { override fun test(t: Comparable<Any>?, u: Comparable<Any>) = t == u },
    lt("<") { override fun test(t: Comparable<Any>?, u: Comparable<Any>) = t != null && t < u },
    le("<=") { override fun test(t: Comparable<Any>?, u: Comparable<Any>) = t != null && t <= u },
    gt(">") { override fun test(t: Comparable<Any>?, u: Comparable<Any>) = t != null && t > u },
    ge(">=") { override fun test(t: Comparable<Any>?, u: Comparable<Any>) = t != null && t >= u },
}

/**
 * A filter which supports less than, less or equals than, etc. Filters out null values.
 */
data class OpFilter<T: Any>(override val databaseColumnName: String, override val value: Comparable<Any>, val operator: CompareOperator) : BeanFilter<T>() {
    override fun toString() = "$databaseColumnName ${operator.sql92Operator} $value"
    override fun toSQL92() = "$databaseColumnName ${operator.sql92Operator} :$parameterName"
}

data class IsNullFilter<T: Any>(override val databaseColumnName: String) : BeanFilter<T>() {
    override val value: Any? = null
    override fun toSQL92() = "$databaseColumnName is null"
}

data class IsNotNullFilter<T: Any>(override val databaseColumnName: String) : BeanFilter<T>() {
    override val value: Any? = null
    override fun toSQL92() = "$databaseColumnName is not null"
}

/**
 * A LIKE filter. It performs the 'starts-with' matching which tends to perform quite well on indexed columns. If you need a substring
 * matching, then you actually need to employ full text search
 * capabilities of your database. For example [PostgreSQL full-text search](https://www.postgresql.org/docs/9.5/static/textsearch.html).
 *
 * There is no point in supporting substring matching: it performs a full table scan when used, regardless of whether the column contains
 * the index or not. If you really wish for substring matching, you probably want a full-text search instead which is implemented using
 * a different keywords.
 * @param substring the substring, automatically appended with `%` when the SQL query is constructed. The substring is matched
 * case-sensitive.
 */
class LikeFilter<T: Any>(override val databaseColumnName: String, substring: String) : BeanFilter<T>() {
    override val value = "${substring.trim()}%"
    override fun toString() = """$databaseColumnName LIKE "$value""""
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false
        other as LikeFilter<*>
        if (databaseColumnName != other.databaseColumnName) return false
        if (value != other.value) return false
        return true
    }
    override fun hashCode(): Int {
        var result = value.hashCode()
        result = 31 * result + value.hashCode()
        return result
    }
    override fun toSQL92() = "$databaseColumnName LIKE :$parameterName"
}

/**
 * An ILIKE filter, performs case-insensitive matching. It performs the 'starts-with' matching which tends to perform quite well on indexed columns. If you need a substring
 * matching, then you actually need to employ full text search
 * capabilities of your database. For example [PostgreSQL full-text search](https://www.postgresql.org/docs/9.5/static/textsearch.html).
 *
 * There is no point in supporting substring matching: it performs a full table scan when used, regardless of whether the column contains
 * the index or not. If you really wish for substring matching, you probably want a full-text search instead which is implemented using
 * a different keywords.
 * @param substring the substring, automatically appended with `%` when the SQL query is constructed. The substring is matched
 * case-insensitive.
 */
class ILikeFilter<T: Any>(override val databaseColumnName: String, substring: String) : BeanFilter<T>() {
    private val substring = substring.trim()
    override val value = "%${substring.trim()}%"
    override fun toString() = """$databaseColumnName ILIKE "$value""""
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false
        other as ILikeFilter<*>
        if (databaseColumnName != other.databaseColumnName) return false
        if (value != other.value) return false
        return true
    }
    override fun hashCode(): Int {
        var result = value.hashCode()
        result = 31 * result + value.hashCode()
        return result
    }
    override fun toSQL92() = "$databaseColumnName ILIKE :$parameterName"
}

class AndFilter<T: Any>(children: Set<Filter<in T>>) : Filter<T> {
    val children: Set<Filter<in T>> = children.flatMap { if (it is AndFilter) it.children else listOf(it) }.toSet()
    override fun toString() = children.joinToString(" and ", "(", ")")
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false
        other as AndFilter<*>
        if (children != other.children) return false
        return true
    }

    override fun hashCode() = children.hashCode()
    override fun toSQL92() = children.joinToString(" and ", "(", ")") { it.toSQL92() }
    override fun getSQL92Parameters(): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        children.forEach { map.putAll(it.getSQL92Parameters()) }
        return map
    }
}

class OrFilter<T: Any>(children: Set<Filter<in T>>) : Filter<T> {
    val children: Set<Filter<in T>> = children.flatMap { if (it is OrFilter) it.children else listOf(it) }.toSet()
    override fun toString() = children.joinToString(" or ", "(", ")")
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false
        other as OrFilter<*>
        if (children != other.children) return false
        return true
    }
    override fun hashCode() = children.hashCode()
    override fun toSQL92() = children.joinToString(" or ", "(", ")") { it.toSQL92() }
    override fun getSQL92Parameters(): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        children.forEach { map.putAll(it.getSQL92Parameters()) }
        return map
    }
}

fun <T: Any> Set<Filter<T>>.and(): Filter<T>? = when (size) {
    0 -> null
    1 -> first()
    else -> AndFilter(this)
}
fun <T: Any> Set<Filter<T>>.or(): Filter<T>? = when (size) {
    0 -> null
    1 -> first()
    else -> OrFilter(this)
}

/**
 * Running block with this class as its receiver will allow you to write expressions like this:
 * `Person::age lt 25`. Does not support joins - just use the plain old SQL 92 where syntax for that ;)
 *
 * Containing these functions in this class will prevent polluting of the KProperty1 interface and also makes it type-safe.
 *
 * This looks like too much Kotlin syntax magic. Promise me to use this for simple Entities and/or programmatic where creation only ;)
 * @param clazz builds the query for this class.
 */
class SqlWhereBuilder<T: Any>(val clazz: Class<T>) {
    private val meta = clazz.entityMeta
    private val KProperty1<T, *>.dbname: String get() = meta.getProperty(this).dbColumnName

    infix fun <R: Serializable?> KProperty1<T, R>.eq(value: R): Filter<T> = EqFilter(dbname, value)
    @Suppress("UNCHECKED_CAST")
    infix fun <R> KProperty1<T, R?>.le(value: R): Filter<T> =
        OpFilter(dbname, value as Comparable<Any>, CompareOperator.le)
    @Suppress("UNCHECKED_CAST")
    infix fun <R> KProperty1<T, R?>.lt(value: R): Filter<T> =
        OpFilter(dbname, value as Comparable<Any>, CompareOperator.lt)
    @Suppress("UNCHECKED_CAST")
    infix fun <R> KProperty1<T, R?>.ge(value: R): Filter<T> =
        OpFilter(dbname, value as Comparable<Any>, CompareOperator.ge)
    @Suppress("UNCHECKED_CAST")
    infix fun <R> KProperty1<T, R?>.gt(value: R): Filter<T> =
        OpFilter(dbname, value as Comparable<Any>, CompareOperator.gt)

    /**
     * A LIKE filter. It performs the 'starts-with' matching which tends to perform quite well on indexed columns. If you need a substring
     * matching, then you actually need to employ full text search
     * capabilities of your database. For example [PostgreSQL full-text search](https://www.postgresql.org/docs/9.5/static/textsearch.html).
     *
     * There is no point in supporting substring matching: it performs a full table scan when used, regardless of whether the column contains
     * the index or not. If you really wish for substring matching, you probably want a full-text search instead which is implemented using
     * a different keywords.
     * @param value the prefix, automatically appended with `%` when the SQL query is constructed. The substring is matched
     * case-sensitive.
     */
    infix fun KProperty1<T, String?>.like(value: String): Filter<T> = LikeFilter(dbname, value)

    /**
     * An ILIKE filter, performs case-insensitive matching. It performs the 'starts-with' matching which tends to perform quite well on indexed columns. If you need a substring
     * matching, then you actually need to employ full text search
     * capabilities of your database. For example [PostgreSQL full-text search](https://www.postgresql.org/docs/9.5/static/textsearch.html).
     *
     * There is no point in supporting substring matching: it performs a full table scan when used, regardless of whether the column contains
     * the index or not. If you really wish for substring matching, you probably want a full-text search instead which is implemented using
     * a different keywords.
     * @param value the substring, automatically appended with `%` when the SQL query is constructed. The substring is matched
     * case-insensitive.
     */
    infix fun KProperty1<T, String?>.ilike(value: String): Filter<T> = ILikeFilter(dbname, value)
    /**
     * Matches only values contained in given range.
     * @param range the range
     */
    infix fun <R> KProperty1<T, R?>.between(range: ClosedRange<R>): Filter<T> where R: Number, R: Comparable<R> =
            this.ge(range.start as Number) and this.le(range.endInclusive as Number)
    val KProperty1<T, *>.isNull: Filter<T> get() = IsNullFilter(dbname)
    val KProperty1<T, *>.isNotNull: Filter<T> get() = IsNotNullFilter(dbname)
    val KProperty1<T, Boolean?>.isTrue: Filter<T> get() = EqFilter(dbname, true)
    val KProperty1<T, Boolean?>.isFalse: Filter<T> get() = EqFilter(dbname, false)

    /**
     * Allows for a native query: `"age < :age_p"("age_p" to 60)`
     */
    operator fun String.invoke(vararg params: Pair<String, Any?>) = NativeSqlFilter<T>(this, mapOf(*params))
}

/**
 * Just write any native SQL into [where], e.g. `age > 25 and name like :name`; don't forget to properly fill in the [params] map.
 *
 * Does not support in-memory filtering and will throw an exception.
 */
data class NativeSqlFilter<T: Any>(val where: String, val params: Map<String, Any?>) : Filter<T> {
    override fun toSQL92() = where
    override fun getSQL92Parameters(): Map<String, Any?> = params
}

/**
 * Creates a filter programmatically: `buildFilter { Person::age lt 25 }`
 */
inline fun <reified T: Any> buildFilter(block: SqlWhereBuilder<T>.()-> Filter<T>): Filter<T> = block(SqlWhereBuilder(T::class.java))
