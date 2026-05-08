package io.github.pgatzka.docker;

import io.github.pgatzka.docker.dsl.DockerExtension;
import io.github.pgatzka.docker.dsl.spec.ContainerSpec;
import io.github.pgatzka.docker.dsl.spec.NetworkSpec;
import io.github.pgatzka.docker.dsl.spec.VolumeSpec;
import javax.inject.Inject;
import lombok.Getter;
import org.gradle.api.NamedDomainObjectContainer;

/**
 * Concrete implementation of {@link DockerExtension} that Gradle decorates and instantiates.
 * The three {@link NamedDomainObjectContainer} arguments are constructed by
 * {@link DockerPlugin#apply(org.gradle.api.Project)} via {@code ObjectFactory.domainObjectContainer}
 * and injected here.
 */
@Getter
public abstract class DockerExtensionImpl implements DockerExtension {

    private final NamedDomainObjectContainer<ContainerSpec> containers;

    private final NamedDomainObjectContainer<VolumeSpec> volumes;

    private final NamedDomainObjectContainer<NetworkSpec> networks;

    /**
     * Invoked by Gradle's {@code ObjectFactory} during extension creation.
     *
     * @param containers container specs registered in {@code docker { containers { ... } }}
     * @param volumes volume specs registered in {@code docker { volumes { ... } }}
     * @param networks network specs registered in {@code docker { networks { ... } }}
     */
    @Inject
    public DockerExtensionImpl(
            NamedDomainObjectContainer<ContainerSpec> containers,
            NamedDomainObjectContainer<VolumeSpec> volumes,
            NamedDomainObjectContainer<NetworkSpec> networks) {
        this.containers = containers;
        this.volumes = volumes;
        this.networks = networks;
    }
}
