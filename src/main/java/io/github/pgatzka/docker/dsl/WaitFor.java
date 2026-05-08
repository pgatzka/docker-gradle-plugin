package io.github.pgatzka.docker.dsl;

import java.io.Serializable;

public sealed interface WaitFor extends Serializable
        permits WaitFor.Healthcheck, WaitFor.LogLine, WaitFor.TcpPort, WaitFor.None {

    record Healthcheck() implements WaitFor {}
    record LogLine(String regex) implements WaitFor {}
    record TcpPort(int port) implements WaitFor {}
    record None() implements WaitFor {}

    static WaitFor healthcheck() { return new Healthcheck(); }
    static WaitFor logLine(String regex) { return new LogLine(regex); }
    static WaitFor tcpPort(int port) { return new TcpPort(port); }
    static WaitFor none() { return new None(); }
}
