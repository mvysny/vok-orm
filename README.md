[![Build Status](https://travis-ci.org/mvysny/vok-orm.svg?branch=master)](https://travis-ci.org/mvysny/vok-orm)

# Vaadin-On-Kotlin database mapping library

A very simple object-relational mapping library. Built around the following ideas:

* The database is the source of truth; JVM objects are nothing more but helpers to work with the data
* The objects only hold values and they are populated from JDBC result set.
* The objects do not track modifications and do not automatically store the values back into the database. The programmer needs to do that manually.

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
