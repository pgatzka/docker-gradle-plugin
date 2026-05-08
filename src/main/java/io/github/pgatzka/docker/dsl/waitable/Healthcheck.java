package io.github.pgatzka.docker.dsl.waitable;

/**
 * Readiness strategy that waits for the container's Docker {@code HEALTHCHECK} to report
 * {@code healthy}. Use {@link Waitable#healthcheck()} to obtain an instance.
 */
public record Healthcheck() implements Waitable {}
