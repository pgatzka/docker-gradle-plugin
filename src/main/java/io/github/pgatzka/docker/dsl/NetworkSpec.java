package io.github.pgatzka.docker.dsl;

import javax.inject.Inject;
import lombok.Getter;
import org.gradle.api.Named;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;

@Getter
public abstract class NetworkSpec implements Named {

    private final String name;

    @Inject
    public NetworkSpec(String name) {
        this.name = name;
        getDriver().convention("bridge");
        getInternal().convention(false);
        getAttachable().convention(false);
    }

    public abstract Property<String> getDriver();

    public abstract MapProperty<String, String> getLabels();

    public abstract Property<Boolean> getInternal();

    public abstract Property<Boolean> getAttachable();
}
