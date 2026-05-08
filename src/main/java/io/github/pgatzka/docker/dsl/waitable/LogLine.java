package io.github.pgatzka.docker.dsl.waitable;

public record LogLine(String regex) implements Waitable {
}
