package io.github.pgatzka.docker.task;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import java.time.Duration;
import org.gradle.api.logging.Logger;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(because = "Docker daemon side effects must always run")
public abstract class StopContainerTask extends DockerTask {

    static void run(DockerClient c, String name, Duration timeout, Logger log) {
        InspectContainerResponse r;
        try {
            r = c.inspectContainerCmd(name).exec();
        } catch (NotFoundException nf) {
            log.info("Container {} not present; nothing to stop", name);
            return;
        }
        if (r.getState() == null || !Boolean.TRUE.equals(r.getState().getRunning())) {
            log.info("Container {} already stopped", name);
            return;
        }
        log.info("Stopping container {} (timeout={}s)", name, timeout.toSeconds());
        c.stopContainerCmd(name).withTimeout((int) timeout.toSeconds()).exec();
        log.info("Stopped container {}", name);
    }

    @Input
    public abstract Property<String> getContainerName();

    @Input
    public abstract Property<Duration> getStopTimeout();

    @TaskAction
    public void execute() {
        run(
                getDockerService().get().getClient(),
                getContainerName().get(),
                getStopTimeout().get(),
                getLogger());
    }
}
