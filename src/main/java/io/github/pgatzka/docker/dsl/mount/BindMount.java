package io.github.pgatzka.docker.dsl.mount;

/**
 * Immutable description of a host-path bind mount attached to a container.
 *
 * @param hostPath path on the host machine to bind
 * @param containerPath absolute path inside the container where {@code hostPath} appears
 * @param readOnly {@code true} if the mount should be read-only
 */
public record BindMount(String hostPath, String containerPath, boolean readOnly) {}
