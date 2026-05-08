package io.github.pgatzka.docker.dsl.spec;

import javax.inject.Inject;
import lombok.Getter;
import org.gradle.api.Named;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;

/**
 * Spec describing a Docker network the plugin will create and manage. Instantiated by Gradle's
 * {@code ObjectFactory} for each name registered in {@code docker { networks { register(...) }}}.
 */
@Getter
public abstract class NetworkSpec implements Named {

    private final String name;

    /**
     * Invoked by Gradle's {@code ObjectFactory} when a network is registered. Defaults
     * {@code driver} to {@code "bridge"} and both {@code internal} and {@code attachable} to
     * {@code false}.
     *
     * @param name logical network name as registered in the DSL
     */
    @Inject
    public NetworkSpec(String name) {
        this.name = name;
        getDriver().convention("bridge");
        getInternal().convention(false);
        getAttachable().convention(false);
    }

    /**
     * Network driver passed to {@code docker network create --driver}. Defaults to
     * {@code "bridge"}.
     *
     * @return the driver property
     */
    public abstract Property<String> getDriver();

    /**
     * Labels applied to the network as {@code --label KEY=VALUE} pairs.
     *
     * @return the labels property
     */
    public abstract MapProperty<String, String> getLabels();

    /**
     * Whether the network is {@code --internal} (no outbound connectivity). Defaults to
     * {@code false}.
     *
     * @return the internal property
     */
    public abstract Property<Boolean> getInternal();

    /**
     * Whether non-service containers may attach to the network ({@code --attachable}). Defaults to
     * {@code false}.
     *
     * @return the attachable property
     */
    public abstract Property<Boolean> getAttachable();
}
