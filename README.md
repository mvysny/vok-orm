[![Build Status](https://travis-ci.org/mvysny/vok-orm.svg?branch=master)](https://travis-ci.org/mvysny/vok-orm)

# Vaadin-On-Kotlin database mapping library

A very simple object-relational mapping library. Built around the following ideas:

* The database is the source of truth; JVM objects are nothing more but helpers to help working with the data in the JVM, in a type-safe way.
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

todo
