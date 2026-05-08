package io.github.pgatzka.docker.dsl;

import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class VolumeSpecTest {

    @Test void hasDefaultsAndCarriesName() {
        var project = ProjectBuilder.builder().build();
        var spec = project.getObjects().newInstance(VolumeSpec.class, "codegen_data");

        assertThat(spec.getName()).isEqualTo("codegen_data");
        assertThat(spec.getDriver().get()).isEqualTo("local");
        assertThat(spec.getDriverOpts().get()).isEmpty();
        assertThat(spec.getLabels().get()).isEmpty();
    }
}
