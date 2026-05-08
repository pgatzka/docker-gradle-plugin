package io.github.pgatzka.docker;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.pgatzka.docker.dsl.DockerExtension;
import io.github.pgatzka.docker.dsl.NetworkSpec;
import io.github.pgatzka.docker.dsl.VolumeSpec;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

class DockerPluginExtensionTest {

    @Test
    void registersDockerExtensionWithThreeContainers() {
        var project = ProjectBuilder.builder().build();
        project.getPlugins().apply("io.github.pgatzka.docker");

        var ext = (DockerExtension) project.getExtensions().getByName("docker");
        ext.getContainers().register("postgres", c -> c.getImage().set("postgres:18-alpine"));
        ext.getVolumes().register("data");
        ext.getNetworks().register("backend");

        assertThat(ext.getContainers().getNames()).containsExactly("postgres");
        assertThat(ext.getContainers().getByName("postgres").getImage().get()).isEqualTo("postgres:18-alpine");
        assertThat(ext.getVolumes().getByName("data")).isInstanceOf(VolumeSpec.class);
        assertThat(ext.getNetworks().getByName("backend")).isInstanceOf(NetworkSpec.class);
    }
}
