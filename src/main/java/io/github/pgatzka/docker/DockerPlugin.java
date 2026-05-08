package io.github.pgatzka.docker;

import io.github.pgatzka.docker.dsl.ContainerSpec;
import io.github.pgatzka.docker.dsl.DockerExtension;
import io.github.pgatzka.docker.dsl.NetworkSpec;
import io.github.pgatzka.docker.dsl.VolumeSpec;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class DockerPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        DockerExtension ext = project.getExtensions().create(
            DockerExtension.class, "docker", DockerExtensionImpl.class,
            project.container(ContainerSpec.class, name ->
                project.getObjects().newInstance(ContainerSpec.class, name)),
            project.container(VolumeSpec.class, name ->
                project.getObjects().newInstance(VolumeSpec.class, name)),
            project.container(NetworkSpec.class, name ->
                project.getObjects().newInstance(NetworkSpec.class, name))
        );
    }

    public static abstract class DockerExtensionImpl implements DockerExtension {
        private final org.gradle.api.NamedDomainObjectContainer<ContainerSpec> containers;
        private final org.gradle.api.NamedDomainObjectContainer<VolumeSpec> volumes;
        private final org.gradle.api.NamedDomainObjectContainer<NetworkSpec> networks;

        @javax.inject.Inject
        public DockerExtensionImpl(
                org.gradle.api.NamedDomainObjectContainer<ContainerSpec> containers,
                org.gradle.api.NamedDomainObjectContainer<VolumeSpec> volumes,
                org.gradle.api.NamedDomainObjectContainer<NetworkSpec> networks) {
            this.containers = containers;
            this.volumes = volumes;
            this.networks = networks;
        }

        @Override public org.gradle.api.NamedDomainObjectContainer<ContainerSpec> getContainers() { return containers; }
        @Override public org.gradle.api.NamedDomainObjectContainer<VolumeSpec>    getVolumes()    { return volumes; }
        @Override public org.gradle.api.NamedDomainObjectContainer<NetworkSpec>   getNetworks()   { return networks; }
    }
}
