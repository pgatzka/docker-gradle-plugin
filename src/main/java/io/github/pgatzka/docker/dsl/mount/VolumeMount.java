package io.github.pgatzka.docker.dsl.mount;

public record VolumeMount(String volumeName, String containerPath, boolean readOnly) {}
