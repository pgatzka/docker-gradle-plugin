package io.github.pgatzka.docker.task.container;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import io.github.pgatzka.docker.dsl.PullPolicy;
import io.github.pgatzka.docker.dsl.mount.BindMount;
import io.github.pgatzka.docker.dsl.mount.VolumeMount;
import io.github.pgatzka.docker.dsl.waitable.*;
import io.github.pgatzka.docker.internal.Readiness;
import io.github.pgatzka.docker.task.DockerTask;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;

/**
 * Starts a Docker container according to its {@link io.github.pgatzka.docker.dsl.spec.ContainerSpec}.
 * Pulls the image (subject to the configured {@link PullPolicy}), creates the container if it
 * does not yet exist, starts it, and blocks until the configured {@link Waitable} readiness
 * strategy is satisfied.
 * <p>Idempotent against re-runs: an existing running container is left alone, and a stopped
 * container with the same name is reused rather than recreated. Marked {@link UntrackedTask}
 * because the daemon side effect must always run.
 */
@UntrackedTask(because = "Docker daemon side effects must always run")
public abstract class StartContainerTask extends DockerTask {

    private static final Duration PULL_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration READINESS_POLL_INTERVAL = Duration.ofMillis(500);
    private static final String LOOPBACK = "127.0.0.1";

    /** Invoked by Gradle's bytecode-decorated subclass; not for direct use. */
    public StartContainerTask() {}

    static void run(DockerClient client, StartContainerTaskParams params, Logger log) {
        pullIfNeeded(client, params.image(), params.pullPolicy(), log);
        validateImageHealthcheckIfRequired(client, params);

        EnsureResult ensured = ensureContainerExists(client, params, log);
        startContainer(client, params, ensured, log);
        waitForReadiness(client, params, log);
    }

    private static void pullIfNeeded(DockerClient client, String image, PullPolicy policy, Logger log) {
        boolean imagePresent = isImagePresent(client, image);
        if (!shouldPull(policy, imagePresent)) {
            log.info("Skipping pull for {} (policy={}, present={})", image, policy, imagePresent);
            return;
        }
        log.info("Pulling {} (policy={})", image, policy);
        try (PullImageResultCallback pullStream = client.pullImageCmd(image).exec(new PullImageResultCallback())) {
            boolean completed = pullStream.awaitCompletion(PULL_TIMEOUT.toMinutes(), TimeUnit.MINUTES);
            if (!completed) {
                throw new GradleException("Pull of " + image + " did not complete within " + PULL_TIMEOUT + ".");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new GradleException("Interrupted while pulling " + image, interrupted);
        } catch (IOException ioFailure) {
            throw new GradleException(
                    "Failed to close pull stream for " + image + ": " + ioFailure.getMessage(), ioFailure);
        }
        log.info("Pull complete: {}", image);
    }

    private static boolean isImagePresent(DockerClient client, String image) {
        return client.listImagesCmd().exec().stream()
                .flatMap(i -> i.getRepoTags() == null ? Stream.empty() : Stream.of(i.getRepoTags()))
                .anyMatch(image::equals);
    }

    private static boolean shouldPull(PullPolicy policy, boolean imagePresent) {
        return switch (policy) {
            case ALWAYS -> true;
            case IF_NOT_PRESENT -> !imagePresent;
            case NEVER -> false;
        };
    }

    private static void validateImageHealthcheckIfRequired(DockerClient client, StartContainerTaskParams params) {
        if (!(params.waitable() instanceof Healthcheck)) {
            return;
        }
        // Inspecting the image is only needed when the consumer opted into healthcheck readiness.
        InspectImageResponse imageInspect;
        try {
            imageInspect = client.inspectImageCmd(params.image()).exec();
        } catch (NotFoundException notFound) {
            throw new GradleException(
                    "Image " + params.image() + " is not present locally and pullPolicy=" + params.pullPolicy()
                            + " did not pull it.",
                    notFound);
        }
        if (imageDeclaresNoHealthcheck(imageInspect)) {
            throw new GradleException("Container " + params.containerName() + " uses waitFor=healthcheck() but image "
                    + params.image() + " declares no HEALTHCHECK. Configure waitFor in the container "
                    + "spec (logLine, tcpPort, or none).");
        }
    }

    private static boolean imageDeclaresNoHealthcheck(InspectImageResponse imageInspect) {
        ContainerConfig imageConfig = imageInspect.getConfig();
        HealthCheck healthCheck = imageConfig == null ? null : imageConfig.getHealthcheck();
        if (healthCheck == null) {
            return true;
        }
        // Docker represents an explicitly-disabled healthcheck as `Test = ["NONE"]`.
        List<String> test = healthCheck.getTest();
        return test != null && !test.isEmpty() && "NONE".equals(test.get(0));
    }

    private static EnsureResult ensureContainerExists(
            DockerClient client, StartContainerTaskParams params, Logger log) {
        Optional<String> existingId = findExistingContainerId(client, params.containerName());
        if (existingId.isPresent()) {
            log.info("Container {} already exists ({})", params.containerName(), existingId.get());
            return new EnsureResult(existingId.get(), false);
        }
        log.info("Creating container {}", params.containerName());
        String createdId = createContainer(client, params);
        attachAdditionalNetworks(client, createdId, params.networks());
        log.info("Created container {} ({})", params.containerName(), createdId);
        return new EnsureResult(createdId, true);
    }

    private static Optional<String> findExistingContainerId(DockerClient client, String containerName) {
        try {
            InspectContainerResponse inspect =
                    client.inspectContainerCmd(containerName).exec();
            return Optional.of(inspect.getId());
        } catch (NotFoundException missing) {
            return Optional.empty();
        }
    }

    private static String createContainer(DockerClient client, StartContainerTaskParams params) {
        try (CreateContainerCmd createCmd =
                client.createContainerCmd(params.image()).withName(params.containerName())) {
            applyEnvironment(createCmd, params.env());
            applyCommand(createCmd, params.command());
            applyPorts(createCmd, params.ports());
            applyMounts(createCmd, params.volumeMounts(), params.bindMounts());
            applyPrimaryNetwork(createCmd, params.networks());
            return createCmd.exec().getId();
        }
    }

    private static void applyEnvironment(CreateContainerCmd createCmd, Map<String, String> env) {
        if (env.isEmpty()) {
            return;
        }
        createCmd.withEnv(env.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .toList());
    }

    private static void applyCommand(CreateContainerCmd createCmd, List<String> command) {
        if (!command.isEmpty()) {
            createCmd.withCmd(command);
        }
    }

    private static void applyPorts(CreateContainerCmd createCmd, Map<Integer, Integer> ports) {
        if (ports.isEmpty()) {
            return;
        }
        List<ExposedPort> exposed = new ArrayList<>();
        Ports bindings = new Ports();
        ports.forEach((hostPort, containerPort) -> {
            ExposedPort port = ExposedPort.tcp(containerPort);
            exposed.add(port);
            bindings.bind(port, Ports.Binding.bindPort(hostPort));
        });
        createCmd.withExposedPorts(exposed);
        hostConfig(createCmd).withPortBindings(bindings);
    }

    private static void applyMounts(
            CreateContainerCmd createCmd, List<VolumeMount> volumeMounts, List<BindMount> bindMounts) {
        List<Bind> binds = new ArrayList<>();
        for (BindMount bindMount : bindMounts) {
            binds.add(new Bind(
                    bindMount.hostPath(),
                    new Volume(bindMount.containerPath()),
                    bindMount.readOnly() ? AccessMode.ro : AccessMode.rw));
        }
        for (VolumeMount volumeMount : volumeMounts) {
            binds.add(new Bind(
                    volumeMount.volumeName(),
                    new Volume(volumeMount.containerPath()),
                    volumeMount.readOnly() ? AccessMode.ro : AccessMode.rw));
        }
        if (!binds.isEmpty()) {
            hostConfig(createCmd).withBinds(binds);
        }
    }

    private static void applyPrimaryNetwork(CreateContainerCmd createCmd, List<String> networks) {
        if (!networks.isEmpty()) {
            hostConfig(createCmd).withNetworkMode(networks.get(0));
        }
    }

    private static void attachAdditionalNetworks(DockerClient client, String containerId, List<String> networks) {
        // Networks beyond the first need a separate connect call after creation; the first one is
        // wired via hostConfig.withNetworkMode in applyPrimaryNetwork.
        for (int i = 1; i < networks.size(); i++) {
            client.connectToNetworkCmd()
                    .withContainerId(containerId)
                    .withNetworkId(networks.get(i))
                    .exec();
        }
    }

    private static HostConfig hostConfig(CreateContainerCmd createCmd) {
        HostConfig existing = createCmd.getHostConfig();
        if (existing != null) {
            return existing;
        }
        // docker-java initializes this in practice, but guard against null defensively.
        HostConfig fresh = HostConfig.newHostConfig();
        createCmd.withHostConfig(fresh);
        return fresh;
    }

    private static void startContainer(
            DockerClient client, StartContainerTaskParams params, EnsureResult ensured, Logger log) {
        if (!ensured.justCreated && isAlreadyRunningOrUnstartable(client, params, log)) {
            return;
        }
        log.info("Starting container {}", params.containerName());
        client.startContainerCmd(params.containerName()).exec();
        log.info("Container {} running", params.containerName());
    }

    /**
     * @return true when the container is already running (caller should skip start). Throws
     * {@link GradleException} when the container is in a state that requires explicit
     * removal before it can be started again.
     */
    private static boolean isAlreadyRunningOrUnstartable(
            DockerClient client, StartContainerTaskParams params, Logger log) {
        InspectContainerResponse inspect =
                client.inspectContainerCmd(params.containerName()).exec();
        var state = inspect.getState();
        if (state == null) {
            return false;
        }
        if (Boolean.TRUE.equals(state.getRunning())) {
            log.info("Container {} already running", params.containerName());
            return true;
        }
        String status = state.getStatus() == null ? "unknown" : state.getStatus();
        if ("paused".equals(status) || "dead".equals(status) || "removing".equals(status)) {
            throw new GradleException("Container " + params.containerName() + " is in state '" + status
                    + "'. Run remove" + capitalize(params.containerName()) + " first.");
        }
        return false;
    }

    private static void waitForReadiness(DockerClient client, StartContainerTaskParams params, Logger log) {
        log.info(
                "Waiting for readiness (strategy={}, timeout={})",
                params.waitable().getClass().getSimpleName(),
                params.waitTimeout());
        switch (params.waitable()) {
            case None ignored -> {
                /* no wait */
            }
            case Healthcheck ignored ->
                Readiness.healthcheck(client, params.containerName(), params.waitTimeout(), READINESS_POLL_INTERVAL);
            case TcpPort(int containerPort) -> waitForTcp(client, params, containerPort);
            case LogLine(String regex) ->
                Readiness.logLine(client, params.containerName(), regex, params.waitTimeout());
        }
        log.info("Ready");
    }

    private static void waitForTcp(DockerClient client, StartContainerTaskParams params, int containerPort) {
        Integer hostPort = findHostPortMappedTo(params.ports(), containerPort);
        if (hostPort == null) {
            throw new GradleException("Container " + params.containerName()
                    + " uses waitFor.tcpPort(" + containerPort + ") but no host port is mapped to container port "
                    + containerPort + "; add it to ports{} or use a port that appears as a value in the map.");
        }
        Readiness.tcpPort(daemonHost(), hostPort, params.waitTimeout(), READINESS_POLL_INTERVAL);
    }

    private static Integer findHostPortMappedTo(Map<Integer, Integer> ports, int containerPort) {
        return ports.entrySet().stream()
                .filter(entry -> entry.getValue() == containerPort)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    /**
     * Resolve the host where published container ports are reachable from the build machine.
     */
    private static String daemonHost() {
        try {
            URI dockerHostUri = DefaultDockerClientConfig.createDefaultConfigBuilder()
                    .build()
                    .getDockerHost();
            String scheme = dockerHostUri.getScheme();
            if (scheme != null && (scheme.startsWith("tcp") || scheme.startsWith("http"))) {
                String host = dockerHostUri.getHost();
                if (host != null && !host.isBlank() && !"0.0.0.0".equals(host)) {
                    return host;
                }
            }
        } catch (RuntimeException ignored) {
            // fall through to localhost
        }
        return LOOPBACK;
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    /**
     * Mirrors {@code ContainerSpec.name}: the container name registered on the daemon.
     *
     * @return the container name property
     */
    @Input
    public abstract Property<String> getContainerName();

    /**
     * Mirrors {@code ContainerSpec.image}: the image reference (e.g. {@code postgres:16-alpine}).
     *
     * @return the image reference property
     */
    @Input
    public abstract Property<String> getImage();

    /**
     * Container environment values, mirroring {@code ContainerSpec.env}. Marked
     * {@code @Internal} on purpose: env values commonly carry secrets and we do not want them
     * fingerprinted into Gradle's task input snapshot or surfaced in build scans / cache
     * snapshots.
     *
     * @return the environment map property
     */
    @Internal
    public abstract MapProperty<String, String> getEnvironment();

    /**
     * Mirrors {@code ContainerSpec.ports}: host port to container port mappings.
     *
     * @return the port mappings property
     */
    @Input
    public abstract MapProperty<Integer, Integer> getPorts();

    /**
     * Mirrors {@code ContainerSpec.networks}: declared networks; the first becomes the
     * container's primary network and any remaining ones are attached after creation.
     *
     * @return the networks property
     */
    @Input
    public abstract ListProperty<String> getNetworks();

    /**
     * Mirrors {@code ContainerSpec.command}: override for the image's CMD, or empty to keep
     * the image default.
     *
     * @return the command override property
     */
    @Input
    public abstract ListProperty<String> getCommand();

    /**
     * Mirrors {@code ContainerSpec.volumeMounts}: named-volume mounts to attach.
     *
     * @return the volume mounts property
     */
    @Input
    public abstract ListProperty<VolumeMount> getVolumeMounts();

    /**
     * Mirrors {@code ContainerSpec.bindMounts}: host-path bind mounts to attach.
     *
     * @return the bind mounts property
     */
    @Input
    public abstract ListProperty<BindMount> getBindMounts();

    /**
     * Mirrors {@code ContainerSpec.waitFor}: the readiness strategy applied after start.
     *
     * @return the readiness strategy property
     */
    @Input
    public abstract Property<Waitable> getWaitFor();

    /**
     * Mirrors {@code ContainerSpec.waitTimeout}: maximum time to wait for readiness.
     *
     * @return the readiness timeout property
     */
    @Input
    public abstract Property<Duration> getWaitTimeout();

    /**
     * Mirrors {@code ContainerSpec.pullPolicy}: when (if ever) to pull the image before start.
     *
     * @return the pull policy property
     */
    @Input
    public abstract Property<PullPolicy> getPullPolicy();

    /**
     * Gradle entry point for this task. Delegates to the package-private
     * {@link #run(DockerClient, StartContainerTaskParams, Logger)} helper.
     */
    @TaskAction
    public void execute() {
        run(
                getDockerService().get().getClient(),
                new StartContainerTaskParams(
                        getContainerName().get(),
                        getImage().get(),
                        getEnvironment().get(),
                        getPorts().get(),
                        getNetworks().get(),
                        getCommand().get(),
                        getVolumeMounts().get(),
                        getBindMounts().get(),
                        getWaitFor().get(),
                        getWaitTimeout().get(),
                        getPullPolicy().get()),
                getLogger());
    }

    private record EnsureResult(String id, boolean justCreated) {}
}
