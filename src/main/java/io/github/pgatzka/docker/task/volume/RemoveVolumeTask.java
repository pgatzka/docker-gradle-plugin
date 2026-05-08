package io.github.pgatzka.docker.task.volume;

import com.github.dockerjava.api.DockerClient;
import io.github.pgatzka.docker.task.DockerTask;
import org.gradle.api.logging.Logger;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;

@UntrackedTask(because = "Docker daemon side effects must always run")
public abstract class RemoveVolumeTask extends DockerTask {

    static void run(DockerClient client, String volumeName, Logger log) {
        boolean exists = client.listVolumesCmd().exec().getVolumes().stream()
                .anyMatch(volume -> volumeName.equals(volume.getName()));
        if (!exists) {
            log.info("Volume {} not present; nothing to remove", volumeName);
            return;
        }
        log.info("Removing volume {}", volumeName);
        client.removeVolumeCmd(volumeName).exec();
        log.info("Removed volume {}", volumeName);
    }

    @Input
    public abstract Property<String> getVolumeName();

    @TaskAction
    public void execute() {
        run(getDockerService().get().getClient(), getVolumeName().get(), getLogger());
    }
}
