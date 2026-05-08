package io.github.pgatzka.docker.dsl;

import org.gradle.api.Named;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

public abstract class VolumeSpec implements Named {

    private final String name;

    @Inject
    public VolumeSpec(String name) {
        this.name = name;
        getDriver().convention("local");
    }

    @Override public String getName() { return name; }

    public abstract Property<String> getDriver();
    public abstract MapProperty<String, String> getDriverOpts();
    public abstract MapProperty<String, String> getLabels();
}
