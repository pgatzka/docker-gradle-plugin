package io.github.pgatzka.docker.task.container;

import static org.mockito.Mockito.*;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.StopContainerCmd;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.exception.NotModifiedException;
import java.time.Duration;
import org.gradle.api.logging.Logger;
import org.junit.jupiter.api.Test;

class StopContainerTaskTest {

    @Test
    void stopsRunningContainer() {
        DockerClient c = mock(DockerClient.class);
        InspectContainerCmd ins = mock(InspectContainerCmd.class);
        InspectContainerResponse resp = mock(InspectContainerResponse.class);
        InspectContainerResponse.ContainerState st = mock(InspectContainerResponse.ContainerState.class);
        StopContainerCmd stop = mock(StopContainerCmd.class, RETURNS_SELF);

        when(c.inspectContainerCmd("c")).thenReturn(ins);
        when(ins.exec()).thenReturn(resp);
        when(resp.getState()).thenReturn(st);
        when(st.getRunning()).thenReturn(true);
        when(c.stopContainerCmd("c")).thenReturn(stop);

        StopContainerTask.run(c, "c", Duration.ofSeconds(5), mock(Logger.class));
        verify(stop).withTimeout(5);
        verify(stop).exec();
    }

    @Test
    void noOpWhenAbsent() {
        DockerClient c = mock(DockerClient.class);
        InspectContainerCmd ins = mock(InspectContainerCmd.class);
        when(c.inspectContainerCmd("c")).thenReturn(ins);
        when(ins.exec()).thenThrow(new NotFoundException("nope"));

        StopContainerTask.run(c, "c", Duration.ofSeconds(5), mock(Logger.class));
        verify(c, never()).stopContainerCmd("c");
    }

    @Test
    void noOpWhenAlreadyStopped() {
        DockerClient c = mock(DockerClient.class);
        InspectContainerCmd ins = mock(InspectContainerCmd.class);
        InspectContainerResponse resp = mock(InspectContainerResponse.class);
        InspectContainerResponse.ContainerState st = mock(InspectContainerResponse.ContainerState.class);

        when(c.inspectContainerCmd("c")).thenReturn(ins);
        when(ins.exec()).thenReturn(resp);
        when(resp.getState()).thenReturn(st);
        when(st.getRunning()).thenReturn(false);

        StopContainerTask.run(c, "c", Duration.ofSeconds(5), mock(Logger.class));
        verify(c, never()).stopContainerCmd("c");
    }

    @Test
    void noOpWhenStateIsNull() {
        // Inspect succeeds but ContainerState is null — early return on the `getState() == null` branch.
        DockerClient c = mock(DockerClient.class);
        InspectContainerCmd ins = mock(InspectContainerCmd.class);
        InspectContainerResponse resp = mock(InspectContainerResponse.class);

        when(c.inspectContainerCmd("c")).thenReturn(ins);
        when(ins.exec()).thenReturn(resp);
        when(resp.getState()).thenReturn(null);

        StopContainerTask.run(c, "c", Duration.ofSeconds(5), mock(Logger.class));
        verify(c, never()).stopContainerCmd("c");
    }

    @Test
    void timeoutZeroPassesZero() {
        DockerClient c = mock(DockerClient.class);
        InspectContainerCmd ins = mock(InspectContainerCmd.class);
        InspectContainerResponse resp = mock(InspectContainerResponse.class);
        InspectContainerResponse.ContainerState st = mock(InspectContainerResponse.ContainerState.class);
        StopContainerCmd stop = mock(StopContainerCmd.class, RETURNS_SELF);

        when(c.inspectContainerCmd("c")).thenReturn(ins);
        when(ins.exec()).thenReturn(resp);
        when(resp.getState()).thenReturn(st);
        when(st.getRunning()).thenReturn(true);
        when(c.stopContainerCmd("c")).thenReturn(stop);

        StopContainerTask.run(c, "c", Duration.ofSeconds(0), mock(Logger.class));
        verify(stop).withTimeout(0);
        verify(stop).exec();
    }

    @Test
    void timeoutOverflowTruncatesToIntegerMax() {
        // Duration in seconds exceeds Integer.MAX_VALUE — the truncation guard caps at MAX_VALUE.
        DockerClient c = mock(DockerClient.class);
        InspectContainerCmd ins = mock(InspectContainerCmd.class);
        InspectContainerResponse resp = mock(InspectContainerResponse.class);
        InspectContainerResponse.ContainerState st = mock(InspectContainerResponse.ContainerState.class);
        StopContainerCmd stop = mock(StopContainerCmd.class, RETURNS_SELF);

        when(c.inspectContainerCmd("c")).thenReturn(ins);
        when(ins.exec()).thenReturn(resp);
        when(resp.getState()).thenReturn(st);
        when(st.getRunning()).thenReturn(true);
        when(c.stopContainerCmd("c")).thenReturn(stop);

        StopContainerTask.run(c, "c", Duration.ofSeconds(Long.MAX_VALUE / 2), mock(Logger.class));
        verify(stop).withTimeout(Integer.MAX_VALUE);
        verify(stop).exec();
    }

    @Test
    void negativeTimeoutClampsToZero() {
        // Math.max(0, ...) guard — negative durations must not reach docker-java as negative ints.
        DockerClient c = mock(DockerClient.class);
        InspectContainerCmd ins = mock(InspectContainerCmd.class);
        InspectContainerResponse resp = mock(InspectContainerResponse.class);
        InspectContainerResponse.ContainerState st = mock(InspectContainerResponse.ContainerState.class);
        StopContainerCmd stop = mock(StopContainerCmd.class, RETURNS_SELF);

        when(c.inspectContainerCmd("c")).thenReturn(ins);
        when(ins.exec()).thenReturn(resp);
        when(resp.getState()).thenReturn(st);
        when(st.getRunning()).thenReturn(true);
        when(c.stopContainerCmd("c")).thenReturn(stop);

        StopContainerTask.run(c, "c", Duration.ofSeconds(-5), mock(Logger.class));
        verify(stop).withTimeout(0);
        verify(stop).exec();
    }

    @Test
    void noOpWhenContainerExitsBetweenInspectAndStop() {
        // Race: container is running at inspect time, but exits on its own before
        // stopContainerCmd reaches the daemon. Docker returns HTTP 304 ->
        // docker-java throws NotModifiedException. The stop must be idempotent.
        DockerClient c = mock(DockerClient.class);
        InspectContainerCmd ins = mock(InspectContainerCmd.class);
        InspectContainerResponse resp = mock(InspectContainerResponse.class);
        InspectContainerResponse.ContainerState st = mock(InspectContainerResponse.ContainerState.class);
        StopContainerCmd stop = mock(StopContainerCmd.class, RETURNS_SELF);

        when(c.inspectContainerCmd("c")).thenReturn(ins);
        when(ins.exec()).thenReturn(resp);
        when(resp.getState()).thenReturn(st);
        when(st.getRunning()).thenReturn(true);
        when(c.stopContainerCmd("c")).thenReturn(stop);
        doThrow(new NotModifiedException("304")).when(stop).exec();

        // Must not propagate — the race is a benign no-op.
        StopContainerTask.run(c, "c", Duration.ofSeconds(5), mock(Logger.class));
        verify(stop).exec();
    }
}
