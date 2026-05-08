package io.github.pgatzka.docker.dsl;

import java.time.Duration;
import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.Named;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;

public abstract class ContainerSpec implements Named {

    private final String name;

    private final Mounts mounts = new Mounts();

    @Inject
    public ContainerSpec(String name) {
        this.name = name;
        getContainerName().convention(name);
        getWaitFor().convention(WaitFor.healthcheck());
        getWaitTimeout().convention(Duration.ofSeconds(60));
        getStopTimeout().convention(Duration.ofSeconds(10));
        getPullPolicy().convention(PullPolicy.IF_NOT_PRESENT);
    }

    @Override
    public String getName() {
        return name;
    }

    public abstract Property<String> getImage();

    public abstract Property<String> getContainerName();

    public abstract MapProperty<String, String> getEnvironment();

    public abstract MapProperty<Integer, Integer> getPorts();

    public abstract ListProperty<String> getNetworks();

    public abstract ListProperty<String> getCommand();

    public abstract Property<WaitFor> getWaitFor();

    public abstract Property<Duration> getWaitTimeout();

    public abstract Property<Duration> getStopTimeout();

    public abstract Property<PullPolicy> getPullPolicy();

    public Mounts getMounts() {
        return mounts;
    }

    public void mounts(Action<? super Mounts> action) {
        action.execute(mounts);
    }
}
