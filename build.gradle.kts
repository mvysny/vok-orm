import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.2.0"
    `maven-publish`
    signing
}

defaultTasks("clean", "build")

group = "com.github.mvysny.vokorm"
version = "3.3-SNAPSHOT"

repositories {
    mavenCentral()
}

tasks.withType<KotlinCompile> {
    compilerOptions.jvmTarget = JvmTarget.JVM_11
}

dependencies {
    api(libs.jdbiorm)

    // logging
    implementation(libs.slf4j.api)

    // validation support
    testImplementation(libs.bundles.hibernate.validator)

    // tests
    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.bundles.gson)
    testImplementation(libs.slf4j.simple)
    testImplementation(libs.h2)
    testImplementation(libs.hikaricp)

    testImplementation(libs.bundles.lucene) // for H2 Full-Text search
    testImplementation(libs.bundles.jdbc)
    testImplementation(libs.bundles.testcontainers)

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // IDEA language injections
    testImplementation(libs.jetbrains.annotations)
}

java {
    withJavadocJar()
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<Javadoc> {
    isFailOnError = false
}

publishing {
    repositories {
        maven {
            setUrl("https://oss.sonatype.org/service/local/staging/deploy/maven2/")
            credentials {
                username = project.properties["ossrhUsername"] as String? ?: "Unknown user"
                password = project.properties["ossrhPassword"] as String? ?: "Unknown user"
            }
        }
    }
    publications {
        create("mavenJava", MavenPublication::class.java).apply {
            groupId = project.group.toString()
            this.artifactId = "vok-orm"
            version = project.version.toString()
            pom {
                description = "A very simple persistence framework, built on top of jdbi"
                name = "VoK-ORM"
                url = "https://github.com/mvysny/vok-orm"
                licenses {
                    license {
                        name = "The MIT License"
                        url = "https://opensource.org/licenses/MIT"
                        distribution = "repo"
                    }
                }
                developers {
                    developer {
                        id = "mavi"
                        name = "Martin Vysny"
                        email = "martin@vysny.me"
                    }
                }
                scm {
                    url = "https://github.com/mvysny/vok-orm"
                }
            }
            from(components["java"])
        }
    }
}

signing {
    sign(publishing.publications["mavenJava"])
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        // to see the stacktraces of failed tests in the CI console.
        exceptionFormat = TestExceptionFormat.FULL
        showStandardStreams = true
    }
    systemProperty("h2only", System.getProperty("h2only"))
}

kotlin {
    explicitApi()
}

if (JavaVersion.current() > JavaVersion.VERSION_11 && gradle.startParameter.taskNames.contains("publish")) {
    throw GradleException("Release this library with JDK 11 or lower, to ensure JDK11 compatibility; current JDK is ${JavaVersion.current()}")
}

