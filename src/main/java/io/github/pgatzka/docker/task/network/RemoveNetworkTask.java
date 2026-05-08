package io.github.pgatzka.docker.task.network;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Network;
import io.github.pgatzka.docker.task.DockerTask;
import java.util.Optional;
import org.gradle.api.logging.Logger;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;

@UntrackedTask(because = "Docker daemon side effects must always run")
public abstract class RemoveNetworkTask extends DockerTask {

    static void run(DockerClient client, String networkName, Logger log) {
        Optional<Network> existing = client.listNetworksCmd().exec().stream()
                .filter(network -> networkName.equals(network.getName()))
                .findFirst();
        if (existing.isEmpty()) {
            log.info("Network {} not present; nothing to remove", networkName);
            return;
        }
        log.info("Removing network {}", networkName);
        client.removeNetworkCmd(existing.get().getId()).exec();
        log.info("Removed network {}", networkName);
    }

    @Input
    public abstract Property<String> getNetworkName();

    @TaskAction
    public void execute() {
        run(getDockerService().get().getClient(), getNetworkName().get(), getLogger());
    }
}
