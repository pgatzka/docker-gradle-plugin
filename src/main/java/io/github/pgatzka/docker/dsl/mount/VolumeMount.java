package io.github.pgatzka.docker.dsl.mount;

/**
 * Immutable description of a named-volume mount attached to a container.
 *
 * @param volumeName name of a volume declared in {@code docker.volumes}
 * @param containerPath absolute path inside the container where the volume is mounted
 * @param readOnly {@code true} if the mount should be read-only
 */
public record VolumeMount(String volumeName, String containerPath, boolean readOnly) {}
