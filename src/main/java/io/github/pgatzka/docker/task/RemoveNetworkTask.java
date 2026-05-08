package io.github.pgatzka.docker.task;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Network;
import java.util.Optional;
import org.gradle.api.logging.Logger;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(because = "Docker daemon side effects must always run")
public abstract class RemoveNetworkTask extends DockerTask {

    static void run(DockerClient c, String name, Logger log) {
        Optional<Network> existing = c.listNetworksCmd().exec().stream()
                .filter(n -> name.equals(n.getName()))
                .findFirst();
        if (existing.isEmpty()) {
            log.info("Network {} not present; nothing to remove", name);
            return;
        }
        log.info("Removing network {}", name);
        c.removeNetworkCmd(existing.get().getId()).exec();
        log.info("Removed network {}", name);
    }

    @Input
    public abstract Property<String> getNetworkName();

    @TaskAction
    public void execute() {
        run(getDockerService().get().getClient(), getNetworkName().get(), getLogger());
    }
}
