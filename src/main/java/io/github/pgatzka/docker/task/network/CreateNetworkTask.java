package io.github.pgatzka.docker.task.network;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateNetworkCmd;
import io.github.pgatzka.docker.task.DockerTask;
import java.util.Map;
import org.gradle.api.logging.Logger;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;

@UntrackedTask(because = "Docker daemon side effects must always run")
public abstract class CreateNetworkTask extends DockerTask {

    static void run(
            DockerClient client,
            String networkName,
            String driver,
            Map<String, String> labels,
            boolean internal,
            boolean attachable,
            Logger log) {
        boolean exists =
                client.listNetworksCmd().exec().stream().anyMatch(network -> networkName.equals(network.getName()));
        if (exists) {
            log.info("Network {} already exists", networkName);
            return;
        }
        log.info("Creating network {}", networkName);
        try (CreateNetworkCmd createCmd = client.createNetworkCmd()
                .withName(networkName)
                .withDriver(driver)
                .withInternal(internal)
                .withAttachable(attachable)) {
            if (!labels.isEmpty()) createCmd.withLabels(labels);
            createCmd.exec();
            log.info("Created network {}", networkName);
        }
    }

    @Input
    public abstract Property<String> getNetworkName();

    @Input
    public abstract Property<String> getDriver();

    @Input
    public abstract MapProperty<String, String> getLabels();

    @Input
    public abstract Property<Boolean> getInternal();

    @Input
    public abstract Property<Boolean> getAttachable();

    @TaskAction
    public void execute() {
        run(
                getDockerService().get().getClient(),
                getNetworkName().get(),
                getDriver().get(),
                getLabels().get(),
                getInternal().get(),
                getAttachable().get(),
                getLogger());
    }
}
