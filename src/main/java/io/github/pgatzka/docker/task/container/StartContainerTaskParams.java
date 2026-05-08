package io.github.pgatzka.docker.task.container;

import io.github.pgatzka.docker.dsl.PullPolicy;
import io.github.pgatzka.docker.dsl.mount.BindMount;
import io.github.pgatzka.docker.dsl.mount.VolumeMount;
import io.github.pgatzka.docker.dsl.waitable.Waitable;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Snapshot of the inputs consumed by {@code StartContainerTask}'s package-private {@code run}
 * helper. Captured at task-action time so the helper can be tested without a Gradle task.
 *
 * @param containerName the daemon-side container name
 * @param image the image reference (e.g. {@code postgres:16-alpine})
 * @param env environment variables in {@code KEY=VALUE} form
 * @param ports host -> container port mappings
 * @param networks declared networks; the first entry becomes the container's primary network
 * @param command override for the image's CMD
 * @param volumeMounts named-volume mounts (volumes must be declared in the {@code docker {}} extension)
 * @param bindMounts host-path bind mounts
 * @param waitable readiness strategy
 * @param waitTimeout maximum time to wait for readiness
 * @param pullPolicy when to pull the image
 */
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
