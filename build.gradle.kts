plugins {
    id("java-gradle-plugin")
    id("com.gradle.plugin-publish") version "2.1.1"
    id("com.diffplug.spotless") version "8.4.0"
    id("jvm-test-suite")
}

group = "io.github.pgatzka"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    implementation("com.github.docker-java:docker-java:3.7.1")
    implementation("com.github.docker-java:docker-java-transport-httpclient5:3.7.1")
}

@Suppress("UnstableApiUsage")
testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter("5.10.2")
            dependencies {
                implementation("org.assertj:assertj-core:3.25.3")
                implementation("org.mockito:mockito-core:5.11.0")
                implementation("org.mockito:mockito-junit-jupiter:5.11.0")
                implementation(gradleTestKit())
            }
        }

        register<JvmTestSuite>("functionalTest") {
            useJUnitJupiter("5.10.2")
            dependencies {
                implementation(project())
                implementation("com.github.docker-java:docker-java:3.7.1")
                implementation("com.github.docker-java:docker-java-transport-httpclient5:3.7.1")
                implementation("org.assertj:assertj-core:3.25.3")
                implementation(gradleTestKit())
            }
            targets {
                all {
                    testTask.configure {
                        shouldRunAfter(test)
                    }
                }
            }
        }
    }
}

gradlePlugin {
    website.set("https://github.com/pgatzka/docker-gradle-plugin")
    vcsUrl.set("https://github.com/pgatzka/docker-gradle-plugin.git")
    plugins {
        create("dockerPlugin") {
            id = "io.github.pgatzka.docker"
            implementationClass = "io.github.pgatzka.docker.DockerPlugin"
            displayName = "Docker Gradle Plugin"
            description = "Declare Docker containers, named volumes, and named networks in your Gradle build."
            tags.set(listOf("docker", "containers", "integration-testing"))
        }
    }
    testSourceSets(sourceSets["functionalTest"])
}

tasks.named("check") {
    dependsOn(testing.suites.named("functionalTest"))
}

spotless {
    java {
        palantirJavaFormat()
    }
}