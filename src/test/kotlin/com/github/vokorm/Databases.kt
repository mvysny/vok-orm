package com.github.vokorm

import com.github.mvysny.dynatest.DynaNodeGroup
import java.time.Instant
import java.time.LocalDate
import java.util.*

@Table("Test")
data class Person(
    override var id: Long? = null,
    var name: String,
    var age: Int,
    @Ignore var ignored: String? = null,
    @Transient var ignored2: Any? = null,
    var dateOfBirth: LocalDate? = null,
    var created: Date? = null,
    var modified: Instant? = null,
    var alive: Boolean? = null,
    var maritalStatus: MaritalStatus? = null

) : Entity<Long> {
    override fun save() {
        if (id == null) {
            if (created == null) created = java.sql.Timestamp(System.currentTimeMillis())
            if (modified == null) modified = Instant.now()
        }
        super.save()
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

private fun DynaNodeGroup.usingDockerizedPosgresql() {
    check(isDockerPresent) { "Docker not available" }
    beforeGroup { Docker.startPostgresql() }
    beforeGroup {
        VokOrm.dataSourceConfig.apply {
            minimumIdle = 0
            maximumPoolSize = 30
            jdbcUrl = "jdbc:postgresql://localhost:5432/postgres"
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
    check(isDockerPresent) { "Docker not available" }
    beforeGroup { Docker.startMysql() }
    beforeGroup {
        VokOrm.dataSourceConfig.apply {
            minimumIdle = 0
            maximumPoolSize = 30
            jdbcUrl = "jdbc:mysql://localhost:3306/db"
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

private fun DynaNodeGroup.usingH2Database() {
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

fun DynaNodeGroup.withAllDatabases(block: DynaNodeGroup.()->Unit) {
    group("H2") {
        usingH2Database()
        block()
    }

    if (isDockerPresent) {
        group("PostgreSQL 10.3") {
            usingDockerizedPosgresql()
            block()
        }

        group("MySQL 5.7.21") {
            usingDockerizedMysql()
            block()
        }
    }
}
