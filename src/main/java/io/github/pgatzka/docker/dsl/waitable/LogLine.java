package io.github.pgatzka.docker.dsl.waitable;

/**
 * Readiness strategy that waits until a log line matching {@code regex} is observed on the
 * container's stdout or stderr. Use {@link Waitable#logLine(String)} to obtain an instance.
 *
 * @param regex Java regular expression matched against each emitted log line
 */
public record LogLine(String regex) implements Waitable {}
