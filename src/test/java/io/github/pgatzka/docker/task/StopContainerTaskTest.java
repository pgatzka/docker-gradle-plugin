package io.github.pgatzka.docker.task;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.StopContainerCmd;
import com.github.dockerjava.api.exception.NotFoundException;
import org.gradle.api.logging.Logger;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.mockito.Mockito.*;

class StopContainerTaskTest {

    @Test void stopsRunningContainer() {
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

    @Test void noOpWhenAbsent() {
        DockerClient c = mock(DockerClient.class);
        InspectContainerCmd ins = mock(InspectContainerCmd.class);
        when(c.inspectContainerCmd("c")).thenReturn(ins);
        when(ins.exec()).thenThrow(new NotFoundException("nope"));

        StopContainerTask.run(c, "c", Duration.ofSeconds(5), mock(Logger.class));
        verify(c, never()).stopContainerCmd("c");
    }

    @Test void noOpWhenAlreadyStopped() {
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
}
