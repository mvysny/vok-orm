package com.github.vokorm

import com.github.mvysny.dynatest.DynaTest
import kotlin.test.expect

class ConditionBuilderTest : DynaTest({
    test("smoke API tests") {
        buildCondition { Person::name eq "foo" }
        buildCondition { !(Person::name eq "foo") }
        buildCondition { (Person::name eq "foo") and (Person::id gt 25)}
        buildCondition { (Person::name eq "foo") or (Person::id gt 25)}
    }
    test("produced condition") {
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
})
