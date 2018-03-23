[![Build Status](https://travis-ci.org/mvysny/vok-orm.svg?branch=master)](https://travis-ci.org/mvysny/vok-orm)

# Vaadin-On-Kotlin database mapping library

`vok-orm` allows you to present the data from database rows as objects and embellish these data objects with business logic methods.

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

## `vok-orm` design principles

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

Please read [Back to Base - make SQL great again](http://mavi.logdown.com/posts/5771422)
for the complete explanation of ideas behind this framework.

This framework uses [Sql2o](https://www.sql2o.org/) to map data from the JDBC `ResultSet` to POJOs; in addition it provides a very simple
mechanism to store/update the data back to the database.


## Usage

Just add the following lines to your Gradle script, to include this library in your project:
```groovy
repositories {
    maven { url "https://dl.bintray.com/mvysny/github" }
}
dependencies {
    compile("com.github.vokorm:vok-orm:x.y")
}
```

> Note: obtain the newest version from the release name, and the tag name as well: [https://github.com/mvysny/vok-orm/releases](https://github.com/mvysny/vok-orm/releases)

## Usage examples

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
That is precisely how the `save()` function saves the bean - it simply calls the `db{}` function and executes
an appropriate INSERT/UPDATE statement.

The function will automatically roll back the transaction on any exception thrown out from the block (both checked and unchecked).

After you're done, call `VokOrm.destroy()` to close the pool.

> You can call methods of this library from anywhere. You don't need to be running inside of the JavaEE or Spring container -
you can use this library from a plain JavaSE main method.

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

In the spirit of type safety, the finder methods will only accept `Long` (or whatever is the type of
the primary key in the `Entity<x>` implementation clause). 

You can of course add your own custom finder methods into the Category companion object. For example:

```kotlin
data class Category(override var id: Long? = null, var name: String = "") : Entity<Long> {
    companion object : Dao<Category> {
        fun findByName(name: String): Category? = findBy(1) { Category::name eq name } .firstOrNull()
        fun getByName(name: String): Category = findByName(name) ?: throw IllegalArgumentException("No category named $name")
        fun existsWithName(name: String): Boolean = findByName(name) != null
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
            val scalar: Any? = con.createQuery("select sum(r.count) from Review r where r.category = :catId")
                    .addParameter("catId", categoryId)
                    .executeScalar()
            (scalar as Number?)?.toLong() ?: 0L
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
    var categoryName: String? = null
}
```

Now we can add a new finder function into `Review`'s companion object:

```kotlin
companion object : Dao<Review> {
    ...
    fun findReviews(): List<ReviewWithCategory> = db {
        con.createQuery("""select r.*, IFNULL(c.name, 'Undefined') as categoryName
            FROM Review r left join Category c on r.category = c.id
            ORDER BY r.name""")
                .executeAndFetch(ReviewWithCategory::class.java)
    }
}
```

We can take Sql2o's mapping capabilities to full use: we can craft any SELECT we want,
and then we can create a holder class that will not be an entity itself, but will merely hold the result of that SELECT.
The only thing that matters is that the class will have properties named exactly as the fields in the SELECT statement:

```kotlin
data class Beverage(var name: String = "", var category: String? = null) : Serializable {
    companion object {
        fun findAll(): List<Beverage> = db {
            con.createQuery("select r.beverageName as name, c.name as category from Review r left join Category c on r.category = c.id")
                .executeAndFetch(Beverage::class.java)
        }
    }
}
```

We just have to make sure that all of the `Beverage`'s fields are pre-initialized, so that the `Beverage` class has a zero-arg constructor.

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
        username = "sa"
        password = ""
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
