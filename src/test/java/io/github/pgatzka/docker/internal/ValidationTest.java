package io.github.pgatzka.docker.internal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.pgatzka.docker.dsl.DockerExtension;
import java.util.List;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

class ValidationTest {

    @Test
    void failsOnUnknownVolumeReference() {
        Project project = ProjectBuilder.builder().build();
        project.getPlugins().apply("io.github.pgatzka.docker");
        var ext = (DockerExtension) project.getExtensions().getByName("docker");

        ext.getContainers().register("c", c -> {
            c.getImage().set("img:1");
            c.mounts(m -> m.volume("missing", "/data"));
        });

        assertThatThrownBy(() -> Validation.validate(ext))
                .isInstanceOf(GradleException.class)
                .hasMessageContaining("undeclared volume")
                .hasMessageContaining("missing");
    }

    @Test
    void failsOnUnknownNetworkReference() {
        Project project = ProjectBuilder.builder().build();
        project.getPlugins().apply("io.github.pgatzka.docker");
        var ext = (DockerExtension) project.getExtensions().getByName("docker");

        ext.getContainers().register("c", c -> {
            c.getImage().set("img:1");
            c.getNetworks().set(List.of("ghost"));
        });

        assertThatThrownBy(() -> Validation.validate(ext))
                .isInstanceOf(GradleException.class)
                .hasMessageContaining("undeclared network")
                .hasMessageContaining("ghost");
    }

    @Test
    void passesWhenAllReferencesAreDeclared() {
        Project project = ProjectBuilder.builder().build();
        project.getPlugins().apply("io.github.pgatzka.docker");
        var ext = (DockerExtension) project.getExtensions().getByName("docker");

        ext.getVolumes().register("data");
        ext.getNetworks().register("backend");
        ext.getContainers().register("c", c -> {
            c.getImage().set("img:1");
            c.getNetworks().set(List.of("backend"));
            c.mounts(m -> m.volume("data", "/data"));
        });

        Validation.validate(ext); // no throw
    }
}
