package io.github.pgatzka.docker.task;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateVolumeCmd;
import com.github.dockerjava.api.command.InspectVolumeResponse;
import com.github.dockerjava.api.command.ListVolumesCmd;
import com.github.dockerjava.api.command.ListVolumesResponse;
import java.util.List;
import java.util.Map;
import org.gradle.api.logging.Logger;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CreateVolumeTaskTest {

  @Test
  void createsVolumeWhenAbsent() {
    DockerClient c = mock(DockerClient.class);
    ListVolumesCmd list = mock(ListVolumesCmd.class);
    ListVolumesResponse listResp = mock(ListVolumesResponse.class);
    CreateVolumeCmd create = mock(CreateVolumeCmd.class, RETURNS_SELF);

    when(c.listVolumesCmd()).thenReturn(list);
    when(list.exec()).thenReturn(listResp);
    when(listResp.getVolumes()).thenReturn(List.of());
    when(c.createVolumeCmd()).thenReturn(create);

    CreateVolumeTask.run(c, "data", "local", Map.of("o", "1"), Map.of("k", "v"), mock(Logger.class));

    verify(create).withName("data");
    verify(create).withDriver("local");
    verify(create).withDriverOpts(Map.of("o", "1"));
    verify(create).withLabels(Map.of("k", "v"));
    verify(create).exec();
  }

  @Test
  void noOpWhenVolumeExists() {
    DockerClient c = mock(DockerClient.class);
    ListVolumesCmd list = mock(ListVolumesCmd.class);
    ListVolumesResponse listResp = mock(ListVolumesResponse.class);
    InspectVolumeResponse existing = mock(InspectVolumeResponse.class);

    when(c.listVolumesCmd()).thenReturn(list);
    when(list.exec()).thenReturn(listResp);
    when(listResp.getVolumes()).thenReturn(List.of(existing));
    when(existing.getName()).thenReturn("data");

    CreateVolumeTask.run(c, "data", "local", Map.of(), Map.of(), mock(Logger.class));

    verify(c, never()).createVolumeCmd();
  }

}
