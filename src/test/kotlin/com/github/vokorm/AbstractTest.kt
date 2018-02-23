package com.github.vokorm

import com.github.mvysny.dynatest.DynaNodeGroup
import java.time.Instant
import java.time.LocalDate
import java.util.*

fun DynaNodeGroup.usingDatabase() {
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
