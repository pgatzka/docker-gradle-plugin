package io.github.pgatzka.docker.internal;

import java.util.regex.Pattern;

public final class Names {

    // Matches Docker resource name characters (letters, digits, _, -, .). Each separator
    // (`_`, `-`, `.`) becomes a word boundary in the camelCased task name.
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._-]+");

    private Names() {}

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

    public static String startTask(String specName) {
        return TaskType.START.name().toLowerCase() + toCamel(specName);
    }

    public static String stopTask(String specName) {
        return TaskType.STOP.name().toLowerCase() + toCamel(specName);
    }

    public static String removeContainerTask(String specName) {
        return TaskType.REMOVE.name().toLowerCase() + "Container" + toCamel(specName);
    }

    public static String createVolumeTask(String specName) {
        return TaskType.CREATE.name().toLowerCase() + "Volume" + toCamel(specName);
    }

    public static String removeVolumeTask(String specName) {
        return TaskType.REMOVE.name().toLowerCase() + "Volume" + toCamel(specName);
    }

    public static String createNetworkTask(String specName) {
        return TaskType.CREATE.name().toLowerCase() + "Network" + toCamel(specName);
    }

    public static String removeNetworkTask(String specName) {
        return TaskType.REMOVE.name().toLowerCase() + "Network" + toCamel(specName);
    }
}
