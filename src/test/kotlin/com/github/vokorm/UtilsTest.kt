package com.github.vokorm

import com.github.mvysny.dynatest.DynaTest
import kotlin.test.expect

class UtilsTest : DynaTest({
    test("implements") {
        expect(true) { Person::class.java.implements(KEntity::class.java) }
        expect(false) { String::class.java.implements(KEntity::class.java) }
    }
})
