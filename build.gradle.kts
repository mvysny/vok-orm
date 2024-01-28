import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val slf4jVersion = "2.0.9"
val testcontainersVersion = "1.19.4" // check latest version at https://repo1.maven.org/maven2/org/testcontainers/testcontainers/

plugins {
    kotlin("jvm") version "1.9.22"
    `maven-publish`
    signing
}

defaultTasks("clean", "build")

group = "com.github.mvysny.vokorm"
version = "3.2-SNAPSHOT"

repositories {
    mavenCentral()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}

dependencies {
    api(kotlin("stdlib-jdk8"))
    api("com.gitlab.mvysny.jdbiorm:jdbi-orm:2.7")

    // logging
    implementation("org.slf4j:slf4j-api:$slf4jVersion")

    // validation support
    testImplementation("org.hibernate.validator:hibernate-validator:8.0.1.Final") // check latest version at https://repo1.maven.org/maven2/org/hibernate/validator/hibernate-validator/
    // EL is required: http://hibernate.org/validator/documentation/getting-started/
    testImplementation("org.glassfish:jakarta.el:4.0.2")

    // tests
    testImplementation("com.github.mvysny.dynatest:dynatest:0.24")
    testImplementation("com.google.code.gson:gson:2.10.1")
    testImplementation("org.slf4j:slf4j-simple:$slf4jVersion")
    testImplementation("com.h2database:h2:2.2.224")
    testImplementation("com.zaxxer:HikariCP:5.0.1")

    testImplementation("org.apache.lucene:lucene-analyzers-common:8.11.1") // for H2 Full-Text search
    testImplementation("org.apache.lucene:lucene-queryparser:8.11.1") // for H2 Full-Text search

    testImplementation("org.postgresql:postgresql:42.5.1")
    testImplementation("mysql:mysql-connector-java:8.0.30")
    testImplementation("org.mariadb.jdbc:mariadb-java-client:3.0.6")
    testImplementation("com.microsoft.sqlserver:mssql-jdbc:11.2.1.jre8")

    testImplementation("org.testcontainers:testcontainers:$testcontainersVersion")
    testImplementation("org.testcontainers:postgresql:$testcontainersVersion")
    testImplementation("org.testcontainers:mysql:$testcontainersVersion")
    testImplementation("org.testcontainers:mariadb:$testcontainersVersion")
    testImplementation("org.testcontainers:mssqlserver:$testcontainersVersion")
    testImplementation("org.testcontainers:cockroachdb:$testcontainersVersion")

    // workaround for https://github.com/google/gson/issues/1059
    testImplementation("com.fatboyindustrial.gson-javatime-serialisers:gson-javatime-serialisers:1.1.1")

    // IDEA language injections
    testImplementation("org.jetbrains:annotations:24.0.1")
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

