package io.github.pgatzka.docker;

import io.github.pgatzka.docker.dsl.DockerExtension;
import org.gradle.api.Task;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ContainerTaskGenerationTest {

    @Test void generatesContainerTasksAndAutoDependsOnVolumesAndNetworks() {
        var project = ProjectBuilder.builder().build();
        project.getPlugins().apply("io.github.pgatzka.docker");

        var ext = (DockerExtension) project.getExtensions().getByName("docker");
        ext.getVolumes().register("data");
        ext.getNetworks().register("backend");
        ext.getContainers().register("postgres", c -> {
            c.getImage().set("postgres:18-alpine");
            c.getNetworks().set(java.util.List.of("backend"));
            c.mounts(m -> m.volume("data", "/var/lib/postgresql"));
        });

        Task start = project.getTasks().getByName("startPostgres");
        assertThat(project.getTasks().findByName("stopPostgres")).isNotNull();
        assertThat(project.getTasks().findByName("removePostgres")).isNotNull();

        var depNames = start.getTaskDependencies().getDependencies(start).stream()
            .map(Task::getName).toList();
        assertThat(depNames).contains("createData", "createBackend");
    }

    @Test void registrationOrderIndependence() {
        // container declared BEFORE its volume/network — auto-dependsOn must still pick them up
        var project = ProjectBuilder.builder().build();
        project.getPlugins().apply("io.github.pgatzka.docker");

        var ext = (DockerExtension) project.getExtensions().getByName("docker");
        ext.getContainers().register("postgres", c -> {
            c.getImage().set("postgres:18-alpine");
            c.getNetworks().set(java.util.List.of("backend"));
            c.mounts(m -> m.volume("data", "/var/lib/postgresql"));
        });
        ext.getVolumes().register("data");
        ext.getNetworks().register("backend");

        Task start = project.getTasks().getByName("startPostgres");
        var depNames = start.getTaskDependencies().getDependencies(start).stream()
            .map(Task::getName).toList();
        assertThat(depNames).contains("createData", "createBackend");
    }
}
