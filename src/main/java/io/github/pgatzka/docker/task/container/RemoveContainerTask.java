package io.github.pgatzka.docker.task.container;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.NotFoundException;
import io.github.pgatzka.docker.task.DockerTask;
import org.gradle.api.logging.Logger;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;

/**
 * Force-removes a Docker container by name. Anonymous volumes attached to the container are
 * preserved; only the container itself is deleted.
 * <p>Idempotent: a no-op when the container is absent. Marked {@link UntrackedTask} because
 * the daemon side effect must always run.
 */
@UntrackedTask(because = "Docker daemon side effects must always run")
public abstract class RemoveContainerTask extends DockerTask {

    static void run(DockerClient client, String containerName, Logger log) {
        try {
            client.inspectContainerCmd(containerName).exec();
        } catch (NotFoundException notFound) {
            log.info("Container {} not present; nothing to remove", containerName);
            return;
        }
        log.info("Removing container {}", containerName);
        client.removeContainerCmd(containerName)
                .withForce(true)
                .withRemoveVolumes(false)
                .exec();
        log.info("Removed container {}", containerName);
    }

    /**
     * Mirrors {@code ContainerSpec.name}: the container name to remove.
     *
     * @return the container name property
     */
    @Input
    public abstract Property<String> getContainerName();

    /**
     * Gradle entry point for this task. Delegates to the package-private
     * {@link #run(DockerClient, String, Logger)} helper.
     */
    @TaskAction
    public void execute() {
        run(getDockerService().get().getClient(), getContainerName().get(), getLogger());
    }
}
