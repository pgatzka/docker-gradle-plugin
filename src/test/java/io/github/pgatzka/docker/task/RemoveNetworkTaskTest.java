package io.github.pgatzka.docker.task;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ListNetworksCmd;
import com.github.dockerjava.api.command.RemoveNetworkCmd;
import com.github.dockerjava.api.model.Network;
import java.util.List;
import org.gradle.api.logging.Logger;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RemoveNetworkTaskTest {

  @Test
  void removesWhenPresent() {
    DockerClient c = mock(DockerClient.class);
    ListNetworksCmd list = mock(ListNetworksCmd.class, RETURNS_SELF);
    Network existing = mock(Network.class);
    RemoveNetworkCmd rm = mock(RemoveNetworkCmd.class);

    when(c.listNetworksCmd()).thenReturn(list);
    when(list.exec()).thenReturn(List.of(existing));
    when(existing.getName()).thenReturn("backend");
    when(existing.getId()).thenReturn("netid");
    when(c.removeNetworkCmd("netid")).thenReturn(rm);

    RemoveNetworkTask.run(c, "backend", mock(Logger.class));
    verify(rm).exec();
  }

  @Test
  void noOpWhenAbsent() {
    DockerClient c = mock(DockerClient.class);
    ListNetworksCmd list = mock(ListNetworksCmd.class, RETURNS_SELF);
    when(c.listNetworksCmd()).thenReturn(list);
    when(list.exec()).thenReturn(List.of());

    RemoveNetworkTask.run(c, "backend", mock(Logger.class));
    verify(c, never()).removeNetworkCmd(anyString());
  }

}
