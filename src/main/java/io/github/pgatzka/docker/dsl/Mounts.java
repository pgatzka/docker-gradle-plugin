package io.github.pgatzka.docker.dsl;

import io.github.pgatzka.docker.dsl.mount.BindMount;
import io.github.pgatzka.docker.dsl.mount.VolumeMount;
import java.util.ArrayList;
import java.util.List;

/**
 * Mutable builder for a container's volume and bind mounts. Configured inside a container spec via
 * {@code mounts { volume(...); bind(...) }} and flushed into immutable snapshots at task time.
 */
public class Mounts {

    private final List<VolumeMount> volumes = new ArrayList<>();

    private final List<BindMount> binds = new ArrayList<>();

    /** Default constructor for use by {@code ContainerSpec}; not intended for direct use. */
    public Mounts() {}

    /**
     * Register a read-write named-volume mount.
     *
     * @param volumeName name of a volume declared in {@code docker.volumes}
     * @param containerPath absolute path inside the container
     */
    public void volume(String volumeName, String containerPath) {
        volume(volumeName, containerPath, false);
    }

    /**
     * Register a named-volume mount.
     *
     * @param volumeName name of a volume declared in {@code docker.volumes}
     * @param containerPath absolute path inside the container
     * @param readOnly {@code true} to mount read-only
     */
    public void volume(String volumeName, String containerPath, boolean readOnly) {
        volumes.add(new VolumeMount(volumeName, containerPath, readOnly));
    }

    /**
     * Register a read-write host bind mount.
     *
     * @param hostPath absolute or project-relative path on the host
     * @param containerPath absolute path inside the container
     */
    public void bind(String hostPath, String containerPath) {
        bind(hostPath, containerPath, false);
    }

    /**
     * Register a host bind mount.
     *
     * @param hostPath absolute or project-relative path on the host
     * @param containerPath absolute path inside the container
     * @param readOnly {@code true} to mount read-only
     */
    public void bind(String hostPath, String containerPath, boolean readOnly) {
        binds.add(new BindMount(hostPath, containerPath, readOnly));
    }

    /**
     * Snapshot the registered volume mounts.
     *
     * @return an immutable snapshot of currently-registered volume mounts
     */
    public List<VolumeMount> volumes() {
        return List.copyOf(volumes);
    }

    /**
     * Snapshot the registered bind mounts.
     *
     * @return an immutable snapshot of currently-registered bind mounts
     */
    public List<BindMount> binds() {
        return List.copyOf(binds);
    }

    /**
     * Names of the volumes referenced by registered volume mounts, in declaration order. Useful for
     * cross-checking against {@code docker.volumes}.
     *
     * @return referenced volume names
     */
    public List<String> referencedVolumeNames() {
        return volumes.stream().map(VolumeMount::volumeName).toList();
    }
}
