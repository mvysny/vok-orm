package com.github.vokorm

import com.github.mvysny.dynatest.DynaNodeGroup
import org.zeroturnaround.exec.ProcessExecutor
import org.zeroturnaround.exec.ProcessInitException
import java.sql.DriverManager
import java.sql.SQLException

val isDockerPresent: Boolean get() {
    try {
        val isPresent = ProcessExecutor().command("docker", "version").execute().exitValue == 0
        println("Docker is available: $isPresent")
        return isPresent
    } catch (e: ProcessInitException) {
        if (e.errorCode == 2) return false // no such file or directory
        throw e
    }
}

fun exec(command: String) {
    val commands = command.split(' ')
    val result = ProcessExecutor().command(commands).redirectOutput(System.out).execute()
    check(result.exitValue == 0) { "Process failed with ${result.exitValue}" }
}

object Docker {
    fun startPostgresql() {
        exec("docker run --rm --name testing_container -e POSTGRES_PASSWORD=mysecretpassword -p 5432:5432 -d postgres:10.3")
        probeJDBC("jdbc:postgresql://localhost:5432/postgres", "postgres", "mysecretpassword")
    }

    private fun probeJDBC(url: String, username: String, password: String) {
        var lastException: SQLException? = null
        repeat(30) {
            try {
                DriverManager.getConnection(url, username, password).close()
                return
            } catch (e: SQLException) {
                lastException = e
                Thread.sleep(300)
            }
        }
        throw lastException!!
    }

    fun stopPostgresql() {
        exec("docker stop testing_container")
    }
}

fun DynaNodeGroup.usingDockerizedPosgresql() {
    check(isDockerPresent) { "Docker not available" }
    beforeGroup { Docker.startPostgresql() }
    beforeGroup {
        VokOrm.dataSourceConfig.apply {
            minimumIdle = 0
            maximumPoolSize = 30
            jdbcUrl = "jdbc:postgresql://localhost:5432/postgres"
            username = "postgres"
            password = "mysecretpassword"
        }
        VokOrm.init()
        db {
            con.createQuery(
                """create table if not exists Test (
                id bigserial primary key,
                name varchar(400) not null,
                age integer not null,
                dateOfBirth date,
                created timestamp,
                modified timestamp,
                alive boolean,
                maritalStatus varchar(200)
                 )"""
            ).executeUpdate()
        }
    }

    afterGroup { VokOrm.destroy() }
    afterGroup { Docker.stopPostgresql() }

    fun clearDb() = Person.deleteAll()
    beforeEach { clearDb() }
    afterEach { clearDb() }
}
