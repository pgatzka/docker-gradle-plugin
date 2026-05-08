package io.github.pgatzka.docker.task;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectVolumeResponse;
import com.github.dockerjava.api.command.ListVolumesCmd;
import com.github.dockerjava.api.command.ListVolumesResponse;
import com.github.dockerjava.api.command.RemoveVolumeCmd;
import java.util.List;
import org.gradle.api.logging.Logger;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RemoveVolumeTaskTest {

  @Test
  void removesWhenPresent() {
    DockerClient c = mock(DockerClient.class);
    ListVolumesCmd list = mock(ListVolumesCmd.class);
    ListVolumesResponse listResp = mock(ListVolumesResponse.class);
    InspectVolumeResponse existing = mock(InspectVolumeResponse.class);
    RemoveVolumeCmd rm = mock(RemoveVolumeCmd.class);

    when(c.listVolumesCmd()).thenReturn(list);
    when(list.exec()).thenReturn(listResp);
    when(listResp.getVolumes()).thenReturn(List.of(existing));
    when(existing.getName()).thenReturn("data");
    when(c.removeVolumeCmd("data")).thenReturn(rm);

    RemoveVolumeTask.run(c, "data", mock(Logger.class));
    verify(rm).exec();
  }

  @Test
  void noOpWhenAbsent() {
    DockerClient c = mock(DockerClient.class);
    ListVolumesCmd list = mock(ListVolumesCmd.class);
    ListVolumesResponse listResp = mock(ListVolumesResponse.class);
    when(c.listVolumesCmd()).thenReturn(list);
    when(list.exec()).thenReturn(listResp);
    when(listResp.getVolumes()).thenReturn(List.of());

    RemoveVolumeTask.run(c, "data", mock(Logger.class));
    verify(c, never()).removeVolumeCmd(anyString());
  }

}
