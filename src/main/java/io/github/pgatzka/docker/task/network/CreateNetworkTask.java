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

/**
 * Creates a Docker network according to its {@link io.github.pgatzka.docker.dsl.spec.NetworkSpec}.
 * <p>Idempotent: a no-op when a network with the same name already exists (driver, internal,
 * attachable, and labels are not reconciled). Marked {@link UntrackedTask} because the daemon
 * side effect must always run.
 */
@UntrackedTask(because = "Docker daemon side effects must always run")
public abstract class CreateNetworkTask extends DockerTask {

    /** Invoked by Gradle's bytecode-decorated subclass; not for direct use. */
    public CreateNetworkTask() {}

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

    /**
     * Mirrors {@code NetworkSpec.name}: the network name registered on the daemon.
     *
     * @return the network name property
     */
    @Input
    public abstract Property<String> getNetworkName();

    /**
     * Mirrors {@code NetworkSpec.driver}: the network driver (e.g. {@code bridge}).
     *
     * @return the network driver property
     */
    @Input
    public abstract Property<String> getDriver();

    /**
     * Mirrors {@code NetworkSpec.labels}: labels applied to the network on the daemon.
     *
     * @return the labels property
     */
    @Input
    public abstract MapProperty<String, String> getLabels();

    /**
     * Mirrors {@code NetworkSpec.internal}: when {@code true}, the network is isolated from
     * external networks.
     *
     * @return the internal flag property
     */
    @Input
    public abstract Property<Boolean> getInternal();

    /**
     * Mirrors {@code NetworkSpec.attachable}: when {@code true}, standalone containers may
     * attach to this network.
     *
     * @return the attachable flag property
     */
    @Input
    public abstract Property<Boolean> getAttachable();

    /**
     * Gradle entry point for this task. Delegates to the package-private
     * {@link #run(DockerClient, String, String, Map, boolean, boolean, Logger)} helper.
     */
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
