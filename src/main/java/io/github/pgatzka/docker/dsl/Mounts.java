package io.github.pgatzka.docker.dsl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Mounts {

    private final List<VolumeMount> volumes = new ArrayList<>();

    private final List<BindMount> binds = new ArrayList<>();

    public void volume(String volumeName, String containerPath) {
        volume(volumeName, containerPath, false);
    }

    public void volume(String volumeName, String containerPath, boolean readOnly) {
        volumes.add(new VolumeMount(volumeName, containerPath, readOnly));
    }

    public void bind(String hostPath, String containerPath) {
        bind(hostPath, containerPath, false);
    }

    public void bind(String hostPath, String containerPath, boolean readOnly) {
        binds.add(new BindMount(hostPath, containerPath, readOnly));
    }

    public List<VolumeMount> volumes() {
        return Collections.unmodifiableList(volumes);
    }

    public List<BindMount> binds() {
        return Collections.unmodifiableList(binds);
    }

    public List<String> referencedVolumeNames() {
        return volumes.stream().map(VolumeMount::volumeName).toList();
    }

    public record VolumeMount(String volumeName, String containerPath, boolean readOnly) {}

    public record BindMount(String hostPath, String containerPath, boolean readOnly) {}
}
