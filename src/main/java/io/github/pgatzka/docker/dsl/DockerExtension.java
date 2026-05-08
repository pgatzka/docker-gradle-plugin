package io.github.pgatzka.docker.dsl;

import io.github.pgatzka.docker.dsl.spec.ContainerSpec;
import io.github.pgatzka.docker.dsl.spec.NetworkSpec;
import io.github.pgatzka.docker.dsl.spec.VolumeSpec;
import org.gradle.api.NamedDomainObjectContainer;

public interface DockerExtension {

    NamedDomainObjectContainer<ContainerSpec> getContainers();

    NamedDomainObjectContainer<VolumeSpec> getVolumes();

    NamedDomainObjectContainer<NetworkSpec> getNetworks();
}
