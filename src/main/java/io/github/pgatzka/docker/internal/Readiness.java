package io.github.pgatzka.docker.internal;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Frame;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public final class Readiness {

    private Readiness() {}

    public static void healthcheck(DockerClient c, String containerId, Duration timeout, Duration pollInterval) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            try {
                InspectContainerResponse r = c.inspectContainerCmd(containerId).exec();
                var state = r.getState();
                if (state != null
                        && state.getHealth() != null
                        && "healthy".equalsIgnoreCase(state.getHealth().getStatus())) {
                    return;
                }
            } catch (NotFoundException nf) {
                throw new NotReadyException(
                        "Container " + containerId + " disappeared while waiting for health: " + nf.getMessage());
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
            } catch (java.io.InterruptedIOException iie) {
                Thread.currentThread().interrupt();
                throw new NotReadyException("Interrupted while waiting on TCP " + host + ":" + port);
            } catch (Exception ignored) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new NotReadyException("Interrupted while waiting on TCP " + host + ":" + port);
                }
                sleep(pollInterval);
            }
        }
        throw new NotReadyException("TCP " + host + ":" + port + " not reachable within " + timeout);
    }

    public static void logLine(DockerClient c, String containerId, String regex, Duration timeout) {
        Pattern pattern = Pattern.compile(regex);
        // Buffer partial lines across frames: docker log frames are not guaranteed to be line-aligned.
        StringBuilder partial = new StringBuilder();
        var callback = new MatchingLogCallback(pattern, partial);
        try (var stream = c.logContainerCmd(containerId)
                .withStdOut(true)
                .withStdErr(true)
                .withFollowStream(true)
                .withTailAll()
                .exec(callback)) {
            boolean completed = stream.awaitCompletion(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!completed && !callback.matched()) {
                throw new NotReadyException("Log line matching /" + regex + "/ not found within " + timeout);
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new NotReadyException("Interrupted while waiting for log line /" + regex + "/");
        } catch (java.io.IOException io) {
            // best-effort close failure during try-with-resources; surface as not-ready
            throw new NotReadyException(
                    "Log stream closed before log line /" + regex + "/ matched: " + io.getMessage());
        }
        if (!callback.matched()) {
            throw new NotReadyException("Log line matching /" + regex + "/ not found within " + timeout);
        }
    }

    /** Pure-logic helper used by unit tests. */
    public static boolean logLineMatch(String text, String regex) {
        Pattern p = Pattern.compile(regex);
        for (String line : text.split("\\r?\\n")) {
            if (p.matcher(line).find()) return true;
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

    private static final class MatchingLogCallback extends ResultCallback.Adapter<Frame> {
        private final Pattern pattern;
        private final StringBuilder partial;
        private volatile boolean matched;

        MatchingLogCallback(Pattern pattern, StringBuilder partial) {
            this.pattern = pattern;
            this.partial = partial;
        }

        boolean matched() {
            return matched;
        }

        @Override
        public void onNext(Frame frame) {
            if (matched) return;
            // UTF-8 is the standard for docker daemon log frames.
            partial.append(new String(frame.getPayload(), StandardCharsets.UTF_8));
            int newline;
            while ((newline = partial.indexOf("\n")) >= 0) {
                String line = partial.substring(0, newline);
                partial.delete(0, newline + 1);
                if (pattern.matcher(line).find()) {
                    matched = true;
                    try {
                        close();
                    } catch (Exception ignored) {
                        // best-effort close after match; the stream end will surface separately if needed
                    }
                    return;
                }
            }
        }
    }

    public static final class NotReadyException extends RuntimeException {

        public NotReadyException(String msg) {
            super(msg);
        }
    }
}
