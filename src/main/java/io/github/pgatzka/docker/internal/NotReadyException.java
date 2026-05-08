package io.github.pgatzka.docker.internal;

/**
 * Thrown by {@link Readiness} when a container does not reach the requested ready state
 * within the configured timeout, or disappears mid-wait. Wrapped by the calling task into
 * a {@code GradleException}.
 */
public final class NotReadyException extends RuntimeException {

    /**
     * Construct a new exception describing why readiness was not reached.
     *
     * @param msg human-readable description of the unmet readiness condition
     */
    public NotReadyException(String msg) {
        super(msg);
    }
}
