package io.github.pgatzka.docker.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

class ContainerSpecTest {

    @Test
    void hasDefaultsAndCarriesName() {
        var project = ProjectBuilder.builder().build();
        var spec = project.getObjects().newInstance(ContainerSpec.class, "postgres_codegen");

        assertThat(spec.getName()).isEqualTo("postgres_codegen");
        assertThat(spec.getContainerName().get()).isEqualTo("postgres_codegen");
        assertThat(spec.getEnvironment().get()).isEmpty();
        assertThat(spec.getPorts().get()).isEmpty();
        assertThat(spec.getNetworks().get()).isEmpty();
        assertThat(spec.getCommand().get()).isEmpty();
        assertThat(spec.getWaitFor().get()).isInstanceOf(WaitFor.Healthcheck.class);
        assertThat(spec.getWaitTimeout().get()).isEqualTo(Duration.ofSeconds(60));
        assertThat(spec.getStopTimeout().get()).isEqualTo(Duration.ofSeconds(10));
        assertThat(spec.getPullPolicy().get()).isEqualTo(PullPolicy.IF_NOT_PRESENT);
    }

    @Test
    void mountsBlockCollectsEntries() {
        var project = ProjectBuilder.builder().build();
        var spec = project.getObjects().newInstance(ContainerSpec.class, "c");

        spec.mounts(m -> {
            m.volume("data", "/var/lib");
            m.bind("./sql", "/init", true);
        });

        assertThat(spec.getMounts().referencedVolumeNames()).containsExactly("data");
        assertThat(spec.getMounts().binds()).hasSize(1);
        assertThat(spec.getMounts().binds().get(0).readOnly()).isTrue();
    }

    @Test
    void valuesCanBeSet() {
        var project = ProjectBuilder.builder().build();
        var spec = project.getObjects().newInstance(ContainerSpec.class, "c");

        spec.getImage().set("postgres:18-alpine");
        spec.getEnvironment().set(Map.of("K", "V"));
        spec.getPorts().set(Map.of(5432, 5432));
        spec.getNetworks().set(List.of("backend"));
        spec.getWaitFor().set(WaitFor.tcpPort(5432));

        assertThat(spec.getImage().get()).isEqualTo("postgres:18-alpine");
        assertThat(spec.getEnvironment().get()).containsEntry("K", "V");
        assertThat(spec.getPorts().get()).containsEntry(5432, 5432);
        assertThat(spec.getNetworks().get()).containsExactly("backend");
        assertThat(spec.getWaitFor().get()).isEqualTo(WaitFor.tcpPort(5432));
    }
}
