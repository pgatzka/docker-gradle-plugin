package io.github.pgatzka.docker.internal;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Frame;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public final class Readiness {

    private Readiness() {}

    public static void healthcheck(DockerClient client, String containerId, Duration timeout, Duration pollInterval) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            try {
                InspectContainerResponse inspect =
                        client.inspectContainerCmd(containerId).exec();
                var state = inspect.getState();
                if (state != null
                        && state.getHealth() != null
                        && "healthy".equalsIgnoreCase(state.getHealth().getStatus())) {
                    return;
                }
            } catch (NotFoundException notFound) {
                throw new NotReadyException(
                        "Container " + containerId + " disappeared while waiting for health: " + notFound.getMessage());
            }
            sleep(pollInterval);
        }
        throw new NotReadyException("Container " + containerId + " not healthy within " + timeout);
    }

    public static void tcpPort(String host, int port, Duration timeout, Duration pollInterval) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), (int) pollInterval.toMillis());
                return;
            } catch (InterruptedIOException interrupted) {
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

    public static void logLine(DockerClient client, String containerId, String regex, Duration timeout) {
        Pattern pattern = Pattern.compile(regex);
        // Buffer partial lines across frames: docker log frames are not guaranteed to be line-aligned.
        StringBuilder partialLineBuffer = new StringBuilder();
        MatchingLogCallback callback = new MatchingLogCallback(pattern, partialLineBuffer);
        streamUntilTimeoutOrMatch(client, containerId, regex, timeout, callback);
        // Re-check after the stream returns: the callback may have set `matched` after we
        // already saw `completed=true` from awaitCompletion (close-vs-flag visibility race),
        // and we don't want to throw when the log line did, in fact, appear.
        if (!callback.matched()) {
            throw new NotReadyException("Log line matching /" + regex + "/ not found within " + timeout);
        }
    }

    private static void streamUntilTimeoutOrMatch(
            DockerClient client, String containerId, String regex, Duration timeout, MatchingLogCallback callback) {
        try (var stream = client.logContainerCmd(containerId)
                .withStdOut(true)
                .withStdErr(true)
                .withFollowStream(true)
                .withTailAll()
                .exec(callback)) {
            boolean completed = stream.awaitCompletion(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!completed && !callback.matched()) {
                throw new NotReadyException("Log line matching /" + regex + "/ not found within " + timeout);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new NotReadyException("Interrupted while waiting for log line /" + regex + "/");
        } catch (IOException ioFailure) {
            // best-effort close failure during try-with-resources; surface as not-ready
            throw new NotReadyException(
                    "Log stream closed before log line /" + regex + "/ matched: " + ioFailure.getMessage());
        }
    }

    /** Pure-logic helper used by unit tests. */
    public static boolean logLineMatch(String text, String regex) {
        Pattern pattern = Pattern.compile(regex);
        for (String line : text.split("\\r?\\n")) {
            if (pattern.matcher(line).find()) return true;
        }
        return false;
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException interrupted) {
            // Preserve the interrupt status; the polling loop's deadline check ends the wait
            // promptly. Throwing here would force every caller to handle the same case.
            Thread.currentThread().interrupt();
        }
    }

    private static final class MatchingLogCallback extends ResultCallback.Adapter<Frame> {
        private final Pattern pattern;
        private final StringBuilder partialLineBuffer;
        private volatile boolean matched;

        MatchingLogCallback(Pattern pattern, StringBuilder partialLineBuffer) {
            this.pattern = pattern;
            this.partialLineBuffer = partialLineBuffer;
        }

        boolean matched() {
            return matched;
        }

        @Override
        public void onNext(Frame frame) {
            if (matched) return;
            // UTF-8 is the standard for docker daemon log frames.
            partialLineBuffer.append(new String(frame.getPayload(), StandardCharsets.UTF_8));
            int newlineIndex;
            while ((newlineIndex = partialLineBuffer.indexOf("\n")) >= 0) {
                String line = partialLineBuffer.substring(0, newlineIndex);
                partialLineBuffer.delete(0, newlineIndex + 1);
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

}
