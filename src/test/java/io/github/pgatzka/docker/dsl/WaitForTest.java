package io.github.pgatzka.docker.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WaitForTest {

    @Test
    void healthcheckIsSingleton() {
        assertThat(WaitFor.healthcheck()).isInstanceOf(WaitFor.Healthcheck.class);
    }

    @Test
    void logLineCarriesRegex() {
        WaitFor wf = WaitFor.logLine(".*ready.*");
        assertThat(wf).isInstanceOf(WaitFor.LogLine.class);
        assertThat(((WaitFor.LogLine) wf).regex()).isEqualTo(".*ready.*");
    }

    @Test
    void tcpPortCarriesPort() {
        WaitFor wf = WaitFor.tcpPort(5432);
        assertThat(wf).isInstanceOf(WaitFor.TcpPort.class);
        assertThat(((WaitFor.TcpPort) wf).port()).isEqualTo(5432);
    }

    @Test
    void noneIsSingleton() {
        assertThat(WaitFor.none()).isInstanceOf(WaitFor.None.class);
    }
}
