package com.github.vokorm

import org.testcontainers.DockerClientFactory

@Synchronized
fun DockerClientFactory.isDockerAvailable(): Boolean {
    // todo Remove when https://github.com/testcontainers/testcontainers-java/issues/2110 is fixed.
    val m = DockerClientFactory::class.java.getDeclaredMethod("getOrInitializeStrategy")
    m.isAccessible = true
    return try {
        m.invoke(this)
        true
    } catch (ex: Exception) {
        false
    }
}
