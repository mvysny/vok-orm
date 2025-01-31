package com.github.vokorm

import com.gitlab.mvysny.jdbiorm.Entity
import jakarta.validation.ValidationException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.expect
import kotlin.test.fail

class ValidationTest : AbstractH2DatabaseTest() {
    // this is not really testing the database: we're testing Entity.validate().
    // Therefore, it's enough to run this battery on H2 only.
    @Test fun `Validation on empty name fails`() {
        assertThrows<ValidationException> {
            Person(name = "", age = 20).validate()
        }
        expect(false) { Person(name = "", age = 20).isValid }
    }
    @Test fun `Validation on non-empty name succeeds`() {
        Person(name = "Valid Name", age = 20).validate()
        expect(true) { Person(name = "Valid Name", age = 20).isValid }
    }
    @Test fun `save() fails when the bean is invalid`() {
        expectThrows<ValidationException>("name: length must be between 1 and 2147483647") {
            Person(name = "", age = 20).save()
        }
    }
    @Test fun `validation is skipped when save(false) is called`() {
        data class ValidationAlwaysFails(private var id: Long?) : Entity<Long> {
            override fun setId(id: Long?) { this.id = id }
            override fun getId(): Long? = id
            override fun validate() = fail("Shouldn't be called")
        }
        db { ddl("create table ValidationAlwaysFails ( id bigint primary key auto_increment )") }
        ValidationAlwaysFails(null).save(false)
    }
}
