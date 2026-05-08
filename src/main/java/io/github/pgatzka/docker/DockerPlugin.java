package io.github.pgatzka.docker;

import io.github.pgatzka.docker.dsl.ContainerSpec;
import io.github.pgatzka.docker.dsl.DockerExtension;
import io.github.pgatzka.docker.dsl.Mounts;
import io.github.pgatzka.docker.dsl.NetworkSpec;
import io.github.pgatzka.docker.dsl.VolumeSpec;
import io.github.pgatzka.docker.internal.Names;
import io.github.pgatzka.docker.internal.Validation;
import io.github.pgatzka.docker.service.DockerService;
import io.github.pgatzka.docker.task.container.RemoveContainerTask;
import io.github.pgatzka.docker.task.container.StartContainerTask;
import io.github.pgatzka.docker.task.container.StopContainerTask;
import io.github.pgatzka.docker.task.network.CreateNetworkTask;
import io.github.pgatzka.docker.task.network.RemoveNetworkTask;
import io.github.pgatzka.docker.task.volume.CreateVolumeTask;
import io.github.pgatzka.docker.task.volume.RemoveVolumeTask;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import javax.inject.Inject;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;

public class DockerPlugin implements Plugin<Project> {

    /** Plugin id used to scope the shared {@link DockerService} so it can't collide with other plugins. */
    static final String DOCKER_SERVICE_NAME = "io.github.pgatzka.docker.DockerService";

    @Override
    public void apply(Project project) {
        DockerExtension ext = project.getExtensions()
                .create(
                        DockerExtension.class,
                        "docker",
                        DockerExtensionImpl.class,
                        project.getObjects().domainObjectContainer(ContainerSpec.class, name -> project.getObjects()
                                .newInstance(ContainerSpec.class, name)),
                        project.getObjects().domainObjectContainer(VolumeSpec.class, name -> project.getObjects()
                                .newInstance(VolumeSpec.class, name)),
                        project.getObjects().domainObjectContainer(NetworkSpec.class, name -> project.getObjects()
                                .newInstance(NetworkSpec.class, name)));

        project.getGradle().getSharedServices().registerIfAbsent(DOCKER_SERVICE_NAME, DockerService.class, spec -> {});

        ext.getVolumes().all(spec -> {
            project.getTasks().register(Names.createVolumeTask(spec.getName()), CreateVolumeTask.class, task -> {
                task.setDescription("Creates Docker volume '" + spec.getName() + "'.");
                task.getVolumeName().set(spec.getName());
                task.getDriver().set(spec.getDriver());
                task.getDriverOpts().set(spec.getDriverOpts());
                task.getLabels().set(spec.getLabels());
            });
            project.getTasks().register(Names.removeVolumeTask(spec.getName()), RemoveVolumeTask.class, task -> {
                task.setDescription("Removes Docker volume '" + spec.getName() + "'.");
                task.getVolumeName().set(spec.getName());
            });
        });

        ext.getNetworks().all(spec -> {
            project.getTasks().register(Names.createNetworkTask(spec.getName()), CreateNetworkTask.class, task -> {
                task.setDescription("Creates Docker network '" + spec.getName() + "'.");
                task.getNetworkName().set(spec.getName());
                task.getDriver().set(spec.getDriver());
                task.getLabels().set(spec.getLabels());
                task.getInternal().set(spec.getInternal());
                task.getAttachable().set(spec.getAttachable());
            });
            project.getTasks().register(Names.removeNetworkTask(spec.getName()), RemoveNetworkTask.class, task -> {
                task.setDescription("Removes Docker network '" + spec.getName() + "'.");
                task.getNetworkName().set(spec.getName());
            });
        });

        ext.getContainers().all(spec -> {
            project.getTasks().register(Names.startTask(spec.getName()), StartContainerTask.class, task -> {
                task.setDescription("Starts Docker container '" + spec.getName() + "'.");
                task.getContainerName().set(spec.getContainerName());
                task.getImage().set(spec.getImage());
                task.getEnvironment().set(spec.getEnvironment());
                task.getPorts().set(spec.getPorts());
                task.getNetworks().set(spec.getNetworks());
                task.getCommand().set(spec.getCommand());
                task.getVolumeMounts()
                        .set(project.provider(() -> spec.getMounts().volumes()));
                task.getBindMounts().set(project.provider(() -> spec.getMounts().binds()));
                task.getWaitFor().set(spec.getWaitFor());
                task.getWaitTimeout().set(spec.getWaitTimeout());
                task.getPullPolicy().set(spec.getPullPolicy());

                // Lazy auto-dependsOn for referenced volumes and networks. Splitting the
                // Callable from the Provider declaration pins the type parameter so Sonar's
                // ECJ parser can resolve the overload (it doesn't propagate target-type info
                // backwards into the lambda the way javac does).
                Callable<List<String>> autoDepsCallable = () -> {
                    List<String> deps = new ArrayList<>();
                    for (Mounts.VolumeMount volumeMount : spec.getMounts().volumes()) {
                        deps.add(Names.createVolumeTask(volumeMount.volumeName()));
                    }
                    for (String networkName : spec.getNetworks().getOrElse(List.of())) {
                        deps.add(Names.createNetworkTask(networkName));
                    }
                    return deps;
                };
                Provider<List<String>> autoDeps = project.provider(autoDepsCallable);
                task.dependsOn(autoDeps);
            });

            project.getTasks().register(Names.stopTask(spec.getName()), StopContainerTask.class, task -> {
                task.setDescription("Stops Docker container '" + spec.getName() + "'.");
                task.getContainerName().set(spec.getContainerName());
                task.getStopTimeout().set(spec.getStopTimeout());
            });
            project.getTasks().register(Names.removeContainerTask(spec.getName()), RemoveContainerTask.class, task -> {
                task.setDescription("Removes Docker container '" + spec.getName() + "'.");
                task.getContainerName().set(spec.getContainerName());
            });
        });

        project.afterEvaluate(p -> Validation.validate(ext));
    }

    public abstract static class DockerExtensionImpl implements DockerExtension {

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

        @Override
        public NamedDomainObjectContainer<ContainerSpec> getContainers() {
            return containers;
        }

        @Override
        public NamedDomainObjectContainer<VolumeSpec> getVolumes() {
            return volumes;
        }

        @Override
        public NamedDomainObjectContainer<NetworkSpec> getNetworks() {
            return networks;
        }
    }
}
