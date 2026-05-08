package io.github.pgatzka.docker.task.container;

import io.github.pgatzka.docker.dsl.PullPolicy;
import io.github.pgatzka.docker.dsl.mount.BindMount;
import io.github.pgatzka.docker.dsl.mount.VolumeMount;
import io.github.pgatzka.docker.dsl.waitable.Waitable;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public record StartContainerTaskParams(
        String containerName,
        String image,
        Map<String, String> env,
        Map<Integer, Integer> ports,
        List<String> networks,
        List<String> command,
        List<VolumeMount> volumeMounts,
        List<BindMount> bindMounts,
        Waitable waitable,
        Duration waitTimeout,
        PullPolicy pullPolicy) {}
