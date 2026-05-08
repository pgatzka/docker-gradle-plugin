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

    @Input
    public abstract Property<String> getVolumeName();

    @Input
    public abstract Property<String> getDriver();

    @Input
    public abstract MapProperty<String, String> getDriverOpts();

    @Input
    public abstract MapProperty<String, String> getLabels();

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
