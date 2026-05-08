package io.github.pgatzka.docker.dsl.waitable;

public record TcpPort(int port) implements Waitable {}
