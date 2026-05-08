package io.github.pgatzka.docker.task.container;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.NotFoundException;
import io.github.pgatzka.docker.task.DockerTask;
import org.gradle.api.logging.Logger;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;

@UntrackedTask(because = "Docker daemon side effects must always run")
public abstract class RemoveContainerTask extends DockerTask {

    static void run(DockerClient c, String name, Logger log) {
        try {
            c.inspectContainerCmd(name).exec();
        } catch (NotFoundException nf) {
            log.info("Container {} not present; nothing to remove", name);
            return;
        }
        log.info("Removing container {}", name);
        c.removeContainerCmd(name).withForce(true).withRemoveVolumes(false).exec();
        log.info("Removed container {}", name);
    }

    @Input
    public abstract Property<String> getContainerName();

    @TaskAction
    public void execute() {
        run(getDockerService().get().getClient(), getContainerName().get(), getLogger());
    }
}
