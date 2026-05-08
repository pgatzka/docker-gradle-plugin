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
        assertThat(Names.removeContainerTask("postgres_codegen")).isEqualTo("removeContainerPostgresCodegen");
        assertThat(Names.createVolumeTask("codegen_data")).isEqualTo("createVolumeCodegenData");
        assertThat(Names.removeVolumeTask("codegen_data")).isEqualTo("removeVolumeCodegenData");
        assertThat(Names.createNetworkTask("codegen_network")).isEqualTo("createNetworkCodegenNetwork");
        assertThat(Names.removeNetworkTask("codegen_network")).isEqualTo("removeNetworkCodegenNetwork");
    }

    @Test
    void taskNamesDisambiguateAcrossResourceTypes() {
        // Container, volume, and network with the same logical name no longer collide.
        assertThat(Names.removeContainerTask("foo")).isEqualTo("removeContainerFoo");
        assertThat(Names.removeVolumeTask("foo")).isEqualTo("removeVolumeFoo");
        assertThat(Names.removeNetworkTask("foo")).isEqualTo("removeNetworkFoo");
        assertThat(Names.createVolumeTask("foo")).isEqualTo("createVolumeFoo");
        assertThat(Names.createNetworkTask("foo")).isEqualTo("createNetworkFoo");
    }

    @Test
    void rejectsInvalidSpecName() {
        assertThatThrownBy(() -> Names.toCamel("bad name"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bad name");
        assertThatThrownBy(() -> Names.toCamel("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void splitsOnDotSeparator() {
        // `.` is now an allowed character in Docker resource names AND acts as a word boundary.
        assertThat(Names.toCamel("foo.bar")).isEqualTo("FooBar");
        assertThat(Names.toCamel("a.b.c")).isEqualTo("ABC");
    }

    @Test
    void rejectsNullSpecName() {
        assertThatThrownBy(() -> Names.toCamel(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null");
    }

    @Test
    void allSeparatorsCollapseToEmptyString() {
        // The VALID regex matches `___` so the call does not throw; the loop skips every char,
        // yielding an empty string. Locking this behavior so future regex tightening is intentional.
        assertThat(Names.toCamel("___")).isEmpty();
        assertThat(Names.toCamel("---")).isEmpty();
        assertThat(Names.toCamel("...")).isEmpty();
        assertThat(Names.toCamel("_-.")).isEmpty();
    }
}
