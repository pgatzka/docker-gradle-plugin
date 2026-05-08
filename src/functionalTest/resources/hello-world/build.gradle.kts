plugins { id("io.github.pgatzka.docker") }

docker {
    containers {
        register("hello") {
            image.set("hello-world:latest")
            wait.set(io.github.pgatzka.docker.dsl.waitable.Waitable.none())
        }
    }
}
