package io.github.pgatzka.docker.task;

import io.github.pgatzka.docker.service.DockerService;
import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Property;
import org.gradle.api.services.ServiceReference;
import org.gradle.api.tasks.UntrackedTask;

/**
 * Base class for all Docker-related Gradle tasks contributed by this plugin.
 * <p>Wires every concrete subclass to the shared {@link DockerService} build service and assigns
 * the {@code "docker"} task group. Marked {@link UntrackedTask} because Docker daemon side
 * effects must always run and the tasks have no inputs/outputs Gradle can fingerprint.
 */
@UntrackedTask(because = "Docker daemon side effects must always run; tasks have no inputs/outputs to track")
public abstract class DockerTask extends DefaultTask {

    /**
     * Creates a new Docker task and places it in the {@code "docker"} group so it shows up
     * grouped under that heading in {@code ./gradlew tasks}.
     */
    public DockerTask() {
        setGroup("docker");
    }

    /**
     * The shared {@link DockerService} build service that owns the {@code DockerClient}
     * connection. Injected by Gradle via {@link ServiceReference}.
     *
     * @return the Docker service property
     */
    @ServiceReference("io.github.pgatzka.docker.DockerService")
    public abstract Property<DockerService> getDockerService();
}
