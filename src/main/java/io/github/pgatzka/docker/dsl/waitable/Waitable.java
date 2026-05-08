package io.github.pgatzka.docker.dsl.waitable;

import java.io.Serializable;

/**
 * Strategy that determines when {@code start<ContainerName>} considers a container ready. This is
 * a sealed contract: implementations are the records {@link Healthcheck} (waits for the image's
 * Docker {@code HEALTHCHECK} to report healthy), {@link LogLine} (waits for a regex match in
 * container logs), {@link TcpPort} (waits for a TCP port to accept connections), and {@link None}
 * (no readiness check). {@code Serializable} so values survive Gradle's configuration cache.
 */
public sealed interface Waitable extends Serializable permits Healthcheck, LogLine, TcpPort, None {

    /**
     * Wait until the container's Docker {@code HEALTHCHECK} reports {@code healthy}. Fails fast at
     * start time if the image declares no HEALTHCHECK.
     *
     * @return a {@link Healthcheck} marker
     */
    static Waitable healthcheck() {
        return new Healthcheck();
    }

    /**
     * Wait until a line matching {@code regex} appears in the container's stdout/stderr stream.
     *
     * @param regex Java regular expression matched against each log line
     * @return a {@link LogLine} strategy bound to {@code regex}
     */
    static Waitable logLine(String regex) {
        return new LogLine(regex);
    }

    /**
     * Wait until the given TCP port inside the container accepts a connection.
     *
     * @param port container-side TCP port to probe
     * @return a {@link TcpPort} strategy bound to {@code port}
     */
    static Waitable tcpPort(int port) {
        return new TcpPort(port);
    }

    /**
     * No readiness check; the start task returns as soon as {@code docker run} returns.
     *
     * @return a {@link None} marker
     */
    static Waitable none() {
        return new None();
    }
}
