package io.github.pgatzka.docker.dsl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MountsTest {

  @Test
  void collectsVolumeAndBindMounts() {
    Mounts m = new Mounts();
    m.volume("codegen_data", "/var/lib/postgresql");
    m.volume("scratch", "/tmp/scratch", true);
    m.bind("./sql", "/init");
    m.bind("./conf", "/etc/conf", true);

    assertThat(m.volumes()).containsExactly(
        new Mounts.VolumeMount("codegen_data", "/var/lib/postgresql", false),
        new Mounts.VolumeMount("scratch", "/tmp/scratch", true)
    );
    assertThat(m.binds()).containsExactly(
        new Mounts.BindMount("./sql", "/init", false),
        new Mounts.BindMount("./conf", "/etc/conf", true)
    );
  }

  @Test
  void volumeNamesAreReturned() {
    Mounts m = new Mounts();
    m.volume("a", "/a");
    m.volume("b", "/b");
    assertThat(m.referencedVolumeNames()).containsExactly("a", "b");
  }

}
