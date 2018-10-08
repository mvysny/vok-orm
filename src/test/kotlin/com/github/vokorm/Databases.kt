package com.github.vokorm

import com.github.mvysny.dynatest.DynaNodeGroup
import org.hibernate.validator.constraints.Length
import java.time.Instant
import java.time.LocalDate
import java.util.*

@Table("Test")
data class Person(
    override var id: Long? = null,
    @field:Length(min = 1)
    var name: String,
    var age: Int,
    @Ignore var ignored: String? = null,
    @Transient var ignored2: Any? = null,
    var dateOfBirth: LocalDate? = null,
    var created: Date? = null,
    var modified: Instant? = null,
    // test of aliased field
    @As("alive")
    var isAlive25: Boolean? = null,
    var maritalStatus: MaritalStatus? = null

) : Entity<Long> {
    override fun save(validate: Boolean) {
        if (id == null) {
            if (created == null) created = java.sql.Timestamp(System.currentTimeMillis())
            if (modified == null) modified = Instant.now()
        }
        super.save(validate)
    }

    // should not be persisted into the database since it's not backed by a field.
    fun getSomeComputedValue(): Int = age + 2

    // should not be persisted into the database since it's not backed by a field.
    val someOtherComputedValue: Int get() = age

    companion object : Dao<Person> {
        val IGNORE_THIS_FIELD: Int = 0
    }
}

enum class MaritalStatus {
    Single,
    Married,
    Divorced,
    Widowed
}

val databasePort = 12345

private fun DynaNodeGroup.usingDockerizedPosgresql() {
    check(Docker.isPresent) { "Docker not available" }
    beforeGroup { Docker.startPostgresql(port = databasePort) }
    beforeGroup {
        VokOrm.dataSourceConfig.apply {
            minimumIdle = 0
            maximumPoolSize = 30
            jdbcUrl = "jdbc:postgresql://localhost:12345/postgres"
            username = "postgres"
            password = "mysecretpassword"
        }
        VokOrm.init()
        db {
            con.createQuery(
                """create table if not exists Test (
                id bigserial primary key,
                name varchar(400) not null,
                age integer not null,
                dateOfBirth date,
                created timestamp,
                modified timestamp,
                alive boolean,
                maritalStatus varchar(200)
                 )"""
            ).executeUpdate()
        }
    }

    afterGroup { VokOrm.destroy() }
    afterGroup { Docker.stopPostgresql() }

    fun clearDb() = Person.deleteAll()
    beforeEach { clearDb() }
    afterEach { clearDb() }
}

private fun DynaNodeGroup.usingDockerizedMysql() {
    check(Docker.isPresent) { "Docker not available" }
    beforeGroup { Docker.startMysql(port = databasePort) }
    beforeGroup {
        VokOrm.dataSourceConfig.apply {
            minimumIdle = 0
            maximumPoolSize = 30
            jdbcUrl = "jdbc:mysql://localhost:12345/db"
            username = "testuser"
            password = "mysqlpassword"
        }
        VokOrm.init()
        db {
            con.createQuery(
                """create table if not exists Test (
                id bigint primary key auto_increment,
                name varchar(400) not null,
                age integer not null,
                dateOfBirth date,
                created timestamp(3) NULL,
                modified timestamp(3) NULL,
                alive boolean,
                maritalStatus varchar(200)
                 )"""
            ).executeUpdate()
        }
    }

    afterGroup { VokOrm.destroy() }
    afterGroup { Docker.stopMysql() }

    fun clearDb() = Person.deleteAll()
    beforeEach { clearDb() }
    afterEach { clearDb() }
}

fun DynaNodeGroup.usingH2Database() {
    beforeGroup {
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
                created timestamp,
                modified timestamp,
                alive boolean,
                maritalStatus varchar
                 )"""
            ).executeUpdate()
        }
    }

    afterGroup { VokOrm.destroy() }

    fun clearDb() = Person.deleteAll()
    beforeEach { clearDb() }
    afterEach { clearDb() }
}

private fun DynaNodeGroup.usingDockerizedMariaDB() {
    check(Docker.isPresent) { "Docker not available" }
    beforeGroup { Docker.startMariaDB(port = databasePort) }
    beforeGroup {
        VokOrm.dataSourceConfig.apply {
            minimumIdle = 0
            maximumPoolSize = 30
            jdbcUrl = "jdbc:mariadb://localhost:12345/db"
            username = "testuser"
            password = "mysqlpassword"
        }
        VokOrm.init()
        db {
            con.createQuery(
                """create table if not exists Test (
                id bigint primary key auto_increment,
                name varchar(400) not null,
                age integer not null,
                dateOfBirth date,
                created timestamp(3) NULL,
                modified timestamp(3) NULL,
                alive boolean,
                maritalStatus varchar(200)
                 )"""
            ).executeUpdate()
        }
    }

    afterGroup { VokOrm.destroy() }
    afterGroup { Docker.stopMariaDB() }

    fun clearDb() = Person.deleteAll()
    beforeEach { clearDb() }
    afterEach { clearDb() }
}

fun DynaNodeGroup.withAllDatabases(block: DynaNodeGroup.()->Unit) {
    group("H2") {
        usingH2Database()
        block()
    }

    if (Docker.isPresent) {
        group("PostgreSQL 10.3") {
            usingDockerizedPosgresql()
            block()
        }

        group("MySQL 5.7.21") {
            usingDockerizedMysql()
            block()
        }

        group("MariaDB 10.1.31") {
            usingDockerizedMariaDB()
            block()
        }
    }
}
