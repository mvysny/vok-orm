package com.github.vokorm.dataloader

import com.github.mvysny.dynatest.DynaTest
import com.github.mvysny.dynatest.expectList
import com.github.vokorm.*
import kotlin.test.expect

inline val <reified T: Entity<*>> Dao<T>.dataLoader: DataLoader<T>
    get() = EntityDataLoader(T::class.java)
val String.desc get() = SortClause(this, false)
val String.asc get() = SortClause(this, true)

class EntityDataProviderTest : DynaTest({

    withAllDatabases {
        test("noEntitiesTest") {
            val ds = Person.dataLoader
            expect(0) { ds.getCount() }
            expectList() { ds.fetch() }
        }

        test("sorting") {
            val ds = Person.dataLoader
            db { for (i in 15..90) Person(name = "test$i", age = i).save() }
            expect(76) { ds.getCount() }
            expect((90 downTo 15).toList()) { ds.fetch(sortBy = listOf("age".desc), range = 0..99).map { it.age } }
        }

        test("filterTest1") {
            db { for (i in 15..90) Person(name = "test$i", age = i).save() }
            val ds = Person.dataLoader.withFilter { Person::age between 30..60 }
            expect(31) { ds.getCount() }
            expect((30..60).toList()) { ds.fetch(sortBy = listOf("age".asc), range = 0..99).map { it.age } }
        }

        test("filterTest2") {
            db { for (i in 15..90) Person(name = "test$i", age = i).save() }
            val ds = Person.dataLoader
            val filter = filter<Person> { Person::age between 30..60 }
            expect(31) { ds.getCount(filter) }
            expect((30..60).toList()) { ds.fetch(sortBy = listOf("age".asc), filter = filter, range = 0..99).map { it.age } }
        }

        test("paging") {
            db { for (i in 15..90) Person(name = "test$i", age = i).save() }
            val ds = Person.dataLoader.withFilter { Person::age between 30..60 }
            expect((30..39).toList()) { ds.fetch(sortBy = listOf("age".asc), range = 0..9).map { it.age } }
            expect((40..49).toList()) { ds.fetch(sortBy = listOf("age".asc), range = 10..19).map { it.age } }
        }

        test("nativeQuery") {
            db { for (i in 15..90) Person(name = "test$i", age = i).save() }
            val ds = Person.dataLoader.withFilter { Person::age lt 60 and "age > :age"("age" to 29)}
            expect((30..59).toList()) { ds.fetch().map { it.age } }
        }
    }
})
