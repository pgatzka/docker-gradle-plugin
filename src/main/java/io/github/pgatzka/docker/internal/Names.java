package io.github.pgatzka.docker.internal;

import java.util.regex.Pattern;

/**
 * Pure utility that derives Gradle task names from user-declared spec names. Spec names use
 * {@code [A-Za-z0-9._-]+} characters; each {@code _} / {@code -} / {@code .} becomes a word
 * boundary in the generated camelCase task name (e.g. {@code postgres_codegen} →
 * {@code startPostgresCodegen}).
 */
public final class Names {

    // Matches Docker resource name characters (letters, digits, _, -, .). Each separator
    // (`_`, `-`, `.`) becomes a word boundary in the camelCased task name.
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._-]+");

    private Names() {}

    /**
     * Convert a spec name to its camelCase form, treating {@code _}, {@code -}, and {@code .}
     * as word boundaries.
     *
     * @param specName the user-declared spec name
     * @return the camelCased form (empty string if the name contains only separators)
     * @throws IllegalArgumentException if {@code specName} is null or contains characters
     *     outside {@code [A-Za-z0-9._-]}
     */
    public static String toCamel(String specName) {
        if (specName == null || !VALID.matcher(specName).matches()) {
            throw new IllegalArgumentException("Invalid spec name '" + specName + "'. Allowed: [A-Za-z0-9._-]+");
        }
        StringBuilder sb = new StringBuilder(specName.length());
        boolean upper = true;
        for (int i = 0; i < specName.length(); i++) {
            char c = specName.charAt(i);
            if (c == '_' || c == '-' || c == '.') {
                upper = true;
                continue;
            }
            sb.append(upper ? Character.toUpperCase(c) : Character.toLowerCase(c));
            upper = false;
        }
        return sb.toString();
    }

    enum TaskType {
        START,
        STOP,
        REMOVE,
        CREATE
    }

    /**
     * @param specName the container spec name
     * @return the {@code startXxx} task name for the container
     * @throws IllegalArgumentException if {@code specName} is null or contains illegal chars
     */
    public static String startTask(String specName) {
        return TaskType.START.name().toLowerCase() + toCamel(specName);
    }

    /**
     * @param specName the container spec name
     * @return the {@code stopXxx} task name for the container
     * @throws IllegalArgumentException if {@code specName} is null or contains illegal chars
     */
    public static String stopTask(String specName) {
        return TaskType.STOP.name().toLowerCase() + toCamel(specName);
    }

    /**
     * @param specName the container spec name
     * @return the {@code removeContainerXxx} task name for the container
     * @throws IllegalArgumentException if {@code specName} is null or contains illegal chars
     */
    public static String removeContainerTask(String specName) {
        return TaskType.REMOVE.name().toLowerCase() + "Container" + toCamel(specName);
    }

    /**
     * @param specName the volume spec name
     * @return the {@code createVolumeXxx} task name for the volume
     * @throws IllegalArgumentException if {@code specName} is null or contains illegal chars
     */
    public static String createVolumeTask(String specName) {
        return TaskType.CREATE.name().toLowerCase() + "Volume" + toCamel(specName);
    }

    /**
     * @param specName the volume spec name
     * @return the {@code removeVolumeXxx} task name for the volume
     * @throws IllegalArgumentException if {@code specName} is null or contains illegal chars
     */
    public static String removeVolumeTask(String specName) {
        return TaskType.REMOVE.name().toLowerCase() + "Volume" + toCamel(specName);
    }

    /**
     * @param specName the network spec name
     * @return the {@code createNetworkXxx} task name for the network
     * @throws IllegalArgumentException if {@code specName} is null or contains illegal chars
     */
    public static String createNetworkTask(String specName) {
        return TaskType.CREATE.name().toLowerCase() + "Network" + toCamel(specName);
    }

    /**
     * @param specName the network spec name
     * @return the {@code removeNetworkXxx} task name for the network
     * @throws IllegalArgumentException if {@code specName} is null or contains illegal chars
     */
    public static String removeNetworkTask(String specName) {
        return TaskType.REMOVE.name().toLowerCase() + "Network" + toCamel(specName);
    }
}
