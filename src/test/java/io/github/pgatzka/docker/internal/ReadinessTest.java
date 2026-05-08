package io.github.pgatzka.docker.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.HealthState;
import com.github.dockerjava.api.command.InspectContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.LogContainerCmd;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ReadinessTest {

    @Test
    void healthcheckReturnsWhenHealthy() {
        DockerClient c = mock(DockerClient.class);
        InspectContainerCmd cmd = mock(InspectContainerCmd.class);
        InspectContainerResponse resp = mock(InspectContainerResponse.class);
        InspectContainerResponse.ContainerState state = mock(InspectContainerResponse.ContainerState.class);
        HealthState health = mock(HealthState.class);

        when(c.inspectContainerCmd("cid")).thenReturn(cmd);
        when(cmd.exec()).thenReturn(resp);
        when(resp.getState()).thenReturn(state);
        when(state.getHealth()).thenReturn(health);
        when(health.getStatus()).thenReturn("healthy");

        assertThatNoException()
                .isThrownBy(() -> Readiness.healthcheck(c, "cid", Duration.ofSeconds(1), Duration.ofMillis(10)));
    }

    @Test
    void healthcheckTimesOutWhenNeverHealthy() {
        DockerClient c = mock(DockerClient.class);
        InspectContainerCmd cmd = mock(InspectContainerCmd.class);
        InspectContainerResponse resp = mock(InspectContainerResponse.class);
        InspectContainerResponse.ContainerState state = mock(InspectContainerResponse.ContainerState.class);
        HealthState health = mock(HealthState.class);

        when(c.inspectContainerCmd("cid")).thenReturn(cmd);
        when(cmd.exec()).thenReturn(resp);
        when(resp.getState()).thenReturn(state);
        when(state.getHealth()).thenReturn(health);
        when(health.getStatus()).thenReturn("starting");

        assertThatThrownBy(() -> Readiness.healthcheck(c, "cid", Duration.ofMillis(50), Duration.ofMillis(10)))
                .isInstanceOf(Readiness.NotReadyException.class)
                .hasMessageContaining("not healthy within");
    }

    @Test
    void healthcheckThrowsWhenContainerDisappears() {
        DockerClient c = mock(DockerClient.class);
        InspectContainerCmd cmd = mock(InspectContainerCmd.class);

        when(c.inspectContainerCmd("cid")).thenReturn(cmd);
        when(cmd.exec()).thenThrow(new NotFoundException("no such container"));

        assertThatThrownBy(() -> Readiness.healthcheck(c, "cid", Duration.ofSeconds(1), Duration.ofMillis(10)))
                .isInstanceOf(Readiness.NotReadyException.class)
                .hasMessageContaining("disappeared while waiting for health");
    }

    @Test
    void healthcheckTimesOutWhenStateHasNoHealth() {
        DockerClient c = mock(DockerClient.class);
        InspectContainerCmd cmd = mock(InspectContainerCmd.class);
        InspectContainerResponse resp = mock(InspectContainerResponse.class);
        InspectContainerResponse.ContainerState state = mock(InspectContainerResponse.ContainerState.class);

        when(c.inspectContainerCmd("cid")).thenReturn(cmd);
        when(cmd.exec()).thenReturn(resp);
        when(resp.getState()).thenReturn(state);
        when(state.getHealth()).thenReturn(null);

        assertThatThrownBy(() -> Readiness.healthcheck(c, "cid", Duration.ofMillis(50), Duration.ofMillis(10)))
                .isInstanceOf(Readiness.NotReadyException.class)
                .hasMessageContaining("not healthy within");
    }

    @Test
    void healthcheckTimesOutWhenStateIsNull() {
        DockerClient c = mock(DockerClient.class);
        InspectContainerCmd cmd = mock(InspectContainerCmd.class);
        InspectContainerResponse resp = mock(InspectContainerResponse.class);

        when(c.inspectContainerCmd("cid")).thenReturn(cmd);
        when(cmd.exec()).thenReturn(resp);
        when(resp.getState()).thenReturn(null);

        assertThatThrownBy(() -> Readiness.healthcheck(c, "cid", Duration.ofMillis(50), Duration.ofMillis(10)))
                .isInstanceOf(Readiness.NotReadyException.class)
                .hasMessageContaining("not healthy within");
    }

    @Test
    void healthcheckDoesNotReturnForUnhealthyStatus() {
        DockerClient c = mock(DockerClient.class);
        InspectContainerCmd cmd = mock(InspectContainerCmd.class);
        InspectContainerResponse resp = mock(InspectContainerResponse.class);
        InspectContainerResponse.ContainerState state = mock(InspectContainerResponse.ContainerState.class);
        HealthState health = mock(HealthState.class);

        when(c.inspectContainerCmd("cid")).thenReturn(cmd);
        when(cmd.exec()).thenReturn(resp);
        when(resp.getState()).thenReturn(state);
        when(state.getHealth()).thenReturn(health);
        when(health.getStatus()).thenReturn("unhealthy");

        assertThatThrownBy(() -> Readiness.healthcheck(c, "cid", Duration.ofMillis(50), Duration.ofMillis(10)))
                .isInstanceOf(Readiness.NotReadyException.class)
                .hasMessageContaining("not healthy within");
    }

    @Test
    void tcpPortReturnsWhenSocketAccepts() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            int port = s.getLocalPort();
            assertThatNoException()
                    .isThrownBy(
                            () -> Readiness.tcpPort("127.0.0.1", port, Duration.ofSeconds(1), Duration.ofMillis(10)));
        }
    }

    @Test
    void tcpPortTimesOutWhenNothingListening() {
        assertThatThrownBy(() -> Readiness.tcpPort("127.0.0.1", 1, Duration.ofMillis(50), Duration.ofMillis(10)))
                .isInstanceOf(Readiness.NotReadyException.class)
                .hasMessageContaining("TCP");
    }

    @Test
    void tcpPortThrowsWhenInterruptFlagIsSet() throws Exception {
        // Run on a worker thread so we can pre-set its interrupt flag without polluting the
        // test thread. The connect attempt to a closed port fails fast (ConnectException),
        // hits the generic catch, then sees the interrupt flag and throws.
        Thread worker = new Thread(() -> {
            Thread.currentThread().interrupt();
            Readiness.tcpPort("127.0.0.1", 1, Duration.ofSeconds(5), Duration.ofMillis(10));
        });
        Throwable[] thrown = new Throwable[1];
        worker.setUncaughtExceptionHandler((t, e) -> thrown[0] = e);
        worker.start();
        worker.join(5_000);
        assertThat(worker.isAlive()).isFalse();
        assertThat(thrown[0])
                .isInstanceOf(Readiness.NotReadyException.class)
                .hasMessageContaining("Interrupted while waiting on TCP");
    }

    @Test
    void logLineMatchesRegexInStream() {
        boolean matched = Readiness.logLineMatch("starting up\nready to accept connections\nidle\n", ".*ready.*");
        assertThat(matched).isTrue();
    }

    @Test
    void logLineDoesNotMatchUnrelatedText() {
        boolean matched = Readiness.logLineMatch("idle\nidle\n", ".*ready.*");
        assertThat(matched).isFalse();
    }

    // ---- logLine end-to-end (mocked docker-java) -------------------------------------------------

    @Test
    void logLineReturnsWhenFrameMatches() {
        DockerClient c = mock(DockerClient.class);
        LogContainerCmd cmd = mock(LogContainerCmd.class, RETURNS_SELF);
        when(c.logContainerCmd("cid")).thenReturn(cmd);
        when(cmd.exec(any())).thenAnswer(inv -> {
            ResultCallback.Adapter<Frame> cb = inv.getArgument(0);
            cb.onStart(() -> {});
            cb.onNext(frame("ready to accept connections\n"));
            return cb;
        });

        assertThatNoException()
                .isThrownBy(() -> Readiness.logLine(c, "cid", ".*ready.*", Duration.ofSeconds(2)));
    }

    @Test
    void logLineThrowsWhenAwaitCompletionTimesOutWithoutMatch() {
        DockerClient c = mock(DockerClient.class);
        LogContainerCmd cmd = mock(LogContainerCmd.class, RETURNS_SELF);
        when(c.logContainerCmd("cid")).thenReturn(cmd);
        when(cmd.exec(any())).thenAnswer(inv -> {
            ResultCallback.Adapter<Frame> cb = inv.getArgument(0);
            cb.onStart(() -> {});
            // No matching frames, no onComplete: awaitCompletion returns false after the timeout.
            cb.onNext(frame("still booting\n"));
            return cb;
        });

        assertThatThrownBy(() -> Readiness.logLine(c, "cid", ".*ready.*", Duration.ofMillis(50)))
                .isInstanceOf(Readiness.NotReadyException.class)
                .hasMessageContaining("not found within");
    }

    @Test
    void logLineThrowsWhenInterruptedDuringAwait() throws Exception {
        DockerClient c = mock(DockerClient.class);
        LogContainerCmd cmd = mock(LogContainerCmd.class, RETURNS_SELF);
        when(c.logContainerCmd("cid")).thenReturn(cmd);
        when(cmd.exec(any())).thenAnswer(inv -> {
            ResultCallback.Adapter<Frame> cb = inv.getArgument(0);
            cb.onStart(() -> {});
            // Never completes; the test thread will be interrupted while awaiting.
            return cb;
        });

        Thread worker = new Thread(() -> Readiness.logLine(c, "cid", ".*ready.*", Duration.ofSeconds(10)));
        Throwable[] thrown = new Throwable[1];
        worker.setUncaughtExceptionHandler((t, e) -> thrown[0] = e);
        worker.start();
        // Give the worker a moment to enter awaitCompletion, then interrupt.
        Thread.sleep(100);
        worker.interrupt();
        worker.join(5_000);
        assertThat(worker.isAlive()).isFalse();
        assertThat(thrown[0])
                .isInstanceOf(Readiness.NotReadyException.class)
                .hasMessageContaining("Interrupted while waiting");
    }

    @Test
    void logLineMatchesAcrossPartialLineFrames() {
        DockerClient c = mock(DockerClient.class);
        LogContainerCmd cmd = mock(LogContainerCmd.class, RETURNS_SELF);
        when(c.logContainerCmd("cid")).thenReturn(cmd);
        when(cmd.exec(any())).thenAnswer(inv -> {
            ResultCallback.Adapter<Frame> cb = inv.getArgument(0);
            cb.onStart(() -> {});
            cb.onNext(frame("rea"));
            cb.onNext(frame("dy\n"));
            return cb;
        });

        assertThatNoException()
                .isThrownBy(() -> Readiness.logLine(c, "cid", ".*ready.*", Duration.ofSeconds(2)));
    }

    @Test
    void logLineIgnoresFurtherFramesAfterMatch() {
        // After the first frame matches the callback closes itself and sets matched=true.
        // A subsequent onNext(...) with a payload that would otherwise mutate state must be
        // a no-op: the public method still returns successfully.
        DockerClient c = mock(DockerClient.class);
        LogContainerCmd cmd = mock(LogContainerCmd.class, RETURNS_SELF);
        when(c.logContainerCmd("cid")).thenReturn(cmd);
        when(cmd.exec(any())).thenAnswer(inv -> {
            ResultCallback.Adapter<Frame> cb = inv.getArgument(0);
            cb.onStart(() -> {});
            cb.onNext(frame("ready\n"));
            // Second frame after match — should be short-circuited inside MatchingLogCallback.
            cb.onNext(frame("noise that would not match anyway\n"));
            return cb;
        });

        assertThatNoException()
                .isThrownBy(() -> Readiness.logLine(c, "cid", ".*ready.*", Duration.ofSeconds(2)));
    }

    private static Frame frame(String payload) {
        return new Frame(StreamType.STDOUT, payload.getBytes(StandardCharsets.UTF_8));
    }
}
