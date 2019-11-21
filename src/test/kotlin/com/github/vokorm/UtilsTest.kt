package com.github.vokorm

import com.github.mvysny.dynatest.DynaTest
import com.gitlab.mvysny.jdbiorm.Entity
import java.util.*
import kotlin.test.expect

class UtilsTest : DynaTest({
    test("implements") {
        expect(true) { Person::class.java.implements(Entity::class.java) }
        expect(false) { String::class.java.implements(Entity::class.java) }
    }
})
