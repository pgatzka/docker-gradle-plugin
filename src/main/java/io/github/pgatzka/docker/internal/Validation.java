package io.github.pgatzka.docker.internal;

import io.github.pgatzka.docker.dsl.DockerExtension;
import io.github.pgatzka.docker.dsl.mount.BindMount;
import io.github.pgatzka.docker.dsl.mount.VolumeMount;
import io.github.pgatzka.docker.dsl.spec.ContainerSpec;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.gradle.api.GradleException;

public final class Validation {

    private Validation() {}

    public static void validate(DockerExtension extension) {
        Set<String> declaredVolumes = new HashSet<>();
        extension.getVolumes().forEach(volume -> declaredVolumes.add(volume.getName()));
        Set<String> declaredNetworks = new HashSet<>();
        extension.getNetworks().forEach(network -> declaredNetworks.add(network.getName()));

        // Track container-name collisions on the daemon and host-port collisions across containers.
        Map<String, String> containerNameOwner = new HashMap<>();
        Map<Integer, String> hostPortOwner = new HashMap<>();

        extension.getContainers().forEach(container -> {
            requireImage(container);
            requireUniqueMountTargets(container);
            requireDeclaredVolumes(container, declaredVolumes);
            requireDeclaredNetworks(container, declaredNetworks);
            requireUniqueContainerName(container, containerNameOwner);
            requireUniqueHostPorts(container, hostPortOwner);
        });
    }

    private static void requireImage(ContainerSpec container) {
        if (!container.getImage().isPresent() || container.getImage().get().isBlank()) {
            throw new GradleException(
                    "Container " + container.getName() + " has no `image` set. Add `image.set(\"…\")` to the spec.");
        }
    }

    private static void requireUniqueMountTargets(ContainerSpec container) {
        Set<String> seenPaths = new HashSet<>();
        for (VolumeMount volumeMount : container.getMounts().volumes()) {
            if (!seenPaths.add(volumeMount.containerPath())) {
                throw new GradleException("Container " + container.getName()
                        + " has duplicate mount target " + volumeMount.containerPath()
                        + "; each container path may only be mounted once.");
            }
        }
        for (BindMount bindMount : container.getMounts().binds()) {
            if (!seenPaths.add(bindMount.containerPath())) {
                throw new GradleException("Container " + container.getName()
                        + " has duplicate mount target " + bindMount.containerPath()
                        + "; each container path may only be mounted once.");
            }
        }
    }

    private static void requireDeclaredVolumes(ContainerSpec container, Set<String> declared) {
        for (VolumeMount volumeMount : container.getMounts().volumes()) {
            if (!declared.contains(volumeMount.volumeName())) {
                throw new GradleException("Container " + container.getName() + " references undeclared volume "
                        + volumeMount.volumeName() + ". Declare it in volumes { register(\""
                        + volumeMount.volumeName() + "\") {} }.");
            }
        }
    }

    private static void requireDeclaredNetworks(ContainerSpec container, Set<String> declared) {
        for (String networkName : container.getNetworks().getOrElse(List.of())) {
            if (!declared.contains(networkName)) {
                throw new GradleException("Container " + container.getName() + " references undeclared network "
                        + networkName + ". Declare it in networks { register(\""
                        + networkName + "\") {} }.");
            }
        }
    }

    private static void requireUniqueContainerName(ContainerSpec container, Map<String, String> owner) {
        String daemonName = container.getContainerName().getOrElse(container.getName());
        String existingOwner = owner.put(daemonName, container.getName());
        if (existingOwner != null) {
            throw new GradleException("Containers " + existingOwner + " and " + container.getName()
                    + " share the daemon-side name '" + daemonName
                    + "'. Override `containerName.set(...)` on at least one to disambiguate.");
        }
    }

    private static void requireUniqueHostPorts(ContainerSpec container, Map<Integer, String> owner) {
        for (Integer hostPort : container.getPorts().getOrElse(Map.of()).keySet()) {
            String existingOwner = owner.put(hostPort, container.getName());
            if (existingOwner != null && !existingOwner.equals(container.getName())) {
                throw new GradleException("Containers " + existingOwner + " and " + container.getName()
                        + " both publish host port " + hostPort
                        + "; pick distinct host ports.");
            }
        }
    }
}
