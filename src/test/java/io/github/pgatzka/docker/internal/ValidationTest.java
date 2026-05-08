package io.github.pgatzka.docker.internal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.pgatzka.docker.dsl.DockerExtension;
import java.util.List;
import java.util.Map;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

class ValidationTest {

    private static DockerExtension newExtension() {
        Project project = ProjectBuilder.builder().build();
        project.getPlugins().apply("io.github.pgatzka.docker");
        return (DockerExtension) project.getExtensions().getByName("docker");
    }

    @Test
    void failsOnUnknownVolumeReference() {
        DockerExtension ext = newExtension();

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
        DockerExtension ext = newExtension();

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
        DockerExtension ext = newExtension();

        ext.getVolumes().register("data");
        ext.getNetworks().register("backend");
        ext.getContainers().register("c", c -> {
            c.getImage().set("img:1");
            c.getNetworks().set(List.of("backend"));
            c.mounts(m -> m.volume("data", "/data"));
        });

        Validation.validate(ext); // no throw
    }

    // ---------- requireImage ----------

    @Test
    void failsWhenContainerHasNoImage() {
        DockerExtension ext = newExtension();
        ext.getContainers().register("c", c -> {
            // no image.set(...)
        });

        assertThatThrownBy(() -> Validation.validate(ext))
                .isInstanceOf(GradleException.class)
                .hasMessageContaining("has no `image` set");
    }

    @Test
    void failsWhenContainerImageIsBlank() {
        DockerExtension ext = newExtension();
        ext.getContainers().register("c", c -> c.getImage().set(""));

        assertThatThrownBy(() -> Validation.validate(ext))
                .isInstanceOf(GradleException.class)
                .hasMessageContaining("has no `image` set");
    }

    // ---------- requireUniqueMountTargets ----------

    @Test
    void failsOnDuplicateVolumeMountTargets() {
        DockerExtension ext = newExtension();
        ext.getVolumes().register("a");
        ext.getVolumes().register("b");
        ext.getContainers().register("c", c -> {
            c.getImage().set("img:1");
            c.mounts(m -> {
                m.volume("a", "/data");
                m.volume("b", "/data");
            });
        });

        assertThatThrownBy(() -> Validation.validate(ext))
                .isInstanceOf(GradleException.class)
                .hasMessageContaining("duplicate mount target")
                .hasMessageContaining("/data");
    }

    @Test
    void failsOnDuplicateBindMountTargets() {
        DockerExtension ext = newExtension();
        ext.getContainers().register("c", c -> {
            c.getImage().set("img:1");
            c.mounts(m -> {
                m.bind("/host/one", "/etc/conf");
                m.bind("/host/two", "/etc/conf");
            });
        });

        assertThatThrownBy(() -> Validation.validate(ext))
                .isInstanceOf(GradleException.class)
                .hasMessageContaining("duplicate mount target")
                .hasMessageContaining("/etc/conf");
    }

    @Test
    void failsWhenVolumeAndBindShareMountTarget() {
        DockerExtension ext = newExtension();
        ext.getVolumes().register("data");
        ext.getContainers().register("c", c -> {
            c.getImage().set("img:1");
            c.mounts(m -> {
                m.volume("data", "/shared");
                m.bind("/host/path", "/shared");
            });
        });

        assertThatThrownBy(() -> Validation.validate(ext))
                .isInstanceOf(GradleException.class)
                .hasMessageContaining("duplicate mount target")
                .hasMessageContaining("/shared");
    }

    // ---------- requireUniqueContainerName ----------

    @Test
    void passesWhenTwoContainersUseDefaultNames() {
        DockerExtension ext = newExtension();
        ext.getContainers().register("alpha", c -> c.getImage().set("img:1"));
        ext.getContainers().register("beta", c -> c.getImage().set("img:1"));

        Validation.validate(ext); // no throw
    }

    @Test
    void failsWhenTwoContainersShareExplicitContainerName() {
        DockerExtension ext = newExtension();
        ext.getContainers().register("alpha", c -> {
            c.getImage().set("img:1");
            c.getContainerName().set("foo");
        });
        ext.getContainers().register("beta", c -> {
            c.getImage().set("img:1");
            c.getContainerName().set("foo");
        });

        assertThatThrownBy(() -> Validation.validate(ext))
                .isInstanceOf(GradleException.class)
                .hasMessageContaining("share the daemon-side name 'foo'");
    }

    @Test
    void failsWhenExplicitNameCollidesWithAnotherSpecsDefault() {
        DockerExtension ext = newExtension();
        // Default daemon name = spec name "alpha".
        ext.getContainers().register("alpha", c -> c.getImage().set("img:1"));
        // Explicit containerName collides with alpha's default.
        ext.getContainers().register("beta", c -> {
            c.getImage().set("img:1");
            c.getContainerName().set("alpha");
        });

        assertThatThrownBy(() -> Validation.validate(ext))
                .isInstanceOf(GradleException.class)
                .hasMessageContaining("share the daemon-side name 'alpha'");
    }

    // ---------- requireUniqueHostPorts ----------

    @Test
    void passesWhenContainersUseDistinctHostPorts() {
        DockerExtension ext = newExtension();
        ext.getContainers().register("alpha", c -> {
            c.getImage().set("img:1");
            c.getPorts().set(Map.of(5432, 5432));
        });
        ext.getContainers().register("beta", c -> {
            c.getImage().set("img:1");
            c.getPorts().set(Map.of(5433, 5432));
        });

        Validation.validate(ext); // no throw
    }

    @Test
    void failsWhenContainersShareHostPort() {
        DockerExtension ext = newExtension();
        ext.getContainers().register("alpha", c -> {
            c.getImage().set("img:1");
            c.getPorts().set(Map.of(5432, 5432));
        });
        ext.getContainers().register("beta", c -> {
            c.getImage().set("img:1");
            c.getPorts().set(Map.of(5432, 6000));
        });

        assertThatThrownBy(() -> Validation.validate(ext))
                .isInstanceOf(GradleException.class)
                .hasMessageContaining("both publish host port 5432");
    }

    @Test
    void passesWhenSingleContainerHasMultipleDistinctHostPorts() {
        DockerExtension ext = newExtension();
        ext.getContainers().register("alpha", c -> {
            c.getImage().set("img:1");
            c.getPorts().set(Map.of(8080, 80, 8443, 443));
        });

        Validation.validate(ext); // no throw
    }
}
