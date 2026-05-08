package io.github.pgatzka.docker.task.volume;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateVolumeCmd;
import io.github.pgatzka.docker.task.DockerTask;
import java.util.Map;
import org.gradle.api.logging.Logger;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;

/**
 * Creates a Docker named volume according to its {@link io.github.pgatzka.docker.dsl.VolumeSpec}.
 * <p>Idempotent: a no-op when a volume with the same name already exists (driver and options
 * are not reconciled). Marked {@link UntrackedTask} because the daemon side effect must always
 * run.
 */
@UntrackedTask(because = "Docker daemon side effects must always run")
public abstract class CreateVolumeTask extends DockerTask {

    static void run(
            DockerClient client,
            String volumeName,
            String driver,
            Map<String, String> driverOpts,
            Map<String, String> labels,
            Logger log) {
        boolean exists = client.listVolumesCmd().exec().getVolumes().stream()
                .anyMatch(volume -> volumeName.equals(volume.getName()));
        if (exists) {
            log.info("Volume {} already exists", volumeName);
            return;
        }
        log.info("Creating volume {}", volumeName);
        try (CreateVolumeCmd createCmd =
                client.createVolumeCmd().withName(volumeName).withDriver(driver)) {
            if (!driverOpts.isEmpty()) createCmd.withDriverOpts(driverOpts);
            if (!labels.isEmpty()) createCmd.withLabels(labels);
            createCmd.exec();
            log.info("Created volume {}", volumeName);
        }
    }

    /**
     * Mirrors {@code VolumeSpec.name}: the volume name registered on the daemon.
     *
     * @return the volume name property
     */
    @Input
    public abstract Property<String> getVolumeName();

    /**
     * Mirrors {@code VolumeSpec.driver}: the volume driver (e.g. {@code local}).
     *
     * @return the volume driver property
     */
    @Input
    public abstract Property<String> getDriver();

    /**
     * Mirrors {@code VolumeSpec.driverOpts}: driver-specific options forwarded to the daemon.
     *
     * @return the driver options property
     */
    @Input
    public abstract MapProperty<String, String> getDriverOpts();

    /**
     * Mirrors {@code VolumeSpec.labels}: labels applied to the volume on the daemon.
     *
     * @return the labels property
     */
    @Input
    public abstract MapProperty<String, String> getLabels();

    /**
     * Gradle entry point for this task. Delegates to the package-private
     * {@link #run(DockerClient, String, String, Map, Map, Logger)} helper.
     */
    @TaskAction
    public void execute() {
        run(
                getDockerService().get().getClient(),
                getVolumeName().get(),
                getDriver().get(),
                getDriverOpts().get(),
                getLabels().get(),
                getLogger());
    }
}
