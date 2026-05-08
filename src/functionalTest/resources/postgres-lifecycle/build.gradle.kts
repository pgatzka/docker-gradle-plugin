plugins { id("io.github.pgatzka.docker") }

docker {
    volumes { register("pg_data") {} }
    networks { register("pg_net") {} }
    containers {
        register("pg_test") {
            image.set("postgres:16-alpine")
            environment.set(mapOf("POSTGRES_PASSWORD" to "x"))
            ports.set(mapOf(54329 to 5432))
            networks.set(listOf("pg_net"))
            mounts {
                volume("pg_data", "/var/lib/postgresql/data")
            }
            waitFor.set(io.github.pgatzka.docker.dsl.WaitFor.tcpPort(5432))
            waitTimeout.set(java.time.Duration.ofSeconds(60))
        }
    }
}
