package io.github.pgatzka.docker.dsl.waitable;

import java.io.Serializable;

public sealed interface Waitable extends Serializable
        permits Healthcheck, LogLine, TcpPort, None {

    static Waitable healthcheck() {
        return new Healthcheck();
    }

    static Waitable logLine(String regex) {
        return new LogLine(regex);
    }

    static Waitable tcpPort(int port) {
        return new TcpPort(port);
    }

    static Waitable none() {
        return new None();
    }

}
