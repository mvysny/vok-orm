package com.github.vokorm

import org.zeroturnaround.exec.ProcessExecutor
import org.zeroturnaround.exec.ProcessInitException
import java.sql.DriverManager
import java.sql.SQLException

val isDockerPresent: Boolean get() {
    try {
        return ProcessExecutor().command("docker", "version").execute().exitValue == 0
    } catch (e: ProcessInitException) {
        if (e.errorCode == 2) return false // no such file or directory
        throw e
    }
}

fun exec(command: String) {
    val commands = command.split(' ')
    val result = ProcessExecutor().command(commands).readOutput(true).execute()
    check(result.exitValue == 0) { "${result.outputUTF8()}\nProcess failed with ${result.exitValue}" }
}

object Docker {
    fun startPostgresql() {
        exec("docker run --rm --name testing_container -e POSTGRES_PASSWORD=mysecretpassword -p 5432:5432 -d postgres:10.3")
        probeJDBC("jdbc:postgresql://localhost:5432/postgres", "postgres", "mysecretpassword")
    }

    private fun probeJDBC(url: String, username: String, password: String) {
        var lastException: SQLException? = null
        // mysql starts sloooooowly
        repeat(100) {
            try {
                DriverManager.getConnection(url, username, password).close()
                return
            } catch (e: SQLException) {
                lastException = e
                Thread.sleep(500)
            }
        }
        throw lastException!!
    }

    fun stopPostgresql() {
        exec("docker stop testing_container")
    }

    fun startMysql() {
        exec("docker run --rm --name testing_container -e MYSQL_ROOT_PASSWORD=mysqlpassword -e MYSQL_DATABASE=db -e MYSQL_USER=testuser -e MYSQL_PASSWORD=mysqlpassword -p 3306:3306 -d mysql:5.7.21")
        probeJDBC("jdbc:mysql://localhost:3306/db", "testuser", "mysqlpassword")
    }

    fun stopMysql() {
        exec("docker stop testing_container")
    }

    fun startMariaDB() {
        exec("docker run --rm --name testing_container -e MYSQL_ROOT_PASSWORD=mysqlpassword -e MYSQL_DATABASE=db -e MYSQL_USER=testuser -e MYSQL_PASSWORD=mysqlpassword -p 3306:3306 -d mariadb:10.1.31")
        probeJDBC("jdbc:mariadb://localhost:3306/db", "testuser", "mysqlpassword")
    }

    fun stopMariaDB() {
        exec("docker stop testing_container")
    }
}
