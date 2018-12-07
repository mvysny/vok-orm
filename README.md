[![Build Status](https://travis-ci.org/mvysny/vok-orm.svg?branch=master)](https://travis-ci.org/mvysny/vok-orm)
[![Join the chat at https://gitter.im/vaadin/vaadin-on-kotlin](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/vaadin/vaadin-on-kotlin?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![GitHub tag](https://img.shields.io/github/tag/mvysny/vok-orm.svg)](https://github.com/mvysny/vok-orm/tags)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.mvysny.vokorm/vok-orm/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.mvysny.vokorm/vok-orm)

# Vaadin-On-Kotlin database mapping library

`vok-orm` allows you to present the data from database rows as objects and embellish these data objects with business logic methods.

## Usage

Just add the following lines to your Gradle script, to include this library in your project:
```groovy
repositories {
    jcenter()  // or mavenCentral()
}
dependencies {
    compile("com.github.mvysny.vokorm:vok-orm:x.y")
}
```

> Note: obtain the newest version from the tag name at the top of the page

Maven: (it's very simple since vok-orm is in Maven Central):

```xml
<project>
	<dependencies>
		<dependency>
			<groupId>com.github.mvysny.vokorm</groupId>
			<artifactId>vok-orm</artifactId>
			<version>x.y</version>
		</dependency>
    </dependencies>
</project>
```

## Usage Examples

Say that we have a table containing a list of beverage categories, such as Cider or Beer. The H2 DDL for such table is simple:

```sql92
create TABLE CATEGORY (
  id bigint auto_increment PRIMARY KEY,
  name varchar(200) NOT NULL
);
create UNIQUE INDEX idx_category_name ON CATEGORY(name);
```

> **Note:** We expect that the programmer wants to write the DDL scripts herself, to make full
use of the DDL capabilities of the underlying database.
We will therefore not hide the DDL behind some type-safe generator API.

Such entity can be mapped to a data class as follows:
```kotlin
data class Category(override var id: Long? = null, var name: String = "") : Entity<Long>
```
(the `id` is nullable since its value is initially `null` until the category is actually created and the id is assigned by the database).

The `Category` class is just a simple data class: there are no hidden private fields added by
runtime enhancements, no hidden lazy loading - everything is pre-fetched upfront. Because of that,
the class can be passed around the application freely as a DTO (data transfer object),
without the fear of failing with
`DetachedException` when accessing properties. Since `Entity` is `Serializable`, you can
also store the entity into a session. 

> The Category class (or any entity class for that matter) must have all fields pre-initialized, so that Kotlin creates a zero-arg constructor.
Zero-arg constructor is mandated by Sql2o, in order for Sql2o to be able to construct
instances of entity class for every row returned.

By implementing the `Entity<Long>` interface, we are telling vok-orm that the primary key is of type `Long`; this will be important later on when using Dao.
The [Entity](src/main/kotlin/com/github/vokorm/Mapping.kt) interface brings in three useful methods:

* `save()` which either creates a new row by generating the INSERT statement (if the ID is null), or updates the row by generating the UPDATE statement (if the ID is not null)
* `delete()` which deletes the row identified by the `id` primary key from the database.
* `validate()` validates the bean. By default all `javax.validation` annotations are validated; you can override this method to provide further bean-level validations.

The INSERT/UPDATE statement is automatically constructed by the `save()` method, simply by enumerating all non-transient and non-ignored properties of
the bean using reflection and fetching their values. See the [Entity](src/main/kotlin/com/github/vokorm/Mapping.kt) sources for more details.
You can annotate the `Category` class with the `@Table(dbname = "Categories")` annotation, to specify a different table name.

The category can now be created easily:

```kotlin
Category(name = "Beer").save()
```

But how do we specify the target database where to store the category in? 

### Connecting to a database

As a bare minimum, you need to specify the JDBC URL
to the `VokOrm.dataSourceConfig` first. It's a [Hikari-CP](https://brettwooldridge.github.io/HikariCP/) configuration file which contains lots of other options as well.
It comes pre-initialized with sensible default settings.

> Hikari-CP is a JDBC connection pool which manages a pool of JDBC connections since they are expensive to create. Typically all projects
use some sort of JDBC connection pooling, and `vok-orm` uses Hikari-CP.

For example, to use an in-memory H2 database, just add H2 onto the classpath as a Gradle dependency: `compile 'com.h2database:h2:1.4.196'`. Then,
configure vok-orm as follows:

```kotlin
VokOrm.dataSourceConfig.apply {
    jdbcUrl = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1"
}
```

After you have configured the JDBC URL, just call `VokOrm.init()` which will initialize
Hikari-CP's connection pool. After the connection pool is initialized, you can simply call
the `db{}` function to run the
block in a database transaction. The `db{}` function will acquire new connection from the
connection pool; then it will start a transaction and it will provide you with means to execute SQL commands:

```kotlin
db {
    con.createQuery("delete from Category where id = :id")
        .addParameter("id", id)
        .executeUpdate()
}
```

You can call this function from anywhere; you don't need to use dependency injection or anything like that.
That is precisely how the `save()` function saves the bean - it simply calls the `db {}` function and executes
an appropriate INSERT/UPDATE statement.

The function will automatically roll back the transaction on any exception thrown out from the block (both checked and unchecked).

After you're done, call `VokOrm.destroy()` to close the pool.

> You can call methods of this library from anywhere. You don't need to be running inside of the JavaEE or Spring container or
any container at all - you can actually use this library from a plain JavaSE main method.

Full example of a `main()` method that does all of the above:

```kotlin
fun main(args: Array<String>) {
    VokOrm.dataSourceConfig.apply {
        jdbcUrl = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1"
    }
    VokOrm.init()
    db {
        con.createQuery("create TABLE CATEGORY (id bigint auto_increment PRIMARY KEY, name varchar(200) NOT NULL );").executeUpdate()
    }
    db {
        (0..100).forEach { Category(name = "cat $it").save() }
    }
    VokOrm.destroy()
}
```

See the [vok-orm-playground](https://gitlab.com/mvysny/vok-orm-playground)
project which contains such `main` method, all JDBC drivers pre-loaded and
simple instructions on how to query different database kinds.

> *Note*: for the sake of simplicity we're running the `CREATE TABLE` as a query. For a persistent database
it's definitely better to use [Flyway](https://flywaydb.org/) as described below.

### Finding Categories

The so-called finder (or Dao) methods actually resemble factory methods since they also produce instances of Categories. The best place for such
methods is on the `Category` class itself. We can write all of the necessary finders ourselves, by using the `db{}`
method as stated above; however vok-orm already provides a set of handy methods for you. All you need
to do is for the companion object to implement the `Dao` interface:

```kotlin
data class Category(override var id: Long? = null, var name: String = "") : Entity<Long> {
    companion object : Dao<Category>
}
```

Since Category's companion object implements the `Dao` interface, Category will now be outfitted
with several useful finder methods (static extension methods
that are attached to the [Dao](src/main/kotlin/com/github/vokorm/Dao.kt) interface itself):

* `Category.findAll()` will return a list of all categories
* `Category.getById(25L)` will fetch a category with the ID of 25, failing if there is no such category
* `Category.findById(25L)` will fetch a category with ID of 25, returning `null` if there is no such category
* `Category.deleteAll()` will delete all categories
* `Category.deleteById(42L)` will delete a category with ID of 42
* `Category.count()` will return the number of rows in the Category table.
* `Category.findBy { "name = :name1 or name = :name2"("name1" to "Beer", "name2" to "Cider") }` will find all categories with the name of "Beer" or "Cider".
  This is an example of a parametrized select, from which you only need to provide the WHERE clause.
* `Category.deleteBy { (Category::name eq "Beer") or (Category::name eq "Cider") }` will delete all categories
  matching given criteria. This is an example of a statically-typed matching criteria which
  is converted into the WHERE clause.
* `Category.getBy { "name = :name"("name" to "Beer") }` will fetch exactly one matching category, failing if there is no such category or there are more than one.
* `Category.findSpecificBy { "name = :name"("name" to "Beer") }` will fetch one matching category, failing if there are more than one. Returns `null` if there is none.
* `Category.count { "name = :name"("name" to "Beer") }` will return the number of rows in the Category table matching given query.

In the spirit of type safety, the finder methods will only accept `Long` (or whatever is the type of
the primary key in the `Entity<x>` implementation clause). 

You can of course add your own custom finder methods into the Category companion object. For example:

```kotlin
data class Category(override var id: Long? = null, var name: String = "") : Entity<Long> {
    companion object : Dao<Category> {
        fun findByName(name: String): Category? = findSpecificBy { Category::name eq name }
        fun getByName(name: String): Category = getBy { Category::name eq name }
        fun existsWithName(name: String): Boolean = count { Category::name eq name } > 0
    }
}
```  

> **Note**: If you don't want to use the Entity interface for some reason (for example when the table has no primary key), you can still include
useful finder methods by making the companion object to implement the `DaoOfAny` interface. The finder methods such as `findById()` will accept
`Any` as a primary key.

### Adding Reviews

Let's add the second table, the "Review" table. The Review table is a list of reviews for
various drinks; it back-references the drink category as a foreign key into the `Category` table:

```sql92
create TABLE REVIEW (
  id bigint auto_increment PRIMARY KEY,
  beverageName VARCHAR(200) not null,
  score TINYINT NOT NULL,
  date DATE not NULL,
  category BIGINT,
  count TINYINT not null
);
alter table Review add CONSTRAINT r_score_range CHECK (score >= 1 and score <= 5);
alter table Review add FOREIGN KEY (category) REFERENCES Category(ID);
alter table Review add CONSTRAINT r_count_range CHECK (count >= 1 and count <= 99);
create INDEX idx_beverage_name ON Review(beverageName);
```

The mapping class is as follows:
```kotlin
/**
 * Represents a beverage review.
 * @property score the score, 1..5, 1 being worst, 5 being best
 * @property date when the review was done
 * @property category the beverage category [Category.id]
 * @property count times tasted, 1..99
 */
open class Review(override var id: Long? = null,
                  var score: Int = 1,
                  var beverageName: String = "",
                  var date: LocalDate = LocalDate.now(),
                  var category: Long? = null,
                  var count: Int = 1) : Entity<Long> {

    companion object : Dao<Review>
}
```

Now if we want to delete a category, we need to first set the `Review.category` value to `null` for all reviews that
are linked to that very category, otherwise
we will get a foreign constraint violation. It's quite easy: just override the `delete()` method in the
`Category` class as follows:

```kotlin
data class Category(...) : Entity<Long> {
    ...
    override fun delete() {
        db {
            if (id != null) {
                con.createQuery("update Review set category = NULL where category=:catId")
                        .addParameter("catId", id!!)
                        .executeUpdate()
            }
            super.delete()
        }
    }
}
```

> **Note:** for all slightly more complex queries it's a good practice to simply use the Sql2o API - we will simply pass in the SQL command as a String to Sql2o.

As you can see, you can use the Sql2o connection yourself, to execute any kind of SELECT/UPDATE/INSERT/DELETE statements as you like.
For example you can define static finder or computation method into the `Review` companion object:

```kotlin
    companion object : Dao<Review> {
        /**
         * Computes the total sum of [count] for all reviews belonging to given [categoryId].
         * @return the total sum, 0 or greater.
         */
        fun getTotalCountForReviewsInCategory(categoryId: Long): Long = db {
            con.createQuery("select sum(r.count) from Review r where r.category = :catId")
                    .addParameter("catId", categoryId)
                    .executeScalar(Long::class.java) ?: 0L
        }
    }
```

Then we can outfit the Category itself with this functionality, by adding an extension method to compute this value:
```kotlin
fun Category.getTotalCountForReviews(): Long = Review.getTotalCountForReviewsInCategory(id!!)
```

Note how freely and simply we can add useful business logic methods to entities. It's because:

* the entities are just plain old classes with no hidden fields and no runtime enhancements, and
* because we can invoke `db{}` freely from anywhere.

### Joins

When we display a list of reviews (say, in a Vaadin Grid), we want to display an actual category name instead of the numeric category ID.
We can take advantage of Sql2o simply matching all SELECT column names into bean fields; all we have to do is to:

* extend the `Review` class and add the `categoryName` field which will carry the category name information;
* write a SELECT that will return all of the `Review` fields, and, additionally, the `categoryName` field

Let's thus create a `ReviewWithCategory` class:

```kotlin
open class ReviewWithCategory : Review() {
    @As("name")
    var categoryName: String? = null
}
```

> Note the `@As` annotation which tells vok-orm that the field is named differently in the database. Often the database naming schema
is different from Kotlin's naming schema, for example `NUMBER_OF_PETS` would be represented by the `numberOfPets` in the Kotlin class.
You can use database aliases, for example `SELECT NUMBER_OF_PETS AS numberOfPets`. However note that you can't then add a `WHERE` clause on
the `numberOfPets` alias - that's not supported by SQL databases. See [Issue #5](https://github.com/mvysny/vok-orm/issues/5) for more details.
Currently we don't use WHERE in our examples so you're free to use aliases, but aliases do not work with Data Loaders and therefore it's a good
practice to use `@As` instead of SQL aliases.

Now we can add a new finder function into `Review`'s companion object:

```kotlin
companion object : Dao<Review> {
    ...
    fun findReviews(): List<ReviewWithCategory> = db {
        con.createQuery("""select r.*, c.name
            FROM Review r left join Category c on r.category = c.id
            ORDER BY r.name""")
                .executeAndFetch(ReviewWithCategory::class.java)
    }
}
```

We can take Sql2o's mapping capabilities to full use: we can craft any SELECT we want,
and then we can create a holder class that will not be an entity itself, but will merely hold the result of that SELECT.
The only thing that matters is that the class will have properties named exactly as the columns in the SELECT statement (or properly aliased
using the `@As` annotation):

```kotlin
data class Beverage(@As("beverageName") var name: String = "", @As("name") var category: String? = null) : Serializable {
    companion object {
        fun findAll(): List<Beverage> = db {
            con.createQuery("select r.beverageName, c.name from Review r left join Category c on r.category = c.id")
                .executeAndFetch(Beverage::class.java)
        }
    }
}
```

We just have to make sure that all of the `Beverage`'s fields are pre-initialized, so that the `Beverage` class has a zero-arg constructor.
If not, Sql2o will throw an exception in runtime, stating that the `Beverage` class has no zero-arg constructor.

## Data Loaders

Very often the UI frameworks provide some kind of tabular component which allows for viewing database tables, or even outcomes of any SELECT
command (possibly joined). An example of such tabular component is the Vaadin Grid; you can see the [live demo](https://vok-crud.herokuapp.com/crud)
of the Grid for yourself.

Typically such tables provide sorting and filtering for the user; since they fetch data lazily as the user scrolls the table, the table must be
able to fetch data in pages.

vok-orm provide the Data Loaders which offer all of the above-mentioned functionality: sorting, filtering and lazy-loading. You can check out
the API here: [DataLoader.kt](src/main/kotlin/com/github/vokorm/dataloader/DataLoader.kt). You then need to write a thin wrapper which wraps
the `DataLoader` and adapts it to the API as required by the particular tabular component from a particular framework. However, since all
of the functionality is provided, the wrapper is typically thin and easy to write.

vok-orm provides two concrete implementations of data loaders out-of-the-box: the `EntityDataLoader` and the `SqlDataLoader`.

### EntityDataLoader

The [EntityDataLoader](src/main/kotlin/com/github/vokorm/dataloader/EntityDataLoader.kt) is able to provide instances of any class which implements the `Entity` interface. Simply create the `EntityDataLoader`
instance for your entity class and you're good to go.

The `EntityDataLoader` honors the `@As` annotation when mapping class instances from the outcome of the `SELECT *` clause. If you don't use SQL aliases
but you stick to use `@As`, then you can use the `Filter` class hierarchy to filter out the results, and you can use `SortClause` to sort
the results. Just keep in mind to pass in the database column name into the `Filter` and `SortClause`, and not the bean property name.

Note that the `EntityDataLoader` will construct the entire SQL SELECT command by itself - you cannot change the way it's constructed. This way
it is very simple to use the `EntityDataLoader`. If you need a full power of the SQL SELECT command, use the `SqlDataLoader`, or
create a database view.

### SqlDataLoader

The [SqlDataLoader](src/main/kotlin/com/github/vokorm/dataloader/SqlDataLoader.kt) is able to map the outcome of any SELECT command supplied by you,
onto a bean. You can use `SqlDataLoader` to map the outcome of joins, stored procedure calls, anything.

The `SqlDataLoader` honors the `@As` annotation when mapping class instances from the outcome of the `SELECT *` clause. If you don't use SQL aliases
but you stick to use `@As`, then you can use the `Filter` class hierarchy to filter out the results, and you can use `SortClause` to sort
the results. Just keep in mind to pass in the database column name into the `Filter` and `SortClause`, and not the bean property name.

## Aliases

As said before, if you want to use `Filter` to add SQL conditions to the SQL WHERE clause, you must annotate fields of your bean using the `@As`
annotation. You can't use SQL aliases to bypass `@As` and map directly to bean field names:

* INSERTs/UPDATEs issued by the `Dao` for your entities will fail since they will use the bean field names instead of actual column name
* You will be able to receive outcome of the SELECT and map it onto a bean, but auto-generated `Filter` objects (e.g. from the Grid filter-to-vok-orm-filters,
  or from the type-safe `SqlWhereBuilder` class) will use aliased column names (bean property names) when constructing the SQL WHERE clause, causing the database
  to fail to execute such SELECT command. Please see [Issue 5](https://github.com/mvysny/vok-orm/issues/5) for more details.

## A main() method Example

Using the vok-orm library from a JavaSE main method:

```kotlin
data class Person(
    override var id: Long? = null,
    var name: String,
    var age: Int,
    var dateOfBirth: LocalDate? = null,
    var recordCreatedAt: Instant? = null
) : Entity<Long> {
    override fun save() {
        if (id == null) {
            if (modified == null) modified = Instant.now()
        }
        super.save()
    }
    
    companion object : Dao<Person>
}

fun main(args: Array<String>) {
    VokOrm.dataSourceConfig.apply {
        minimumIdle = 0
        maximumPoolSize = 30
        jdbcUrl = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1"
    }
    VokOrm.init()
    db {
        con.createQuery(
            """create table if not exists Test (
            id bigint primary key auto_increment,
            name varchar not null,
            age integer not null,
            dateOfBirth date,
            recordCreatedAt timestamp
             )"""
        ).executeUpdate()
    }
    
    // runs SELECT * FROM Person
    // prints []
    println(Person.findAll())
    
    // runs INSERT INTO Person (name, age, recordCreatedAt) values (:p1, :p2, :p3)
    Person(name = "John", age = 42).save()
    
    // runs SELECT * FROM Person
    // prints [Person(id=1, name=John, age=42, dateOfBirth=null, recordCreatedAt=2011-12-03T10:15:30Z)]
    println(Person.findAll())
    
    // runs SELECT * FROM Person where id=:id
    // prints John
    println(Person.getById(1L).name)
    
    // mass-saves 11 persons in a single transaction.
    db { (0..10).forEach { Person(name = "person $it", age = it).save() } }
    
    VokOrm.destroy()
}
```

# Using Flyway to migrate the database

[Flyway](https://flywaydb.org/) is able to run DDL scripts on given database and track which scripts already ran.
This way, you can simply add more scripts and Flyway will apply them, to migrate the database to the newest version.
This even works in a cluster since Flyway will obtain a database lock, locking out other members of the cluster attempting
to upgrade.

Let's use the Category example from above. We need Flyway to run two scripts, to initialize the database:
one creates the table, while other creates the indices.

You don't need to use Flyway plugin. Just add the following Gradle dependency to your project:

```gradle
compile "org.flywaydb:flyway-core:5.2.0"
```

Flyway expects the migration scripts named in a certain format, to know the order in which to execute them.
Create the `db.migration` package in your `src/main/resources` and put two files there: the `V01__CreateCategory.sql`
file:
```sql92
create TABLE CATEGORY (
  id bigint auto_increment PRIMARY KEY,
  name varchar(200) NOT NULL
);
```
The next one will be `V02__CreateIndexCategoryName.sql`:
```sql92
create UNIQUE INDEX idx_category_name ON CATEGORY(name);
```

In order to run the migrations, just run the following after `VokOrm.init()`:
```kotlin
val flyway = Flyway()
flyway.dataSource = VokOrm.dataSource
flyway.migrate()
```

# Using with Spring or JavaEE

By default VoK-ORM connects to the JDBC database directly and uses its own instance of
Hikari-CP to pool JDBC connections. That of course doesn't work with containers such as Spring or
JavaEE which manage JDBC resources themselves.

It is possible to use VoK-ORM with Spring or JavaEE. First, you have to implement the
[DatabaseAccessor](src/main/kotlin/com/github/vokorm/DatabaseAccessor.kt) interface.
The interface is really simple: it's just a single method `runInTransaction()` which runs a block
in a transaction, committing on success, rolling back on failure. You typically implement this
interface simply by invoking a bean's method annotated with `@Transactional`, running the block inside
of that method. That way the container will handle the transactions.

Then, you just need to set the producer for your implementation into the
`VokOrm.databaseAccessorProvider` field and call `VokOrm.init()` and `VokOrm.destroy()` appropriately.
Or, if the accessor doesn't even need to be closed, you can simply set the `VokOrm.databaseAccessor` field
directly. Then you don't have to call `VokOrm.init()` nor `VokOrm.destroy()`.

# `vok-orm` design principles

`vok-orm` is a very simple object-relational mapping library, built around the following ideas:

* Simplicity is the most valued property; working with plain SQL commands is preferred over having a type-safe
  query language. If you want a type-safe database mapping library, try [Exposed](https://github.com/JetBrains/Exposed).
* The database is the source of truth. JVM objects are nothing more than DTOs,
  merely capture snapshots of the JDBC `ResultSet` rows. The entities are populated by the
  means of reflection: for every column in
  the JDBC `ResultSet` an appropriate setter is invoked, to populate the data.
* The entities are real POJOs: they do not track modifications, they do not automatically store modified
  values back into the database. They are not runtime-enhanced and can be final.
* A switch from one type of database to another never happens. We understand that the programmer
  wants to exploit the full potential of the database, by writing SQLs tailored for that particular database.
  `vok-orm` should not attempt to generate SELECTs on behalf of the programmer (except for the very basic ones related to CRUD);
  instead it should simply allow SELECTs to be passed as Strings, and then map the result
  to an object of programmer's choosing.

As such, `vok-orm` has much in common with the [ActiveJDBC](https://github.com/javalite/activejdbc) project, in terms
of design principles. The advantage of `vok-orm` is that it doesn't require any instrumentation to work
(instead it uses Kotlin language features), and it's even simpler than ActiveJDBC.

Please read [Back to Base - make SQL great again](http://mavi.logdown.com/posts/5771422)
for the complete explanation of ideas behind this framework.

This framework uses [Sql2o](https://www.sql2o.org/) to map data from the JDBC `ResultSet` to POJOs; in addition it provides a very simple
mechanism to store/update the data back to the database.

## Why not JPA

JPA is *the* default framework of choice for many projects. However, there are issues in JPA which cannot be overlooked:

* [Vaadin-on-Kotlin Issue #3 Remove JPA](https://github.com/mvysny/vaadin-on-kotlin/issues/3)
* [Back to Base - make SQL great again](http://mavi.logdown.com/posts/5771422)
* [Do-It-Yourself ORM as an Alternative to Hibernate](https://blog.philipphauer.de/do-it-yourself-orm-alternative-hibernate-drawbacks/)

JPA promises simplicity of usage by providing an object-oriented API. However, this is achieved by
creating a *virtual object database* layer over a relational database; that creates much complexity
under the hood which leaks in various ways.

We strive to erase the virtual object database layer. We acknowledge the existence of
the relational database; we only provide tools to ease the use of the database from a
statically-typed OOP language.

# License

Licensed under the [MIT License](https://opensource.org/licenses/MIT).

Copyright (c) 2017-2018 Martin Vysny

All rights reserved.

Permission is hereby granted, free  of charge, to any person obtaining
a  copy  of this  software  and  associated  documentation files  (the
"Software"), to  deal in  the Software without  restriction, including
without limitation  the rights to  use, copy, modify,  merge, publish,
distribute,  sublicense, and/or sell  copies of  the Software,  and to
permit persons to whom the Software  is furnished to do so, subject to
the following conditions:

The  above  copyright  notice  and  this permission  notice  shall  be
included in all copies or substantial portions of the Software.
THE  SOFTWARE IS  PROVIDED  "AS  IS", WITHOUT  WARRANTY  OF ANY  KIND,
EXPRESS OR  IMPLIED, INCLUDING  BUT NOT LIMITED  TO THE  WARRANTIES OF
MERCHANTABILITY,    FITNESS    FOR    A   PARTICULAR    PURPOSE    AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
OF CONTRACT, TORT OR OTHERWISE,  ARISING FROM, OUT OF OR IN CONNECTION
WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
