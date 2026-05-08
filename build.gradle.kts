plugins {
    id("java-gradle-plugin")
    id("com.gradle.plugin-publish") version "2.1.1"
}

repositories {
    mavenCentral()
}


gradlePlugin {
    website.set("https://github.com/pgatzka/docker-gradle-plugin")
    vcsUrl.set("https://github.com/pgatzka/docker-gradle-plugin.git")
    plugins {
        create("dockerPlugin") {
            id = "io.github.pgatzka.docker"
            implementationClass = "io.github.pgatzka.docker.DockerPlugin"
        }
    }
}
