package io.github.pgatzka.docker.dsl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.pgatzka.docker.dsl.mount.BindMount;
import io.github.pgatzka.docker.dsl.mount.VolumeMount;
import org.junit.jupiter.api.Test;

class MountsTest {

    @Test
    void collectsVolumeAndBindMounts() {
        Mounts m = new Mounts();
        m.volume("codegen_data", "/var/lib/postgresql");
        m.volume("scratch", "/tmp/scratch", true);
        m.bind("./sql", "/init");
        m.bind("./conf", "/etc/conf", true);

        assertThat(m.volumes())
                .containsExactly(
                        new VolumeMount("codegen_data", "/var/lib/postgresql", false),
                        new VolumeMount("scratch", "/tmp/scratch", true));
        assertThat(m.binds())
                .containsExactly(new BindMount("./sql", "/init", false), new BindMount("./conf", "/etc/conf", true));
    }

    @Test
    void volumeNamesAreReturned() {
        Mounts m = new Mounts();
        m.volume("a", "/a");
        m.volume("b", "/b");
        assertThat(m.referencedVolumeNames()).containsExactly("a", "b");
    }

    @Test
    void emptyMountsReturnsEmptyLists() {
        Mounts m = new Mounts();
        assertThat(m.volumes()).isEmpty();
        assertThat(m.binds()).isEmpty();
        assertThat(m.referencedVolumeNames()).isEmpty();
    }

    @Test
    void volumesSnapshotIsImmutableCopy() {
        // Captured snapshot must not reflect later mutations, and must reject in-place edits.
        Mounts m = new Mounts();
        m.volume("first", "/first");
        var snapshot = m.volumes();

        m.volume("second", "/second");

        assertThat(snapshot).hasSize(1).containsExactly(new VolumeMount("first", "/first", false));
        assertThatThrownBy(() -> snapshot.add(new VolumeMount("x", "/x", false)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void bindsSnapshotIsImmutableCopy() {
        Mounts m = new Mounts();
        m.bind("./first", "/first");
        var snapshot = m.binds();

        m.bind("./second", "/second");

        assertThat(snapshot).hasSize(1).containsExactly(new BindMount("./first", "/first", false));
        assertThatThrownBy(() -> snapshot.add(new BindMount("x", "/x", false)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void bindTwoArgDefaultsToReadWrite() {
        Mounts m = new Mounts();
        m.bind("./conf", "/etc/conf");
        assertThat(m.binds()).containsExactly(new BindMount("./conf", "/etc/conf", false));
    }

    @Test
    void volumeTwoArgDefaultsToReadWrite() {
        Mounts m = new Mounts();
        m.volume("data", "/var/lib");
        assertThat(m.volumes()).containsExactly(new VolumeMount("data", "/var/lib", false));
    }
}
