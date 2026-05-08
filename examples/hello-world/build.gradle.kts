plugins {
    id("io.github.pgatzka.docker") version "1.0.0-SNAPSHOT"
}

docker {
    volumes { register("codegen_data") {} }
    networks { register("codegen_network") {} }
    containers {
        register("postgres_codegen") {
            image.set("postgres:18-alpine")
            environment.set(
                mapOf(
                    "POSTGRES_DB" to "codegen",
                    "POSTGRES_USER" to "codegen",
                    "POSTGRES_PASSWORD" to "codegen"
                )
            )
            ports.set(mapOf(5432 to 5432))
            networks.set(listOf("codegen_network"))
            mounts {
                volume("codegen_data", "/var/lib/postgresql/data")
            }
            waitFor.set(io.github.pgatzka.docker.dsl.WaitFor.tcpPort(5432))
        }
    }
}

tasks {
    register("codegen"){
        dependsOn("startPostgresCodegen")
        finalizedBy("stopPostgresCodegen")
    }
}