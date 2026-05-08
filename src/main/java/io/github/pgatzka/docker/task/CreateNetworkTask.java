package io.github.pgatzka.docker.task;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateNetworkCmd;
import org.gradle.api.logging.Logger;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

import java.util.Map;

public abstract class CreateNetworkTask extends DockerTask {

    @Input public abstract Property<String> getNetworkName();
    @Input public abstract Property<String> getDriver();
    @Input public abstract MapProperty<String, String> getLabels();
    @Input public abstract Property<Boolean> getInternal();
    @Input public abstract Property<Boolean> getAttachable();

    @TaskAction
    public void execute() {
        run(getDockerService().get().getClient(),
            getNetworkName().get(), getDriver().get(), getLabels().get(),
            getInternal().get(), getAttachable().get(), getLogger());
    }

    static void run(DockerClient c, String name, String driver, Map<String, String> labels,
                    boolean internal, boolean attachable, Logger log) {
        boolean exists = c.listNetworksCmd().exec().stream()
            .anyMatch(n -> name.equals(n.getName()));
        if (exists) {
            log.info("Network {} already exists", name);
            return;
        }
        log.info("Creating network {}", name);
        CreateNetworkCmd cmd = c.createNetworkCmd()
            .withName(name).withDriver(driver)
            .withInternal(internal).withAttachable(attachable);
        if (!labels.isEmpty()) cmd.withLabels(labels);
        cmd.exec();
        log.info("Created network {}", name);
    }
}
