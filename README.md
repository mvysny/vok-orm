[![Build Status](https://travis-ci.org/mvysny/vok-orm.svg?branch=master)](https://travis-ci.org/mvysny/vok-orm)

# Vaadin-On-Kotlin database mapping library

A very simple object-relational mapping library. Built around the following ideas:

* The database is the source of truth; JVM objects are nothing more but DTOs to help working with the data in the JVM, in a type-safe way.
* The entities are populated by the means of reflection. For every column in the result set a setter is invoked, to populate the data.
* The entities are POJOs: they do not track modifications and do not automatically store modified values back into the database. They are not runtime-enhanced and can be final.
* A switch from one type of database to another never happens. The programmer therefore wants to exploit the full potential of that database, by writing SQLs tailored for that particular database.
  `vok-orm` should not attempt to generate SELECTs on behalf of the programmer (except for the very basic ones related to CRUD);
  instead it should simply allow SELECTs to be passed as Strings, and then map the result simply to an object of programmer's choosing.
* The simplicity is preferred over anything else, even over type safety. If you want a type-safe database mapping library,
  try [Exposed](https://github.com/JetBrains/Exposed).

Please read [Do-It-Yourself ORM as an Alternative to Hibernate](https://blog.philipphauer.de/do-it-yourself-orm-alternative-hibernate-drawbacks/)
for the complete explanation of ideas behind this framework.

Uses [Sql2o](https://www.sql2o.org/) to map data from resultset to POJOs; uses a very simple built-in methods to store/update the data back to the database.

## Why not JPA

The reasons why we have decided not to use JPA are summed in these links:

* [Vaadin-on-Kotlin Issue #3 Remove JPA](https://github.com/mvysny/vaadin-on-kotlin/issues/3)
* [Back to Base - make SQL great again](http://mavi.logdown.com/posts/5771422)
* [Do-It-Yourself ORM as an Alternative to Hibernate](https://blog.philipphauer.de/do-it-yourself-orm-alternative-hibernate-drawbacks/)

JPA promises simplicity but delivers complexity under the hood which leaks with various ways. Therefore, we have decided to revisit the persistency layer from scratch.

## Usage examples

Say that we have a table containing a list of beverage categories, such as Cider or Beer. The H2 DDL for such table is simple:

```sql92
create TABLE CATEGORY (
  id bigint auto_increment PRIMARY KEY,
  name varchar(200) NOT NULL
);
create UNIQUE INDEX idx_category_name ON CATEGORY(name);
```

> **Note:** We expect that programmer knows the DDL language and wants to see the DDL scripts.
We will therefore not hide the DDL behind some type-safe generator API.

Such entity can be mapped to a data class as follows:
```kotlin
data class Category(override var id: Long? = null, var name: String = "") : Entity<Long>
```
(the id is nullable since it will be null until the category is actually created in the database).
By implementing the `Entity<Long>` interface, we are telling vok-orm that the primary key is of type `Long`; this will be important later on with the Dao.
The [Entity](src/main/kotlin/com/github/vokorm/Mapping.kt) interface brings in two useful methods:

* `save()` which either creates a new row by generating the INSERT statement (if the ID is null), or updates the row by generating the UPDATE statement (if the ID is not null)
* `delete()` which deletes that row from the database.

The INSERT statement is generated simply, by storing the values of all non-transient properties. See the Entity sources for more details.
You can annotate the `Category` class with the `@Table(dbname = "Categories")` annotation, to specify a different table name.

The category can now be created easily:

```kotlin
Category(name = "Beer").save()
```

But how do we specify the target database where to store the category in? 

### Connecting to a database

You need to specify the JDBC URL
to the `VokOrm.dataSourceConfig`. It's a [Hikari-CP](https://brettwooldridge.github.io/HikariCP/) configuration file.

> Hikari-CP is a JDBC connection pool which manages a pool of JDBC connections since they are expensive to create. Typically all projects
use some form of JDBC connection pooling, and `vok-orm` uses Hikari-CP.

Then you need to call `VokOrm.init()` so that the pool is created; now you simply can call the `db{}` function to run the
database transaction. The `db{}` function will poll this pool for a connection, starts a transaction and will allow you to execute commands such as:

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

After you're done, call `VokOrm.destroy()` to close the pool.

> You can use this library from anywhere - you don't need JavaEE or Spring app, you can call it from a plain JavaSE
main method.

### Finding Categories

todo
