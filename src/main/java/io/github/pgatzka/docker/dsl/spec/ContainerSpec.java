package io.github.pgatzka.docker.dsl.spec;

import io.github.pgatzka.docker.dsl.Mounts;
import io.github.pgatzka.docker.dsl.PullPolicy;
import io.github.pgatzka.docker.dsl.waitable.Waitable;
import java.time.Duration;
import javax.inject.Inject;
import lombok.Getter;
import org.gradle.api.Action;
import org.gradle.api.Named;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;

/**
 * Spec describing a single Docker container the plugin will manage. Created by Gradle's
 * {@code ObjectFactory} once per name registered in {@code docker { containers { register(...) }}}.
 */
@Getter
public abstract class ContainerSpec implements Named {

    private final String name;

    /**
     * Mutable builder collecting volume and bind mounts declared via {@link #mounts(Action)}.
     *
     * @return the mounts builder
     */
    private final Mounts mounts = new Mounts();

    /**
     * Invoked by Gradle's {@code ObjectFactory} when a container is registered. Sets default
     * conventions: {@code containerName} mirrors {@code name}, {@code wait} defaults to
     * {@link Waitable#none()}, {@code waitTimeout} to 60s, {@code stopTimeout} to 10s, and
     * {@code pullPolicy} to {@link PullPolicy#IF_NOT_PRESENT}.
     *
     * @param name logical container name as registered in the DSL
     */
    @Inject
    public ContainerSpec(String name) {
        this.name = name;
        getContainerName().convention(name);
        // None by default: most images do not declare a HEALTHCHECK, so this avoids a
        // surprising fail-loud when the consumer forgets to opt into a strategy. Users
        // who want healthcheck-based readiness call `waitFor.set(WaitFor.healthcheck())`.
        getWait().convention(Waitable.none());
        getWaitTimeout().convention(Duration.ofSeconds(60));
        getStopTimeout().convention(Duration.ofSeconds(10));
        getPullPolicy().convention(PullPolicy.IF_NOT_PRESENT);
    }

    /**
     * Image reference, e.g. {@code postgres:16-alpine}. Required.
     *
     * @return the image property
     */
    public abstract Property<String> getImage();

    /**
     * Docker container name to use at {@code docker run} time. Defaults to the logical DSL name.
     *
     * @return the container-name property
     */
    public abstract Property<String> getContainerName();

    /**
     * Environment variables passed to the container as {@code -e KEY=VALUE} pairs.
     *
     * @return the environment property
     */
    public abstract MapProperty<String, String> getEnvironment();

    /**
     * Published ports: keys are <strong>host</strong> ports, values are <strong>container</strong>
     * ports. Example: {@code ports.set(mapOf(5432 to 5432))} maps host 5432 → container 5432.
     *
     * @return the ports property
     */
    public abstract MapProperty<Integer, Integer> getPorts();

    /**
     * Names of networks (declared in {@code docker.networks}) this container should attach to.
     *
     * @return the networks property
     */
    public abstract ListProperty<String> getNetworks();

    /**
     * Override the image's default {@code CMD}. Each entry becomes one argv element.
     *
     * @return the command property
     */
    public abstract ListProperty<String> getCommand();

    /**
     * Readiness strategy used by the generated {@code start<ContainerName>} task. Defaults to
     * {@link Waitable#none()}.
     *
     * @return the wait property
     */
    public abstract Property<Waitable> getWait();

    /**
     * Maximum time the start task waits for {@link #getWait()} to succeed before failing.
     * Defaults to 60 seconds.
     *
     * @return the wait-timeout property
     */
    public abstract Property<Duration> getWaitTimeout();

    /**
     * Grace period passed to {@code docker stop} before {@code SIGKILL}. Defaults to 10 seconds.
     *
     * @return the stop-timeout property
     */
    public abstract Property<Duration> getStopTimeout();

    /**
     * When the plugin should pull the image. Defaults to {@link PullPolicy#IF_NOT_PRESENT}.
     *
     * @return the pull-policy property
     */
    public abstract Property<PullPolicy> getPullPolicy();

    /**
     * Configure volume and bind mounts for this container.
     *
     * @param action configuration block applied to the shared {@link Mounts} builder
     */
    public void mounts(Action<? super Mounts> action) {
        action.execute(mounts);
    }
}
