package io.github.pgatzka.docker;

import io.github.pgatzka.docker.dsl.ContainerSpec;
import io.github.pgatzka.docker.dsl.DockerExtension;
import io.github.pgatzka.docker.dsl.NetworkSpec;
import io.github.pgatzka.docker.dsl.VolumeSpec;
import io.github.pgatzka.docker.internal.Names;
import io.github.pgatzka.docker.service.DockerService;
import io.github.pgatzka.docker.task.CreateNetworkTask;
import io.github.pgatzka.docker.task.CreateVolumeTask;
import io.github.pgatzka.docker.task.RemoveNetworkTask;
import io.github.pgatzka.docker.task.RemoveVolumeTask;
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

        project.getGradle().getSharedServices()
            .registerIfAbsent("docker", DockerService.class, spec -> {});

        ext.getVolumes().all(spec -> {
            project.getTasks().register(Names.createVolumeTask(spec.getName()), CreateVolumeTask.class, t -> {
                t.getVolumeName().set(spec.getName());
                t.getDriver().set(spec.getDriver());
                t.getDriverOpts().set(spec.getDriverOpts());
                t.getLabels().set(spec.getLabels());
            });
            project.getTasks().register(Names.removeVolumeTask(spec.getName()), RemoveVolumeTask.class, t -> {
                t.getVolumeName().set(spec.getName());
            });
        });

        ext.getNetworks().all(spec -> {
            project.getTasks().register(Names.createNetworkTask(spec.getName()), CreateNetworkTask.class, t -> {
                t.getNetworkName().set(spec.getName());
                t.getDriver().set(spec.getDriver());
                t.getLabels().set(spec.getLabels());
                t.getInternal().set(spec.getInternal());
                t.getAttachable().set(spec.getAttachable());
            });
            project.getTasks().register(Names.removeNetworkTask(spec.getName()), RemoveNetworkTask.class, t -> {
                t.getNetworkName().set(spec.getName());
            });
        });
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
