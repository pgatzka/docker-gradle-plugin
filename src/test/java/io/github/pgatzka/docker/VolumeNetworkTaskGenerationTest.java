package io.github.pgatzka.docker;

import io.github.pgatzka.docker.dsl.DockerExtension;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VolumeNetworkTaskGenerationTest {

  @Test
  void generatesCreateAndRemoveTasksPerVolumeAndNetwork() {
    var project = ProjectBuilder.builder().build();
    project.getPlugins().apply("io.github.pgatzka.docker");

    var ext = (DockerExtension) project.getExtensions().getByName("docker");
    ext.getVolumes().register("codegen_data");
    ext.getNetworks().register("codegen_network");

    assertThat(project.getTasks().findByName("createCodegenData")).isNotNull();
    assertThat(project.getTasks().findByName("removeCodegenData")).isNotNull();
    assertThat(project.getTasks().findByName("createCodegenNetwork")).isNotNull();
    assertThat(project.getTasks().findByName("removeCodegenNetwork")).isNotNull();

    assertThat(project.getTasks().getByName("createCodegenData").getGroup()).isEqualTo("docker");
  }

}
