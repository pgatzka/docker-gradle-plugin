package io.github.pgatzka.docker.dsl;

import io.github.pgatzka.docker.dsl.mount.BindMount;
import io.github.pgatzka.docker.dsl.mount.VolumeMount;

import java.util.ArrayList;
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

    /** @return an immutable snapshot of currently-registered volume mounts. */
    public List<VolumeMount> volumes() {
        return List.copyOf(volumes);
    }

    /** @return an immutable snapshot of currently-registered bind mounts. */
    public List<BindMount> binds() {
        return List.copyOf(binds);
    }

    public List<String> referencedVolumeNames() {
        return volumes.stream().map(VolumeMount::volumeName).toList();
    }

}
