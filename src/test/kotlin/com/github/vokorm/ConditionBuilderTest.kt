package com.github.vokorm

import org.junit.jupiter.api.Test
import kotlin.test.expect

class ConditionBuilderTest {
    @Test fun `smoke API tests`() {
        buildCondition { Person::name eq "foo" }
        buildCondition { !(Person::name eq "foo") }
        buildCondition { (Person::name eq "foo") and (Person::id gt 25)}
        buildCondition { (Person::name eq "foo") or (Person::id gt 25)}
    }
    @Test fun `produced condition`() {
        expect("Person.name = foo") {
            buildCondition { Person::name eq "foo" } .toString()
        }
        expect("NOT(Person.name = foo)") {
            buildCondition { !(Person::name eq "foo") } .toString()
        }
        expect("(Person.name = foo) AND (Person.id > 25)") {
            buildCondition { (Person::name eq "foo") and (Person::id gt 25) } .toString()
        }
        expect("(Person.name = foo) OR (Person.id > 25)") {
            buildCondition { (Person::name eq "foo") or (Person::id gt 25)} .toString()
        }
    }
}
