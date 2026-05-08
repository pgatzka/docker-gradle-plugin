package io.github.pgatzka.docker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.PingCmd;
import com.github.dockerjava.api.command.VersionCmd;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.model.Version;
import org.gradle.api.GradleException;
import org.junit.jupiter.api.Test;

class DockerServiceTest {

    @Test
    void prechecksSucceedsWhenDaemonReachable() {
        DockerClient client = mock(DockerClient.class);
        PingCmd ping = mock(PingCmd.class);
        VersionCmd ver = mock(VersionCmd.class);
        Version version = mock(Version.class);

        when(client.pingCmd()).thenReturn(ping);
        when(client.versionCmd()).thenReturn(ver);
        when(ver.exec()).thenReturn(version);
        when(version.getVersion()).thenReturn("24.0.0");

        // does not throw
        String reportedVersion = DockerService.precheck(client);
        assertThat(reportedVersion).isEqualTo("24.0.0");
    }

    @Test
    void precheckFailsWithClearMessageWhenPingFails() {
        DockerClient client = mock(DockerClient.class);
        PingCmd ping = mock(PingCmd.class);
        when(client.pingCmd()).thenReturn(ping);
        when(ping.exec()).thenThrow(new DockerException("connection refused", 0));

        assertThatThrownBy(() -> DockerService.precheck(client))
                .isInstanceOf(GradleException.class)
                .hasMessageContaining("Docker daemon unreachable")
                .hasMessageContaining("Is Docker running?");
    }
}
