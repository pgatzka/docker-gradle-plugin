package io.github.pgatzka.docker.dsl;

import io.github.pgatzka.docker.dsl.spec.ContainerSpec;
import io.github.pgatzka.docker.dsl.spec.NetworkSpec;
import io.github.pgatzka.docker.dsl.spec.VolumeSpec;
import org.gradle.api.NamedDomainObjectContainer;

/**
 * Root DSL surface registered as the {@code docker} extension on a Gradle project. Exposes the
 * named containers, volumes, and networks the plugin should manage.
 */
public interface DockerExtension {

    /**
     * Containers declared via {@code docker { containers { register("...") { ... } } }}.
     *
     * @return the container container, keyed by logical container name
     */
    NamedDomainObjectContainer<ContainerSpec> getContainers();

    /**
     * Volumes declared via {@code docker { volumes { register("...") { ... } } }}.
     *
     * @return the volume container, keyed by logical volume name
     */
    NamedDomainObjectContainer<VolumeSpec> getVolumes();

    /**
     * Networks declared via {@code docker { networks { register("...") { ... } } }}.
     *
     * @return the network container, keyed by logical network name
     */
    NamedDomainObjectContainer<NetworkSpec> getNetworks();
}
