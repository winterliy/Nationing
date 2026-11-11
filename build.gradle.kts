plugins {
    kotlin("jvm") version "2.2.21"
    `java-library`
}

group = "winterliy.nationing"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

java {
    withSourcesJar()
}

tasks.test {
    useJUnitPlatform()
}
