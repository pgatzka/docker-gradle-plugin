package io.github.pgatzka.docker.dsl.waitable;

/**
 * Readiness strategy that performs no check; the start task completes as soon as
 * {@code docker run} returns. Use {@link Waitable#none()} to obtain an instance.
 */
public record None() implements Waitable {}
