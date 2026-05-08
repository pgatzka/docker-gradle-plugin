package io.github.pgatzka.docker.internal;

import io.github.pgatzka.docker.dsl.DockerExtension;
import io.github.pgatzka.docker.dsl.NetworkSpec;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.gradle.api.GradleException;

public final class Validation {

    private Validation() {}

    public static void validate(DockerExtension ext) {
        Set<String> declaredVolumes = new HashSet<>();
        ext.getVolumes().forEach(v -> declaredVolumes.add(v.getName()));
        Set<String> declaredNetworks = new HashSet<>();
        ext.getNetworks().forEach((NetworkSpec n) -> declaredNetworks.add(n.getName()));

        ext.getContainers().forEach(c -> {
            for (var vm : c.getMounts().volumes()) {
                if (!declaredVolumes.contains(vm.volumeName())) {
                    throw new GradleException("Container " + c.getName() + " references undeclared volume "
                            + vm.volumeName() + ". Declare it in volumes { register(\""
                            + vm.volumeName() + "\") {} }.");
                }
            }
            for (String n : c.getNetworks().getOrElse(List.of())) {
                if (!declaredNetworks.contains(n)) {
                    throw new GradleException("Container " + c.getName() + " references undeclared network "
                            + n + ". Declare it in networks { register(\""
                            + n + "\") {} }.");
                }
            }
        });
    }
}
