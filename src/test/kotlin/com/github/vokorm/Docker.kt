package com.github.vokorm

import org.zeroturnaround.exec.ProcessExecutor
import org.zeroturnaround.exec.ProcessInitException

object Docker {
    /**
     * Remove when https://github.com/testcontainers/testcontainers-java/issues/2110 is fixed.
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
