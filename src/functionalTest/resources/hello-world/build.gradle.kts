plugins { id("io.github.pgatzka.docker") }

docker {
    containers {
        register("hello") {
            image.set("hello-world:latest")
            waitFor.set(io.github.pgatzka.docker.dsl.WaitFor.none())
        }
    }
}
