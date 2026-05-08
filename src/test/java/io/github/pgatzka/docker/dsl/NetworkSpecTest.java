package io.github.pgatzka.docker.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.pgatzka.docker.dsl.spec.NetworkSpec;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

class NetworkSpecTest {

    @Test
    void hasDefaultsAndCarriesName() {
        var project = ProjectBuilder.builder().build();
        var spec = project.getObjects().newInstance(NetworkSpec.class, "codegen_network");

        assertThat(spec.getName()).isEqualTo("codegen_network");
        assertThat(spec.getDriver().get()).isEqualTo("bridge");
        assertThat(spec.getInternal().get()).isFalse();
        assertThat(spec.getAttachable().get()).isFalse();
        assertThat(spec.getLabels().get()).isEmpty();
    }
}
