package io.github.pgatzka.docker.task;

import static org.mockito.Mockito.*;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.RemoveContainerCmd;
import com.github.dockerjava.api.exception.NotFoundException;
import org.gradle.api.logging.Logger;
import org.junit.jupiter.api.Test;

class RemoveContainerTaskTest {

    @Test
    void forceRemovesWithoutRemovingVolumes() {
        DockerClient c = mock(DockerClient.class);
        InspectContainerCmd ins = mock(InspectContainerCmd.class);
        InspectContainerResponse resp = mock(InspectContainerResponse.class);
        RemoveContainerCmd rm = mock(RemoveContainerCmd.class, RETURNS_SELF);

        when(c.inspectContainerCmd("c")).thenReturn(ins);
        when(ins.exec()).thenReturn(resp);
        when(c.removeContainerCmd("c")).thenReturn(rm);

        RemoveContainerTask.run(c, "c", mock(Logger.class));

        verify(rm).withForce(true);
        verify(rm).withRemoveVolumes(false);
        verify(rm).exec();
    }

    @Test
    void noOpWhenAbsent() {
        DockerClient c = mock(DockerClient.class);
        InspectContainerCmd ins = mock(InspectContainerCmd.class);
        when(c.inspectContainerCmd("c")).thenReturn(ins);
        when(ins.exec()).thenThrow(new NotFoundException("nope"));

        RemoveContainerTask.run(c, "c", mock(Logger.class));
        verify(c, never()).removeContainerCmd("c");
    }
}
