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

class HelloWorldFunctionalTest {

    @TempDir
    Path projectDir;

    private static void copyResource(String name, Path dest) throws IOException {
        Path src = Paths.get("src/functionalTest/resources").resolve(name).toAbsolutePath();
        try (var s = Files.walk(src)) {
            s.forEach(p -> {
                try {
                    Path target = dest.resolve(src.relativize(p));
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
    }

    @BeforeEach
    void daemonRequired() {
        assumeTrue(DockerAvailability.available(), "Docker daemon not reachable; skipping");
    }

    @Test
    void startCreatesContainerThenRemoveCleansUp() throws IOException {
        copyResource("hello-world", projectDir);

        try (DockerClient c = DockerAvailability.client()) {
            // Pre-cleanup just in case
            try {
                c.removeContainerCmd("hello").withForce(true).exec();
            } catch (Exception ignored) {
            }

            GradleRunner.create()
                    .withProjectDir(projectDir.toFile())
                    .withPluginClasspath()
                    .withArguments("startHello", "--info", "--stacktrace")
                    .build();

            boolean exists = c.listContainersCmd().withShowAll(true).exec().stream()
                    .anyMatch(ct -> {
                        for (String n : ct.getNames()) if (("/hello").equals(n)) return true;
                        return false;
                    });
            assertThat(exists).isTrue();

            GradleRunner.create()
                    .withProjectDir(projectDir.toFile())
                    .withPluginClasspath()
                    .withArguments("removeHello", "--info")
                    .build();

            boolean stillExists = c.listContainersCmd().withShowAll(true).exec().stream()
                    .anyMatch(ct -> {
                        for (String n : ct.getNames()) if (("/hello").equals(n)) return true;
                        return false;
                    });
            assertThat(stillExists).isFalse();
        }
    }
}
