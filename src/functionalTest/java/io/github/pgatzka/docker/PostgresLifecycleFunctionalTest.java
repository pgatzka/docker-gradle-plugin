package io.github.pgatzka.docker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.github.dockerjava.api.DockerClient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PostgresLifecycleFunctionalTest {

    @TempDir
    Path projectDir;

    @BeforeEach
    void daemonRequired() {
        assumeTrue(DockerAvailability.available(), "Docker daemon not reachable; skipping");
    }

    @Test
    void startStopRemoveAndVolumeOutlivesContainer() throws IOException {
        HelloWorldFunctionalTest.class.getName(); // keep import
        Path src = Paths.get("src/functionalTest/resources/postgres-lifecycle").toAbsolutePath();
        try (var s = Files.walk(src)) {
            s.forEach(p -> {
                try {
                    Path target = projectDir.resolve(src.relativize(p));
                    if (Files.isDirectory(p)) Files.createDirectories(target);
                    else {
                        Files.createDirectories(target.getParent());
                        Files.copy(p, target);
                    }
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            });
        }

        try (DockerClient c = DockerAvailability.client()) {
            // Pre-cleanup
            try {
                c.removeContainerCmd("pg_test").withForce(true).exec();
            } catch (Exception ignored) {
            }
            try {
                c.removeVolumeCmd("pg_data").exec();
            } catch (Exception ignored) {
            }
            try {
                c.listNetworksCmd().exec().stream()
                        .filter(n -> "pg_net".equals(n.getName()))
                        .findFirst()
                        .ifPresent(n -> c.removeNetworkCmd(n.getId()).exec());
            } catch (Exception ignored) {
            }

            GradleRunner.create()
                    .withProjectDir(projectDir.toFile())
                    .withPluginClasspath()
                    .withArguments("startPgTest", "--info", "--stacktrace")
                    .build();

            assertThat(c.inspectContainerCmd("pg_test").exec().getState().getRunning())
                    .isTrue();
            assertThat(c.listVolumesCmd().exec().getVolumes().stream().anyMatch(v -> "pg_data".equals(v.getName())))
                    .isTrue();

            GradleRunner.create()
                    .withProjectDir(projectDir.toFile())
                    .withPluginClasspath()
                    .withArguments("stopPgTest", "--info")
                    .build();

            assertThat(c.inspectContainerCmd("pg_test").exec().getState().getRunning())
                    .isFalse();

            GradleRunner.create()
                    .withProjectDir(projectDir.toFile())
                    .withPluginClasspath()
                    .withArguments("removePgTest", "--info")
                    .build();

            // Volume must still exist after removeContainer
            assertThat(c.listVolumesCmd().exec().getVolumes().stream().anyMatch(v -> "pg_data".equals(v.getName())))
                    .isTrue();

            // Final cleanup
            GradleRunner.create()
                    .withProjectDir(projectDir.toFile())
                    .withPluginClasspath()
                    .withArguments("removePgData", "removePgNet", "--info")
                    .build();
        }
    }
}
