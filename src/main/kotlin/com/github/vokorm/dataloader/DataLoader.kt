package com.github.vokorm.dataloader

import com.github.vokorm.Filter
import com.github.vokorm.SqlWhereBuilder
import java.io.Serializable

/**
 * Sorts beans by given [property], ascending or descending based on the value of the [asc] parameter.
 */
data class SortClause(val property: String, val asc: Boolean) : Serializable

/**
 * Provides paged access to instances of bean of type [T]. Typically provides data for some kind of a scrollable/paged table.
 */
interface DataLoader<T: Any> : Serializable {
    /**
     * Returns the number of items available which match given [filter].
     * @param filter optional filter which defines filtering to be used for counting the
     * number of items. If null all items are considered.
     */
    fun getCount(filter: Filter<T>? = null): Int

    /**
     * Fetches data from the back end. The items must match given [filter]
     * @param filter optional filter which defines filtering to be used for counting the
     * number of items. If null all items are considered.
     * @param sortBy optionally sort the beans according to given criteria.
     * @param range offset and limit to fetch
     * @return a list of items matching the query, may be empty.
     */
    fun getItems(filter: Filter<T>? = null, sortBy: List<SortClause> = listOf(), range: IntRange = 0..Int.MAX_VALUE): List<T>
}

/**
 * Returns a new data loader which always applies given [filter] and ANDs it with any filter given to [DataLoader.getCount] or [DataLoader.getItems].
 */
fun <T: Any> DataLoader<T>.withFilter(filter: Filter<T>): DataLoader<T> = FilteredDataLoader(filter, this)

/**
 * Returns a new data loader which always applies given [filter] and ANDs it with any filter given to [DataLoader.getCount] or [DataLoader.getItems].
 */
fun <T: Any> DataLoader<T>.withFilter(block: SqlWhereBuilder<T>.()->Filter<T>): DataLoader<T> =
    withFilter(SqlWhereBuilder<T>().block())

internal class FilteredDataLoader<T: Any>(val filter: Filter<T>, val delegate: DataLoader<T>) : DataLoader<T> {
    private fun and(other: Filter<T>?) = if (other == null) filter else filter.and(other)

    override fun getCount(filter: Filter<T>?): Int = delegate.getCount(and(filter))

    override fun getItems(filter: Filter<T>?, sortBy: List<SortClause>, range: IntRange): List<T> =
            delegate.getItems(and(filter), sortBy, range)
}
