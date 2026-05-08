package io.github.pgatzka.docker.internal;

public final class NotReadyException extends RuntimeException {

    public NotReadyException(String msg) {
        super(msg);
    }
}
