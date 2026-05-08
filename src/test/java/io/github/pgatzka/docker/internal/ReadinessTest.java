package io.github.pgatzka.docker.internal;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.HealthState;
import com.github.dockerjava.api.command.InspectContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    Readiness.healthcheck(c, "cid", Duration.ofSeconds(1), Duration.ofMillis(10));
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

    assertThatThrownBy(() ->
        Readiness.healthcheck(c, "cid", Duration.ofMillis(50), Duration.ofMillis(10)))
        .isInstanceOf(Readiness.NotReadyException.class)
        .hasMessageContaining("not healthy within");
  }

  @Test
  void tcpPortReturnsWhenSocketAccepts() throws IOException {
    try (ServerSocket s = new ServerSocket(0)) {
      int port = s.getLocalPort();
      Readiness.tcpPort("127.0.0.1", port, Duration.ofSeconds(1), Duration.ofMillis(10));
    }
  }

  @Test
  void tcpPortTimesOutWhenNothingListening() {
    assertThatThrownBy(() ->
        Readiness.tcpPort("127.0.0.1", 1, Duration.ofMillis(50), Duration.ofMillis(10)))
        .isInstanceOf(Readiness.NotReadyException.class)
        .hasMessageContaining("TCP");
  }

  @Test
  void logLineMatchesRegexInStream() {
    boolean matched = Readiness.logLineMatch(
        "starting up\nready to accept connections\nidle\n",
        ".*ready.*");
    assertThat(matched).isTrue();
  }

  @Test
  void logLineDoesNotMatchUnrelatedText() {
    boolean matched = Readiness.logLineMatch("idle\nidle\n", ".*ready.*");
    assertThat(matched).isFalse();
  }

}
