package io.github.pgatzka.docker.dsl.mount;

public record BindMount(String hostPath, String containerPath, boolean readOnly) {
}
