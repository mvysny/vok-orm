package com.github.vokorm

import org.junit.jupiter.api.Test
import kotlin.test.expect

class UtilsTest {
    @Test fun implements() {
        expect(true) { Person::class.java.implements(KEntity::class.java) }
        expect(false) { String::class.java.implements(KEntity::class.java) }
    }
}
