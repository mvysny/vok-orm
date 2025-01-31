package com.github.vokorm

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.expect

class ConnectionUtilsTest : AbstractH2DatabaseTest() {
    @Nested inner class `one column` {
        @Test fun `empty dump`() {
            expect("ID\n--\n--0 row(s)\n") { db { handle.createQuery("select id from Test").dump() } }
        }
        @Test fun `two rows`() {
            Person(name = "Chuck", age = 25, dateOfBirth = LocalDate.of(2000, 1, 1)).save()
            Person(name = "Duke", age = 40, dateOfBirth = LocalDate.of(1999, 1, 1)).save()
            expect("NAME\n----\nChuck\nDuke\n----2 row(s)\n") { db { handle.createQuery("select name from Test").dump() } }
        }
    }

    @Nested inner class `multiple columns`() {
        @Test fun `empty dump`() {
            expect("""ID, NAME, AGE, DATEOFBIRTH, CREATED, MODIFIED, ALIVE, MARITALSTATUS
-------------------------------------------------------------------
-------------------------------------------------------------------0 row(s)
""") { db { handle.createQuery("select * from Test").dump() } }
        }
        @Test fun `two rows`() {
            Person(name = "Chuck", age = 25, dateOfBirth = LocalDate.of(2000, 1, 1)).save()
            Person(name = "Duke", age = 40, dateOfBirth = LocalDate.of(1999, 1, 1)).save()
            expect("""ID, NAME, AGE, DATEOFBIRTH, ALIVE, MARITALSTATUS
------------------------------------------------
1, Chuck, 25, 2000-01-01, null, null
2, Duke, 40, 1999-01-01, null, null
------------------------------------------------2 row(s)
""") { db { handle.createQuery("select id, name, age, dateofbirth, alive, maritalstatus from Test").dump() } }
        }
    }
}
