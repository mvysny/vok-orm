package com.github.vok.framework.sql2o

import org.slf4j.LoggerFactory
import java.io.Closeable

fun Closeable.closeQuietly() {
    try {
        close()
    } catch (e: Exception) {
        LoggerFactory.getLogger(javaClass).info("Failed to close $this", e)
    }
}
