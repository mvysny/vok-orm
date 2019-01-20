package com.github.vokorm

import com.github.mvysny.dynatest.DynaTest
import java.util.*
import kotlin.test.expect

class UtilsTest : DynaTest({
    test("UUID-to-ByteArray") {
        val uuid = UUID.randomUUID()
        expect(uuid) { uuidFromByteArray(uuid.toByteArray()) }
    }

    test("implements") {
        expect(true) { Person::class.java.implements(Entity::class.java) }
        expect(false) { String::class.java.implements(Entity::class.java) }
    }
})
