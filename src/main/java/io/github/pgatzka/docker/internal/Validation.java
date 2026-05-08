package io.github.pgatzka.docker.internal;

import io.github.pgatzka.docker.dsl.ContainerSpec;
import io.github.pgatzka.docker.dsl.DockerExtension;
import io.github.pgatzka.docker.dsl.NetworkSpec;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.gradle.api.GradleException;

public final class Validation {

    private Validation() {}

    public static void validate(DockerExtension ext) {
        Set<String> declaredVolumes = new HashSet<>();
        ext.getVolumes().forEach(v -> declaredVolumes.add(v.getName()));
        Set<String> declaredNetworks = new HashSet<>();
        ext.getNetworks().forEach((NetworkSpec n) -> declaredNetworks.add(n.getName()));

        // Track container-name collisions on the daemon and host-port collisions across containers.
        Map<String, String> containerNameOwner = new HashMap<>();
        Map<Integer, String> hostPortOwner = new HashMap<>();

        ext.getContainers().forEach(c -> {
            requireImage(c);
            requireUniqueMountTargets(c);
            requireDeclaredVolumes(c, declaredVolumes);
            requireDeclaredNetworks(c, declaredNetworks);
            requireUniqueContainerName(c, containerNameOwner);
            requireUniqueHostPorts(c, hostPortOwner);
        });
    }

    private static void requireImage(ContainerSpec c) {
        if (!c.getImage().isPresent() || c.getImage().get().isBlank()) {
            throw new GradleException(
                    "Container " + c.getName() + " has no `image` set. Add `image.set(\"…\")` to the spec.");
        }
    }

    private static void requireUniqueMountTargets(ContainerSpec c) {
        Set<String> seen = new HashSet<>();
        for (var vm : c.getMounts().volumes()) {
            if (!seen.add(vm.containerPath())) {
                throw new GradleException("Container " + c.getName()
                        + " has duplicate mount target " + vm.containerPath()
                        + "; each container path may only be mounted once.");
            }
        }
        for (var bm : c.getMounts().binds()) {
            if (!seen.add(bm.containerPath())) {
                throw new GradleException("Container " + c.getName()
                        + " has duplicate mount target " + bm.containerPath()
                        + "; each container path may only be mounted once.");
            }
        }
    }

    private static void requireDeclaredVolumes(ContainerSpec c, Set<String> declared) {
        for (var vm : c.getMounts().volumes()) {
            if (!declared.contains(vm.volumeName())) {
                throw new GradleException("Container " + c.getName() + " references undeclared volume "
                        + vm.volumeName() + ". Declare it in volumes { register(\""
                        + vm.volumeName() + "\") {} }.");
            }
        }
    }

    private static void requireDeclaredNetworks(ContainerSpec c, Set<String> declared) {
        for (String n : c.getNetworks().getOrElse(List.of())) {
            if (!declared.contains(n)) {
                throw new GradleException("Container " + c.getName() + " references undeclared network "
                        + n + ". Declare it in networks { register(\""
                        + n + "\") {} }.");
            }
        }
    }

    private static void requireUniqueContainerName(ContainerSpec c, Map<String, String> owner) {
        String containerName = c.getContainerName().getOrElse(c.getName());
        String existing = owner.put(containerName, c.getName());
        if (existing != null) {
            throw new GradleException("Containers " + existing + " and " + c.getName()
                    + " share the daemon-side name '" + containerName
                    + "'. Override `containerName.set(...)` on at least one to disambiguate.");
        }
    }

    private static void requireUniqueHostPorts(ContainerSpec c, Map<Integer, String> owner) {
        for (Integer hostPort : c.getPorts().getOrElse(Map.of()).keySet()) {
            String existing = owner.put(hostPort, c.getName());
            if (existing != null && !existing.equals(c.getName())) {
                throw new GradleException("Containers " + existing + " and " + c.getName()
                        + " both publish host port " + hostPort
                        + "; pick distinct host ports.");
            }
        }
    }
}
