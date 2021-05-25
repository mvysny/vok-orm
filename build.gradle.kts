import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val slf4jVersion = "1.7.30"
val testcontainersVersion = "1.15.1"

plugins {
    kotlin("jvm") version "1.5.10"
    `maven-publish`
    signing
}

defaultTasks("clean", "build")

group = "com.github.mvysny.vokorm"
version = "1.5-SNAPSHOT"

repositories {
    mavenCentral()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

dependencies {
    api(kotlin("stdlib-jdk8"))
    api("com.github.mvysny.vokdataloader:vok-dataloader:0.8")
    api("com.gitlab.mvysny.jdbiorm:jdbi-orm:0.7")

    // logging
    implementation("org.slf4j:slf4j-api:$slf4jVersion")

    // validation support
    testImplementation("org.hibernate.validator:hibernate-validator:6.1.6.Final")
    // EL is required: http://hibernate.org/validator/documentation/getting-started/
    testImplementation("org.glassfish:javax.el:3.0.1-b08")

    // tests
    testImplementation("com.github.mvysny.dynatest:dynatest-engine:0.19")
    testImplementation("com.google.code.gson:gson:2.8.5")
    testImplementation("org.slf4j:slf4j-simple:$slf4jVersion")
    testImplementation("com.h2database:h2:1.4.200")
    testImplementation("com.zaxxer:HikariCP:4.0.3")

    testImplementation("org.apache.lucene:lucene-analyzers-common:5.5.5") // for H2 Full-Text search
    testImplementation("org.apache.lucene:lucene-queryparser:5.5.5") // for H2 Full-Text search

    testImplementation("org.postgresql:postgresql:42.2.5")
    testImplementation("mysql:mysql-connector-java:5.1.48")
    testImplementation("org.mariadb.jdbc:mariadb-java-client:2.4.0")
    testImplementation("com.microsoft.sqlserver:mssql-jdbc:8.4.1.jre8")

    testImplementation("org.testcontainers:testcontainers:$testcontainersVersion")
    testImplementation("org.testcontainers:postgresql:$testcontainersVersion")
    testImplementation("org.testcontainers:mysql:$testcontainersVersion")
    testImplementation("org.testcontainers:mariadb:$testcontainersVersion")
    testImplementation("org.testcontainers:mssqlserver:$testcontainersVersion")

    // IDEA language injections
    testImplementation("org.jetbrains:annotations:20.1.0")
}

java {
    withJavadocJar()
    withSourcesJar()
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
                description.set("A very simple persistence framework, built on top of jdbi")
                name.set("VoK-ORM")
                url.set("https://github.com/mvysny/vok-orm")
                licenses {
                    license {
                        name.set("The MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("mavi")
                        name.set("Martin Vysny")
                        email.set("martin@vysny.me")
                    }
                }
                scm {
                    url.set("https://github.com/mvysny/vok-orm")
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
        // to see the exceptions of failed tests in Travis-CI console.
        exceptionFormat = TestExceptionFormat.FULL
        showStandardStreams = true
    }
}

kotlin {
    explicitApi()
}

