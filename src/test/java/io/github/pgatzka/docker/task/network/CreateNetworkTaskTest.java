package io.github.pgatzka.docker.task.network;

import static org.mockito.Mockito.*;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateNetworkCmd;
import com.github.dockerjava.api.command.ListNetworksCmd;
import com.github.dockerjava.api.model.Network;
import java.util.List;
import java.util.Map;
import org.gradle.api.logging.Logger;
import org.junit.jupiter.api.Test;

class CreateNetworkTaskTest {

    @Test
    void createsWhenAbsent() {
        DockerClient c = mock(DockerClient.class);
        ListNetworksCmd list = mock(ListNetworksCmd.class, RETURNS_SELF);
        CreateNetworkCmd create = mock(CreateNetworkCmd.class, RETURNS_SELF);

        when(c.listNetworksCmd()).thenReturn(list);
        when(list.exec()).thenReturn(List.of());
        when(c.createNetworkCmd()).thenReturn(create);

        CreateNetworkTask.run(c, "backend", "bridge", Map.of("k", "v"), true, false, mock(Logger.class));

        verify(create).withName("backend");
        verify(create).withDriver("bridge");
        verify(create).withLabels(Map.of("k", "v"));
        verify(create).withInternal(true);
        verify(create).withAttachable(false);
        verify(create).exec();
    }

    @Test
    void noOpWhenPresent() {
        DockerClient c = mock(DockerClient.class);
        ListNetworksCmd list = mock(ListNetworksCmd.class, RETURNS_SELF);
        Network existing = mock(Network.class);

        when(c.listNetworksCmd()).thenReturn(list);
        when(list.exec()).thenReturn(List.of(existing));
        when(existing.getName()).thenReturn("backend");

        CreateNetworkTask.run(c, "backend", "bridge", Map.of(), false, false, mock(Logger.class));
        verify(c, never()).createNetworkCmd();
    }

    @Test
    void createsWithoutLabelsWhenLabelsEmpty() {
        DockerClient c = mock(DockerClient.class);
        ListNetworksCmd list = mock(ListNetworksCmd.class, RETURNS_SELF);
        CreateNetworkCmd create = mock(CreateNetworkCmd.class, RETURNS_SELF);

        when(c.listNetworksCmd()).thenReturn(list);
        when(list.exec()).thenReturn(List.of());
        when(c.createNetworkCmd()).thenReturn(create);

        CreateNetworkTask.run(c, "backend", "bridge", Map.of(), false, false, mock(Logger.class));

        verify(create).withName("backend");
        verify(create).withDriver("bridge");
        verify(create).withInternal(false);
        verify(create).withAttachable(false);
        verify(create, never()).withLabels(anyMap());
        verify(create).exec();
    }

    @Test
    void createsWithInternalFalseAttachableTrue() {
        DockerClient c = mock(DockerClient.class);
        ListNetworksCmd list = mock(ListNetworksCmd.class, RETURNS_SELF);
        CreateNetworkCmd create = mock(CreateNetworkCmd.class, RETURNS_SELF);

        when(c.listNetworksCmd()).thenReturn(list);
        when(list.exec()).thenReturn(List.of());
        when(c.createNetworkCmd()).thenReturn(create);

        CreateNetworkTask.run(c, "backend", "bridge", Map.of(), false, true, mock(Logger.class));

        verify(create).withInternal(false);
        verify(create).withAttachable(true);
        verify(create).exec();
    }

    @Test
    void createsWithInternalTrueAttachableTrue() {
        DockerClient c = mock(DockerClient.class);
        ListNetworksCmd list = mock(ListNetworksCmd.class, RETURNS_SELF);
        CreateNetworkCmd create = mock(CreateNetworkCmd.class, RETURNS_SELF);

        when(c.listNetworksCmd()).thenReturn(list);
        when(list.exec()).thenReturn(List.of());
        when(c.createNetworkCmd()).thenReturn(create);

        CreateNetworkTask.run(c, "backend", "bridge", Map.of(), true, true, mock(Logger.class));

        verify(create).withInternal(true);
        verify(create).withAttachable(true);
        verify(create).exec();
    }

    @Test
    void createsWhenMultipleNetworksNoneMatch() {
        DockerClient c = mock(DockerClient.class);
        ListNetworksCmd list = mock(ListNetworksCmd.class, RETURNS_SELF);
        CreateNetworkCmd create = mock(CreateNetworkCmd.class, RETURNS_SELF);
        Network a = mock(Network.class);
        Network b = mock(Network.class);

        when(c.listNetworksCmd()).thenReturn(list);
        when(list.exec()).thenReturn(List.of(a, b));
        when(a.getName()).thenReturn("foo");
        when(b.getName()).thenReturn("bar");
        when(c.createNetworkCmd()).thenReturn(create);

        CreateNetworkTask.run(c, "backend", "bridge", Map.of(), false, false, mock(Logger.class));

        verify(create).withName("backend");
        verify(create).exec();
    }

    @Test
    void noOpWhenMultipleNetworksOneMatches() {
        DockerClient c = mock(DockerClient.class);
        ListNetworksCmd list = mock(ListNetworksCmd.class, RETURNS_SELF);
        Network a = mock(Network.class);
        Network b = mock(Network.class);

        when(c.listNetworksCmd()).thenReturn(list);
        when(list.exec()).thenReturn(List.of(a, b));
        when(a.getName()).thenReturn("foo");
        when(b.getName()).thenReturn("backend");

        CreateNetworkTask.run(c, "backend", "bridge", Map.of(), false, false, mock(Logger.class));
        verify(c, never()).createNetworkCmd();
    }
}
