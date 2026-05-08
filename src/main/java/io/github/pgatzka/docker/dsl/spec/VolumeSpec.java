package io.github.pgatzka.docker.dsl.spec;

import javax.inject.Inject;
import lombok.Getter;
import org.gradle.api.Named;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;

/**
 * Spec describing a Docker named volume the plugin will create and manage. Instantiated by
 * Gradle's {@code ObjectFactory} for each name registered in
 * {@code docker { volumes { register(...) }}}.
 */
@Getter
public abstract class VolumeSpec implements Named {

    private final String name;

    /**
     * Invoked by Gradle's {@code ObjectFactory} when a volume is registered. Defaults
     * {@code driver} to {@code "local"}.
     *
     * @param name logical volume name as registered in the DSL
     */
    @Inject
    public VolumeSpec(String name) {
        this.name = name;
        getDriver().convention("local");
    }

    /**
     * Volume driver passed to {@code docker volume create --driver}. Defaults to {@code "local"}.
     *
     * @return the driver property
     */
    public abstract Property<String> getDriver();

    /**
     * Driver-specific options forwarded as {@code --opt KEY=VALUE} pairs.
     *
     * @return the driver-opts property
     */
    public abstract MapProperty<String, String> getDriverOpts();

    /**
     * Labels applied to the volume as {@code --label KEY=VALUE} pairs.
     *
     * @return the labels property
     */
    public abstract MapProperty<String, String> getLabels();
}
