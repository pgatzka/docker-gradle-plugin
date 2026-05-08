package io.github.pgatzka.docker.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.pgatzka.docker.dsl.spec.VolumeSpec;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

class VolumeSpecTest {

    @Test
    void hasDefaultsAndCarriesName() {
        var project = ProjectBuilder.builder().build();
        var spec = project.getObjects().newInstance(VolumeSpec.class, "codegen_data");

        assertThat(spec.getName()).isEqualTo("codegen_data");
        assertThat(spec.getDriver().get()).isEqualTo("local");
        assertThat(spec.getDriverOpts().get()).isEmpty();
        assertThat(spec.getLabels().get()).isEmpty();
    }
}
