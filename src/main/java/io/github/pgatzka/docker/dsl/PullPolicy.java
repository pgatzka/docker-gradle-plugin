package io.github.pgatzka.docker.dsl;

/**
 * Controls when the plugin pulls a container's image from a registry before {@code docker run}.
 */
public enum PullPolicy {
    /** Always pull the image, even if a local copy already exists. */
    ALWAYS,
    /** Pull only when no local image with the requested reference is present. */
    IF_NOT_PRESENT,
    /** Never pull; fail if the image is not already available locally. */
    NEVER
}
