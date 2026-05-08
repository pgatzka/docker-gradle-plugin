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

/**
 * Removes a Docker network by name.
 * <p>Idempotent: a no-op when the network is absent. Marked {@link UntrackedTask} because the
 * daemon side effect must always run.
 */
@UntrackedTask(because = "Docker daemon side effects must always run")
public abstract class RemoveNetworkTask extends DockerTask {

    /** Invoked by Gradle's bytecode-decorated subclass; not for direct use. */
    public RemoveNetworkTask() {}

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

    /**
     * Mirrors {@code NetworkSpec.name}: the network name to remove.
     *
     * @return the network name property
     */
    @Input
    public abstract Property<String> getNetworkName();

    /**
     * Gradle entry point for this task. Delegates to the package-private
     * {@link #run(DockerClient, String, Logger)} helper.
     */
    @TaskAction
    public void execute() {
        run(getDockerService().get().getClient(), getNetworkName().get(), getLogger());
    }
}
