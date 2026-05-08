package io.github.pgatzka.docker.internal;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.core.command.LogContainerResultCallback;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

public final class Readiness {

    private Readiness() {}

    public static void healthcheck(DockerClient c, String containerId, Duration timeout, Duration pollInterval) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            InspectContainerResponse r = c.inspectContainerCmd(containerId).exec();
            var state = r.getState();
            if (state != null
                    && state.getHealth() != null
                    && "healthy".equalsIgnoreCase(state.getHealth().getStatus())) {
                return;
            }
            sleep(pollInterval);
        }
        throw new NotReadyException("Container " + containerId + " not healthy within " + timeout);
    }

    public static void tcpPort(String host, int port, Duration timeout, Duration pollInterval) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(host, port), (int) pollInterval.toMillis());
                return;
            } catch (Exception ignored) {
                sleep(pollInterval);
            }
        }
        throw new NotReadyException("TCP " + host + ":" + port + " not reachable within " + timeout);
    }

    public static void logLine(DockerClient c, String containerId, String regex, Duration timeout) {
        Pattern pattern = Pattern.compile(regex);
        AtomicBoolean matched = new AtomicBoolean(false);
        var callback = new LogContainerResultCallback() {
            @Override
            public void onNext(Frame frame) {
                String s = new String(frame.getPayload());
                for (String line : s.split("\\r?\\n")) {
                    if (pattern.matcher(line).matches() || pattern.matcher(line).find()) {
                        matched.set(true);
                        try {
                            close();
                        } catch (Exception ignored) {
                        }
                        return;
                    }
                }
            }
        };
        try {
            c.logContainerCmd(containerId)
                    .withStdOut(true)
                    .withStdErr(true)
                    .withFollowStream(true)
                    .withTailAll()
                    .exec(callback)
                    .awaitCompletion(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        if (!matched.get()) {
            throw new NotReadyException("Log line matching /" + regex + "/ not found within " + timeout);
        }
    }

    /**
     * Pure-logic helper used by unit tests.
     */
    public static boolean logLineMatch(String text, String regex) {
        Pattern p = Pattern.compile(regex);
        for (String line : text.split("\\r?\\n")) {
            if (p.matcher(line).matches() || p.matcher(line).find()) return true;
        }
        return false;
    }

    private static void sleep(Duration d) {
        try {
            Thread.sleep(d.toMillis());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    public static final class NotReadyException extends RuntimeException {

        public NotReadyException(String msg) {
            super(msg);
        }
    }
}
