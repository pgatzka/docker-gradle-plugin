package io.github.pgatzka.docker.dsl;

import java.time.Duration;
import javax.inject.Inject;

import lombok.Getter;
import org.gradle.api.Action;
import org.gradle.api.Named;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;

@Getter
public abstract class ContainerSpec implements Named {

    private final String name;

    private final Mounts mounts = new Mounts();

    @Inject
    public ContainerSpec(String name) {
        this.name = name;
        getContainerName().convention(name);
        // None by default: most images do not declare a HEALTHCHECK, so this avoids a
        // surprising fail-loud when the consumer forgets to opt into a strategy. Users
        // who want healthcheck-based readiness call `waitFor.set(WaitFor.healthcheck())`.
        getWaitFor().convention(WaitFor.none());
        getWaitTimeout().convention(Duration.ofSeconds(60));
        getStopTimeout().convention(Duration.ofSeconds(10));
        getPullPolicy().convention(PullPolicy.IF_NOT_PRESENT);
    }

    public abstract Property<String> getImage();

    public abstract Property<String> getContainerName();

    public abstract MapProperty<String, String> getEnvironment();

    /**
     * Published ports: keys are <strong>host</strong> ports, values are <strong>container</strong>
     * ports. Example: {@code ports.set(mapOf(5432 to 5432))} maps host 5432 → container 5432.
     */
    public abstract MapProperty<Integer, Integer> getPorts();

    public abstract ListProperty<String> getNetworks();

    public abstract ListProperty<String> getCommand();

    public abstract Property<WaitFor> getWaitFor();

    public abstract Property<Duration> getWaitTimeout();

    public abstract Property<Duration> getStopTimeout();

    public abstract Property<PullPolicy> getPullPolicy();

    public void mounts(Action<? super Mounts> action) {
        action.execute(mounts);
    }
}
