package io.github.pgatzka.docker.task.volume;

import com.github.dockerjava.api.DockerClient;
import io.github.pgatzka.docker.task.DockerTask;
import org.gradle.api.logging.Logger;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;

/**
 * Removes a Docker named volume by name.
 * <p>Idempotent: a no-op when the volume is absent. Marked {@link UntrackedTask} because the
 * daemon side effect must always run.
 */
@UntrackedTask(because = "Docker daemon side effects must always run")
public abstract class RemoveVolumeTask extends DockerTask {

    /** Invoked by Gradle's bytecode-decorated subclass; not for direct use. */
    public RemoveVolumeTask() {}

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

    /**
     * Mirrors {@code VolumeSpec.name}: the volume name to remove.
     *
     * @return the volume name property
     */
    @Input
    public abstract Property<String> getVolumeName();

    /**
     * Gradle entry point for this task. Delegates to the package-private
     * {@link #run(DockerClient, String, Logger)} helper.
     */
    @TaskAction
    public void execute() {
        run(getDockerService().get().getClient(), getVolumeName().get(), getLogger());
    }
}
