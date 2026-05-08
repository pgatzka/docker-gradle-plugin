plugins {
    id("io.freefair.lombok") version "9.5.0"
    id("java-gradle-plugin")
    id("com.gradle.plugin-publish") version "2.1.1"
    id("com.diffplug.spotless") version "8.4.0"
    id("org.sonarqube") version "7.3.0.8198"
    id("jvm-test-suite")
    id("jacoco")
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
    implementation(libs.docker.java)
    implementation(libs.docker.java.transport)
}

spotless {
    java {
        palantirJavaFormat()
    }
}

jacoco {
    toolVersion = "0.8.12"
}

sonar {
    properties {
        property("sonar.projectKey", "io.github.pgatzka.docker:docker-gradle-plugin")
        property("sonar.organization", "pgatzka")
    }
}

@Suppress("UnstableApiUsage") testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter("5.10.2")
            dependencies {
                implementation(libs.assertj)
                implementation(libs.mockito)
                implementation(libs.mockito.jupiter)
                implementation(gradleTestKit())
            }
        }

        register<JvmTestSuite>("functionalTest") {
            useJUnitJupiter("5.10.2")
            dependencies {
                implementation(project())
                implementation(libs.docker.java)
                implementation(libs.docker.java.transport)
                implementation(libs.assertj)
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

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/pgatzka/docker-gradle-plugin")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

tasks {
    jacocoTestCoverageVerification {
        dependsOn(jacocoTestReport)
        violationRules {
            rule {
                limit {
                    minimum = BigDecimal.valueOf(0.8)
                }
            }
        }
    }
    jacocoTestReport {
        dependsOn(test)
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
        mustRunAfter(test)
    }
    named<org.sonarqube.gradle.SonarTask>("sonar") {
        dependsOn(jacocoTestReport)
    }
    @Suppress("UnstableApiUsage") check {
        dependsOn(testing.suites.named("functionalTest"))
    }
    test {
        finalizedBy(jacocoTestReport)
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
            tags.set(listOf("docker", "containers", "integration-testing", "codegen", "code-generation"))
        }
    }
    testSourceSets(sourceSets["functionalTest"])
}