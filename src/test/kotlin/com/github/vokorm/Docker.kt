package com.github.vokorm

import org.zeroturnaround.exec.ProcessExecutor
import org.zeroturnaround.exec.ProcessInitException

/**
 * Provides means to start/stop various databases in Docker.
 */
object Docker {
    /**
     * Checks whether the "docker" command-line tool is available.
     */
    val isPresent: Boolean by lazy {
        try {
            ProcessExecutor().command("docker", "version").execute().exitValue == 0
        } catch (e: ProcessInitException) {
            if (e.errorCode == 2) {
                // no such file or directory
                false
            } else {
                throw e
            }
        }
    }
}
