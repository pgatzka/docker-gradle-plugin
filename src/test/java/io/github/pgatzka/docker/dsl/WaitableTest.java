package io.github.pgatzka.docker.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.pgatzka.docker.dsl.waitable.*;
import org.junit.jupiter.api.Test;

class WaitableTest {

    @Test
    void healthcheckIsSingleton() {
        assertThat(Waitable.healthcheck()).isInstanceOf(Healthcheck.class);
    }

    @Test
    void logLineCarriesRegex() {
        Waitable wf = Waitable.logLine(".*ready.*");
        assertThat(wf).isInstanceOf(LogLine.class);
        assertThat(((LogLine) wf).regex()).isEqualTo(".*ready.*");
    }

    @Test
    void tcpPortCarriesPort() {
        Waitable wf = Waitable.tcpPort(5432);
        assertThat(wf).isInstanceOf(TcpPort.class);
        assertThat(((TcpPort) wf).port()).isEqualTo(5432);
    }

    @Test
    void noneIsSingleton() {
        assertThat(Waitable.none()).isInstanceOf(None.class);
    }
}
