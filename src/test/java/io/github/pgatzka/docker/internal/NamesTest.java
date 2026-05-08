package io.github.pgatzka.docker.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class NamesTest {

    @Test
    void capitalizesSimpleName() {
        assertThat(Names.toCamel("postgres")).isEqualTo("Postgres");
    }

    @Test
    void splitsSnakeCase() {
        assertThat(Names.toCamel("postgres_codegen")).isEqualTo("PostgresCodegen");
    }

    @Test
    void splitsKebabCase() {
        assertThat(Names.toCamel("postgres-codegen")).isEqualTo("PostgresCodegen");
    }

    @Test
    void mixesSeparators() {
        assertThat(Names.toCamel("a_b-c")).isEqualTo("ABC");
    }

    @Test
    void buildsTaskNames() {
        assertThat(Names.startTask("postgres_codegen")).isEqualTo("startPostgresCodegen");
        assertThat(Names.stopTask("postgres_codegen")).isEqualTo("stopPostgresCodegen");
        assertThat(Names.removeContainerTask("postgres_codegen")).isEqualTo("removePostgresCodegen");
        assertThat(Names.createVolumeTask("codegen_data")).isEqualTo("createCodegenData");
        assertThat(Names.removeVolumeTask("codegen_data")).isEqualTo("removeCodegenData");
        assertThat(Names.createNetworkTask("codegen_network")).isEqualTo("createCodegenNetwork");
        assertThat(Names.removeNetworkTask("codegen_network")).isEqualTo("removeCodegenNetwork");
    }

    @Test
    void rejectsInvalidSpecName() {
        assertThatThrownBy(() -> Names.toCamel("bad name"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bad name");
        assertThatThrownBy(() -> Names.toCamel("")).isInstanceOf(IllegalArgumentException.class);
    }
}
