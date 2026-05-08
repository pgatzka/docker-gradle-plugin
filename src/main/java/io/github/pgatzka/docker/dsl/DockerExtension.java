package io.github.pgatzka.docker.dsl;

import org.gradle.api.NamedDomainObjectContainer;

public interface DockerExtension {

    NamedDomainObjectContainer<ContainerSpec> getContainers();

    NamedDomainObjectContainer<VolumeSpec> getVolumes();

    NamedDomainObjectContainer<NetworkSpec> getNetworks();
}
