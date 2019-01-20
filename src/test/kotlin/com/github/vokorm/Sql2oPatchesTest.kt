package com.github.vokorm

import com.github.mvysny.dynatest.DynaTest
import org.h2.tools.SimpleResultSet
import org.sql2o.reflection.PojoMetadata
import java.sql.Types
import java.util.*
import kotlin.test.expect

class Sql2oPatchesTest : DynaTest({
    // test for https://github.com/mvysny/vok-orm/issues/8
    val metadata = PojoMetadata(LogRecord::class.java, false, false, mapOf(), true)
    val isIdTypeMisdetected = metadata.getPropertySetter(LogRecord::class.java.entityMeta.idProperty.name).type == Object::class.java
    if (isIdTypeMisdetected) {
        test("mysql converter is applied to the UUID ID column") {
            val quirks = MysqlQuirks()
            quirks.converterOf(LogRecord::class.java)
            val uuid = UUID.randomUUID()
            val rs = SimpleResultSet().apply {
                addColumn("id", Types.BINARY, 0, 0)
                addColumn("text", Types.VARCHAR, 0, 0)
                addRow(uuid.toByteArray(), "foo")
                next()
            }
            expect(uuid) { quirks.getRSVal(rs, 1) }
        }
    }
})
