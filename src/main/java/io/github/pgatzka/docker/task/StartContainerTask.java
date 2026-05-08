package io.github.pgatzka.docker.task;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.*;
import io.github.pgatzka.docker.dsl.Mounts;
import io.github.pgatzka.docker.dsl.PullPolicy;
import io.github.pgatzka.docker.dsl.WaitFor;
import io.github.pgatzka.docker.internal.Readiness;
import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public abstract class StartContainerTask extends DockerTask {

    @Input public abstract Property<String> getContainerName();
    @Input public abstract Property<String> getImage();
    @Input public abstract MapProperty<String, String> getEnvironment();
    @Input public abstract MapProperty<Integer, Integer> getPorts();
    @Input public abstract ListProperty<String> getNetworks();
    @Input public abstract ListProperty<String> getCommand();
    @Internal public abstract ListProperty<Mounts.VolumeMount> getVolumeMounts();
    @Internal public abstract ListProperty<Mounts.BindMount> getBindMounts();
    @Input public abstract Property<WaitFor> getWaitFor();
    @Input public abstract Property<Duration> getWaitTimeout();
    @Input public abstract Property<PullPolicy> getPullPolicy();

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

    @TaskAction
    public void execute() {
        run(getDockerService().get().getClient(),
            new Params(
                getContainerName().get(), getImage().get(),
                getEnvironment().get(), getPorts().get(),
                getNetworks().get(), getCommand().get(),
                getVolumeMounts().get(), getBindMounts().get(),
                getWaitFor().get(), getWaitTimeout().get(),
                getPullPolicy().get()),
            getLogger());
    }

    static void run(DockerClient c, Params p, Logger log) {
        pullIfNeeded(c, p.image, p.pullPolicy, log);
        InspectImageResponse imgInspect = c.inspectImageCmd(p.image).exec();
        validateHealthcheck(p, imgInspect);

        EnsureResult er = ensureCreated(c, p, log);
        startIfNotRunning(c, p, er, log);
        waitReady(c, p, er.id, log);
    }

    private record EnsureResult(String id, boolean justCreated) {}

    private static void pullIfNeeded(DockerClient c, String image, PullPolicy policy, Logger log) {
        boolean present = c.listImagesCmd().exec().stream()
            .flatMap(i -> i.getRepoTags() == null ? Stream.empty() : Stream.of(i.getRepoTags()))
            .anyMatch(image::equals);
        boolean pull = switch (policy) {
            case ALWAYS -> true;
            case IF_NOT_PRESENT -> !present;
            case NEVER -> false;
        };
        if (!pull) {
            log.info("Skipping pull for {} (policy={}, present={})", image, policy, present);
            return;
        }
        log.info("Pulling {} (policy={})", image, policy);
        try {
            c.pullImageCmd(image).exec(new PullImageResultCallback())
                .awaitCompletion(5, TimeUnit.MINUTES);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new GradleException("Interrupted while pulling " + image, ie);
        }
        log.info("Pull complete: {}", image);
    }

    private static void validateHealthcheck(Params p, InspectImageResponse imgInspect) {
        if (!(p.waitFor instanceof WaitFor.Healthcheck)) return;
        ContainerConfig cfg = imgInspect.getConfig();
        HealthCheck hc = cfg == null ? null : cfg.getHealthcheck();
        boolean none = hc == null
            || (hc.getTest() != null && !hc.getTest().isEmpty()
                && "NONE".equals(hc.getTest().get(0)));
        if (none) {
            throw new GradleException(
                "Container " + p.containerName + " uses waitFor=healthcheck() but image "
                + p.image + " declares no HEALTHCHECK. Configure waitFor in the container "
                + "spec (logLine, tcpPort, or none).");
        }
    }

    private static EnsureResult ensureCreated(DockerClient c, Params p, Logger log) {
        try {
            InspectContainerResponse existing = c.inspectContainerCmd(p.containerName).exec();
            log.info("Container {} already exists ({})", p.containerName, existing.getId());
            return new EnsureResult(existing.getId(), false);
        } catch (NotFoundException ignored) {
            // create
        }
        log.info("Creating container {}", p.containerName);
        CreateContainerCmd create = c.createContainerCmd(p.image)
            .withName(p.containerName);
        if (!p.env.isEmpty()) {
            create.withEnv(p.env.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue()).toList());
        }
        if (!p.command.isEmpty()) {
            create.withCmd(p.command);
        }
        applyPorts(create, p.ports);
        applyMounts(create, p.volumeMounts, p.bindMounts);
        if (!p.networks.isEmpty()) {
            create.getHostConfig().withNetworkMode(p.networks.get(0));
        }
        String id = create.exec().getId();
        // attach additional networks (beyond the first / "default")
        for (int i = 1; i < p.networks.size(); i++) {
            c.connectToNetworkCmd().withContainerId(id).withNetworkId(p.networks.get(i)).exec();
        }
        log.info("Created container {} ({})", p.containerName, id);
        return new EnsureResult(id, true);
    }

    private static void applyPorts(CreateContainerCmd cmd, Map<Integer, Integer> ports) {
        if (ports.isEmpty()) return;
        List<ExposedPort> exposed = new ArrayList<>();
        Ports bindings = new Ports();
        ports.forEach((host, container) -> {
            ExposedPort ep = ExposedPort.tcp(container);
            exposed.add(ep);
            bindings.bind(ep, Ports.Binding.bindPort(host));
        });
        cmd.withExposedPorts(exposed);
        cmd.getHostConfig().withPortBindings(bindings);
    }

    private static void applyMounts(CreateContainerCmd cmd,
                                    List<Mounts.VolumeMount> volMounts,
                                    List<Mounts.BindMount> bindMounts) {
        List<Bind> binds = new ArrayList<>();
        for (var b : bindMounts) {
            binds.add(new Bind(b.hostPath(), new Volume(b.containerPath()),
                b.readOnly() ? AccessMode.ro : AccessMode.rw));
        }
        for (var v : volMounts) {
            binds.add(new Bind(v.volumeName(), new Volume(v.containerPath()),
                v.readOnly() ? AccessMode.ro : AccessMode.rw));
        }
        if (!binds.isEmpty()) {
            cmd.getHostConfig().withBinds(binds);
        }
    }

    private static void startIfNotRunning(DockerClient c, Params p, EnsureResult er, Logger log) {
        if (!er.justCreated) {
            InspectContainerResponse r = c.inspectContainerCmd(p.containerName).exec();
            var st = r.getState();
            String status = st == null ? "unknown" : st.getStatus();
            if (st != null && Boolean.TRUE.equals(st.getRunning())) {
                log.info("Container {} already running", p.containerName);
                return;
            }
            if (st != null && ("paused".equals(status) || "dead".equals(status) || "removing".equals(status))) {
                throw new GradleException(
                    "Container " + p.containerName + " is in state '" + status
                    + "'. Run remove" + capitalize(p.containerName) + " first.");
            }
        }
        log.info("Starting container {}", p.containerName);
        c.startContainerCmd(p.containerName).exec();
        log.info("Container {} running", p.containerName);
    }

    private static void waitReady(DockerClient c, Params p, String id, Logger log) {
        Duration poll = Duration.ofMillis(500);
        log.info("Waiting for readiness (strategy={}, timeout={})",
            p.waitFor.getClass().getSimpleName(), p.waitTimeout);
        switch (p.waitFor) {
            case WaitFor.None ignored -> { /* no wait */ }
            case WaitFor.Healthcheck ignored ->
                Readiness.healthcheck(c, p.containerName, p.waitTimeout, poll);
            case WaitFor.TcpPort tp -> {
                int hostPort = p.ports.entrySet().stream()
                    .filter(e -> e.getValue() == tp.port())
                    .map(Map.Entry::getKey).findFirst()
                    .orElse(tp.port());
                Readiness.tcpPort("127.0.0.1", hostPort, p.waitTimeout, poll);
            }
            case WaitFor.LogLine ll ->
                Readiness.logLine(c, p.containerName, ll.regex(), p.waitTimeout);
        }
        log.info("Ready");
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
