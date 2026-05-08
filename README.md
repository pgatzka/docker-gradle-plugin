# docker-gradle-plugin

Declare Docker containers, named volumes, and named networks in your Gradle build.

## Apply

```kotlin
plugins {
    id("io.github.pgatzka.docker") version "1.0.0"
}
```

## Declare resources

```kotlin
docker {
    volumes  { register("pgdata") {} }
    networks { register("backend") {} }
    containers {
        register("postgres") {
            image.set("postgres:16-alpine")
            environment.set(mapOf("POSTGRES_PASSWORD" to "x"))
            ports.set(mapOf(5432 to 5432))
            networks.set(listOf("backend"))
            mounts {
                volume("pgdata", "/var/lib/postgresql/data")
                bind("./sql", "/docker-entrypoint-initdb.d", readOnly = true)
            }
            waitFor.set(io.github.pgatzka.docker.dsl.WaitFor.tcpPort(5432))
        }
    }
}
```

## Generated tasks (group `docker`)

| Resource | Tasks |
|---|---|
| `containers.register("postgres")` | `startPostgres`, `stopPostgres`, `removePostgres` |
| `volumes.register("pgdata")`      | `createPgdata`, `removePgdata` |
| `networks.register("backend")`    | `createBackend`, `removeBackend` |

`startPostgres` automatically `dependsOn` `createPgdata` and `createBackend`.

## Wiring tasks

```kotlin
tasks.named("flywayMigrate") {
    dependsOn("startPostgres")
    finalizedBy("stopPostgres")
}
```

## Readiness strategies

- `WaitFor.healthcheck()` — default; image must declare `HEALTHCHECK`. Fails fast if not.
- `WaitFor.logLine(regex)` — match a log line.
- `WaitFor.tcpPort(port)` — TCP probe (port is the *container* port; the host port is derived from `ports`).
- `WaitFor.none()` — no wait.

## Notes

- Container/volume/network names are used verbatim on the daemon. Two Gradle projects on one machine declaring the same name will collide; use distinct names or override `containerName`.
- All output is at `info`/`debug` level. Run with `--info` to see phase transitions.
- Daemon connection is read from environment (`DOCKER_HOST`, `DOCKER_TLS_VERIFY`, `DOCKER_CERT_PATH`).
