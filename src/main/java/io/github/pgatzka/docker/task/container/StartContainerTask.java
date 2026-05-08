package io.github.pgatzka.docker.task.container;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.ContainerConfig;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HealthCheck;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import io.github.pgatzka.docker.dsl.Mounts;
import io.github.pgatzka.docker.dsl.PullPolicy;
import io.github.pgatzka.docker.dsl.WaitFor;
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

@UntrackedTask(because = "Docker daemon side effects must always run")
public abstract class StartContainerTask extends DockerTask {

    private static final Duration PULL_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration READINESS_POLL_INTERVAL = Duration.ofMillis(500);
    private static final String LOOPBACK = "127.0.0.1";

    static void run(DockerClient client, Params params, Logger log) {
        pullIfNeeded(client, params.image, params.pullPolicy, log);
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

    private static void validateImageHealthcheckIfRequired(DockerClient client, Params params) {
        if (!(params.waitFor instanceof WaitFor.Healthcheck)) {
            return;
        }
        // Inspecting the image is only needed when the consumer opted into healthcheck readiness.
        InspectImageResponse imageInspect;
        try {
            imageInspect = client.inspectImageCmd(params.image).exec();
        } catch (NotFoundException notFound) {
            throw new GradleException(
                    "Image " + params.image + " is not present locally and pullPolicy=" + params.pullPolicy
                            + " did not pull it.",
                    notFound);
        }
        if (imageDeclaresNoHealthcheck(imageInspect)) {
            throw new GradleException("Container " + params.containerName + " uses waitFor=healthcheck() but image "
                    + params.image + " declares no HEALTHCHECK. Configure waitFor in the container "
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

    private static EnsureResult ensureContainerExists(DockerClient client, Params params, Logger log) {
        Optional<String> existingId = findExistingContainerId(client, params.containerName);
        if (existingId.isPresent()) {
            log.info("Container {} already exists ({})", params.containerName, existingId.get());
            return new EnsureResult(existingId.get(), false);
        }
        log.info("Creating container {}", params.containerName);
        String createdId = createContainer(client, params);
        attachAdditionalNetworks(client, createdId, params.networks);
        log.info("Created container {} ({})", params.containerName, createdId);
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

    private static String createContainer(DockerClient client, Params params) {
        try (CreateContainerCmd createCmd =
                client.createContainerCmd(params.image).withName(params.containerName)) {
            applyEnvironment(createCmd, params.env);
            applyCommand(createCmd, params.command);
            applyPorts(createCmd, params.ports);
            applyMounts(createCmd, params.volumeMounts, params.bindMounts);
            applyPrimaryNetwork(createCmd, params.networks);
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
            CreateContainerCmd createCmd, List<Mounts.VolumeMount> volumeMounts, List<Mounts.BindMount> bindMounts) {
        List<Bind> binds = new ArrayList<>();
        for (Mounts.BindMount bindMount : bindMounts) {
            binds.add(new Bind(
                    bindMount.hostPath(),
                    new Volume(bindMount.containerPath()),
                    bindMount.readOnly() ? AccessMode.ro : AccessMode.rw));
        }
        for (Mounts.VolumeMount volumeMount : volumeMounts) {
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

    private static void startContainer(DockerClient client, Params params, EnsureResult ensured, Logger log) {
        if (!ensured.justCreated && isAlreadyRunningOrUnstartable(client, params, log)) {
            return;
        }
        log.info("Starting container {}", params.containerName);
        client.startContainerCmd(params.containerName).exec();
        log.info("Container {} running", params.containerName);
    }

    /**
     * @return true when the container is already running (caller should skip start). Throws
     *     {@link GradleException} when the container is in a state that requires explicit
     *     removal before it can be started again.
     */
    private static boolean isAlreadyRunningOrUnstartable(DockerClient client, Params params, Logger log) {
        InspectContainerResponse inspect =
                client.inspectContainerCmd(params.containerName).exec();
        var state = inspect.getState();
        if (state == null) {
            return false;
        }
        if (Boolean.TRUE.equals(state.getRunning())) {
            log.info("Container {} already running", params.containerName);
            return true;
        }
        String status = state.getStatus() == null ? "unknown" : state.getStatus();
        if ("paused".equals(status) || "dead".equals(status) || "removing".equals(status)) {
            throw new GradleException("Container " + params.containerName + " is in state '" + status + "'. Run remove"
                    + capitalize(params.containerName) + " first.");
        }
        return false;
    }

    private static void waitForReadiness(DockerClient client, Params params, Logger log) {
        log.info(
                "Waiting for readiness (strategy={}, timeout={})",
                params.waitFor.getClass().getSimpleName(),
                params.waitTimeout);
        switch (params.waitFor) {
            case WaitFor.None ignored -> {
                /* no wait */
            }
            case WaitFor.Healthcheck ignored ->
                Readiness.healthcheck(client, params.containerName, params.waitTimeout, READINESS_POLL_INTERVAL);
            case WaitFor.TcpPort(int containerPort) -> waitForTcp(client, params, containerPort);
            case WaitFor.LogLine(String regex) ->
                Readiness.logLine(client, params.containerName, regex, params.waitTimeout);
        }
        log.info("Ready");
    }

    private static void waitForTcp(DockerClient client, Params params, int containerPort) {
        Integer hostPort = findHostPortMappedTo(params.ports, containerPort);
        if (hostPort == null) {
            throw new GradleException("Container " + params.containerName
                    + " uses waitFor.tcpPort(" + containerPort + ") but no host port is mapped to container port "
                    + containerPort + "; add it to ports{} or use a port that appears as a value in the map.");
        }
        Readiness.tcpPort(daemonHost(), hostPort, params.waitTimeout, READINESS_POLL_INTERVAL);
    }

    private static Integer findHostPortMappedTo(Map<Integer, Integer> ports, int containerPort) {
        return ports.entrySet().stream()
                .filter(entry -> entry.getValue() == containerPort)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    /** Resolve the host where published container ports are reachable from the build machine. */
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

    @Input
    public abstract Property<String> getContainerName();

    @Input
    public abstract Property<String> getImage();

    /**
     * Container environment values. Marked {@code @Internal} on purpose: env values commonly
     * carry secrets and we do not want them fingerprinted into Gradle's task input snapshot or
     * surfaced in build scans / cache snapshots.
     */
    @Internal
    public abstract MapProperty<String, String> getEnvironment();

    @Input
    public abstract MapProperty<Integer, Integer> getPorts();

    @Input
    public abstract ListProperty<String> getNetworks();

    @Input
    public abstract ListProperty<String> getCommand();

    @Input
    public abstract ListProperty<Mounts.VolumeMount> getVolumeMounts();

    @Input
    public abstract ListProperty<Mounts.BindMount> getBindMounts();

    @Input
    public abstract Property<WaitFor> getWaitFor();

    @Input
    public abstract Property<Duration> getWaitTimeout();

    @Input
    public abstract Property<PullPolicy> getPullPolicy();

    @TaskAction
    public void execute() {
        run(
                getDockerService().get().getClient(),
                new Params(
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

    public record Params(
            String containerName,
            String image,
            Map<String, String> env,
            Map<Integer, Integer> ports,
            List<String> networks,
            List<String> command,
            List<Mounts.VolumeMount> volumeMounts,
            List<Mounts.BindMount> bindMounts,
            WaitFor waitFor,
            Duration waitTimeout,
            PullPolicy pullPolicy) {}

    private record EnsureResult(String id, boolean justCreated) {}
}
