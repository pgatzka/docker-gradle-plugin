package io.github.pgatzka.docker.task;

import com.github.dockerjava.api.DockerClient;
import org.gradle.api.logging.Logger;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(because = "Docker daemon side effects must always run")
public abstract class RemoveVolumeTask extends DockerTask {

    @Input public abstract Property<String> getVolumeName();

    @TaskAction
    public void execute() {
        run(getDockerService().get().getClient(), getVolumeName().get(), getLogger());
    }

    static void run(DockerClient c, String name, Logger log) {
        boolean exists = c.listVolumesCmd().exec().getVolumes().stream()
            .anyMatch(v -> name.equals(v.getName()));
        if (!exists) {
            log.info("Volume {} not present; nothing to remove", name);
            return;
        }
        log.info("Removing volume {}", name);
        c.removeVolumeCmd(name).exec();
        log.info("Removed volume {}", name);
    }
}
