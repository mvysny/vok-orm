package com.github.vokorm

import com.github.mvysny.dynatest.DynaTest
import java.util.*
import kotlin.test.expect

class UtilsTest : DynaTest({
    test("UUID-to-ByteArray") {
        val uuid = UUID.randomUUID()
        expect(uuid) { uuidFromByteArray(uuid.toByteArray()) }
    }
})
