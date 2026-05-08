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
            DockerClient c,
            String name,
            String driver,
            Map<String, String> opts,
            Map<String, String> labels,
            Logger log) {
        boolean exists = c.listVolumesCmd().exec().getVolumes().stream().anyMatch(v -> name.equals(v.getName()));
        if (exists) {
            log.info("Volume {} already exists", name);
            return;
        }
        log.info("Creating volume {}", name);
        try (CreateVolumeCmd cmd = c.createVolumeCmd().withName(name).withDriver(driver)) {
            if (!opts.isEmpty()) cmd.withDriverOpts(opts);
            if (!labels.isEmpty()) cmd.withLabels(labels);
            cmd.exec();
            log.info("Created volume {}", name);
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
