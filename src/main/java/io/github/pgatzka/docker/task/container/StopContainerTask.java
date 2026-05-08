package io.github.pgatzka.docker.task.container;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.exception.NotModifiedException;
import io.github.pgatzka.docker.task.DockerTask;
import java.time.Duration;
import org.gradle.api.logging.Logger;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;

/**
 * Stops a running Docker container, waiting up to the configured timeout for graceful
 * shutdown before the daemon escalates to SIGKILL.
 * <p>Idempotent: a no-op when the container is absent or already stopped, and tolerates the
 * benign race where the container exits between the inspect and stop calls. Marked
 * {@link UntrackedTask} because the daemon side effect must always run.
 */
@UntrackedTask(because = "Docker daemon side effects must always run")
public abstract class StopContainerTask extends DockerTask {

    static void run(DockerClient client, String containerName, Duration timeout, Logger log) {
        InspectContainerResponse inspect;
        try {
            inspect = client.inspectContainerCmd(containerName).exec();
        } catch (NotFoundException notFound) {
            log.info("Container {} not present; nothing to stop", containerName);
            return;
        }
        if (inspect.getState() == null
                || !Boolean.TRUE.equals(inspect.getState().getRunning())) {
            log.info("Container {} already stopped", containerName);
            return;
        }
        long timeoutSecondsLong = timeout.toSeconds();
        int timeoutSeconds =
                timeoutSecondsLong > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(0, timeoutSecondsLong);
        log.info("Stopping container {} (timeout={}s)", containerName, timeoutSeconds);
        try {
            client.stopContainerCmd(containerName).withTimeout(timeoutSeconds).exec();
        } catch (NotModifiedException alreadyStopped) {
            // Container exited between our inspect and the stop call (HTTP 304). Benign.
            log.info("Container {} exited before stop completed", containerName);
            return;
        }
        log.info("Stopped container {}", containerName);
    }

    /**
     * Mirrors {@code ContainerSpec.name}: the container name on the daemon.
     *
     * @return the container name property
     */
    @Input
    public abstract Property<String> getContainerName();

    /**
     * Mirrors {@code ContainerSpec.stopTimeout}: the grace period before the daemon escalates
     * to SIGKILL.
     *
     * @return the stop timeout property
     */
    @Input
    public abstract Property<Duration> getStopTimeout();

    /**
     * Gradle entry point for this task. Delegates to the package-private
     * {@link #run(DockerClient, String, Duration, Logger)} helper.
     */
    @TaskAction
    public void execute() {
        run(
                getDockerService().get().getClient(),
                getContainerName().get(),
                getStopTimeout().get(),
                getLogger());
    }
}
