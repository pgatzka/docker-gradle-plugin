package io.github.pgatzka.docker;

import io.github.pgatzka.docker.dsl.ContainerSpec;
import io.github.pgatzka.docker.dsl.DockerExtension;
import io.github.pgatzka.docker.dsl.NetworkSpec;
import io.github.pgatzka.docker.dsl.VolumeSpec;
import io.github.pgatzka.docker.internal.Names;
import io.github.pgatzka.docker.internal.Validation;
import io.github.pgatzka.docker.service.DockerService;
import io.github.pgatzka.docker.task.*;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class DockerPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        DockerExtension ext = project.getExtensions()
                .create(
                        DockerExtension.class,
                        "docker",
                        DockerExtensionImpl.class,
                        project.container(ContainerSpec.class, name -> project.getObjects()
                                .newInstance(ContainerSpec.class, name)),
                        project.container(
                                VolumeSpec.class, name -> project.getObjects().newInstance(VolumeSpec.class, name)),
                        project.container(
                                NetworkSpec.class, name -> project.getObjects().newInstance(NetworkSpec.class, name)));

        project.getGradle().getSharedServices().registerIfAbsent("docker", DockerService.class, spec -> {});

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

        ext.getContainers().all(spec -> {
            project.getTasks().register(Names.startTask(spec.getName()), StartContainerTask.class, t -> {
                t.getContainerName().set(spec.getContainerName());
                t.getImage().set(spec.getImage());
                t.getEnvironment().set(spec.getEnvironment());
                t.getPorts().set(spec.getPorts());
                t.getNetworks().set(spec.getNetworks());
                t.getCommand().set(spec.getCommand());
                t.getVolumeMounts().set(project.provider(() -> spec.getMounts().volumes()));
                t.getBindMounts().set(project.provider(() -> spec.getMounts().binds()));
                t.getWaitFor().set(spec.getWaitFor());
                t.getWaitTimeout().set(spec.getWaitTimeout());
                t.getPullPolicy().set(spec.getPullPolicy());

                // Lazy auto-dependsOn for referenced volumes and networks.
                t.dependsOn(project.provider(() -> {
                    java.util.List<String> deps = new java.util.ArrayList<>();
                    for (var vm : spec.getMounts().volumes()) {
                        deps.add(Names.createVolumeTask(vm.volumeName()));
                    }
                    for (String n : spec.getNetworks().getOrElse(java.util.List.of())) {
                        deps.add(Names.createNetworkTask(n));
                    }
                    return deps;
                }));
            });

            project.getTasks().register(Names.stopTask(spec.getName()), StopContainerTask.class, t -> {
                t.getContainerName().set(spec.getContainerName());
                t.getStopTimeout().set(spec.getStopTimeout());
            });
            project.getTasks().register(Names.removeContainerTask(spec.getName()), RemoveContainerTask.class, t -> {
                t.getContainerName().set(spec.getContainerName());
            });
        });

        project.afterEvaluate(p -> Validation.validate(ext));
    }

    public abstract static class DockerExtensionImpl implements DockerExtension {

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

        @Override
        public org.gradle.api.NamedDomainObjectContainer<ContainerSpec> getContainers() {
            return containers;
        }

        @Override
        public org.gradle.api.NamedDomainObjectContainer<VolumeSpec> getVolumes() {
            return volumes;
        }

        @Override
        public org.gradle.api.NamedDomainObjectContainer<NetworkSpec> getNetworks() {
            return networks;
        }
    }
}
