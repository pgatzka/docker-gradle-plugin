package io.github.pgatzka.docker.dsl.waitable;

/**
 * Readiness strategy that waits until the given TCP port (inside the container) accepts a
 * connection. Use {@link Waitable#tcpPort(int)} to obtain an instance.
 *
 * @param port container-side TCP port number to probe
 */
public record TcpPort(int port) implements Waitable {}
