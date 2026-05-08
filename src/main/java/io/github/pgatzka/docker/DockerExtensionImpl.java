package io.github.pgatzka.docker;

import io.github.pgatzka.docker.dsl.DockerExtension;
import io.github.pgatzka.docker.dsl.spec.ContainerSpec;
import io.github.pgatzka.docker.dsl.spec.NetworkSpec;
import io.github.pgatzka.docker.dsl.spec.VolumeSpec;
import lombok.Getter;
import org.gradle.api.NamedDomainObjectContainer;

import javax.inject.Inject;

@Getter
public abstract class DockerExtensionImpl implements DockerExtension {

    private final NamedDomainObjectContainer<ContainerSpec> containers;

    private final NamedDomainObjectContainer<VolumeSpec> volumes;

    private final NamedDomainObjectContainer<NetworkSpec> networks;

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
