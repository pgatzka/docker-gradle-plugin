package io.github.pgatzka.docker.internal;

import java.util.regex.Pattern;

public final class Names {

    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9_-]+");

    private Names() {}

    public static String toCamel(String specName) {
        if (specName == null || !VALID.matcher(specName).matches()) {
            throw new IllegalArgumentException("Invalid spec name '" + specName + "'. Allowed: [A-Za-z0-9_-]+");
        }
        StringBuilder sb = new StringBuilder(specName.length());
        boolean upper = true;
        for (int i = 0; i < specName.length(); i++) {
            char c = specName.charAt(i);
            if (c == '_' || c == '-') {
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

    public static String startTask(String n) {
        return TaskType.START.name().toLowerCase() + toCamel(n);
    }

    public static String stopTask(String n) {
        return TaskType.STOP.name().toLowerCase() + toCamel(n);
    }

    public static String removeContainerTask(String n) {
        return TaskType.REMOVE.name().toLowerCase() + toCamel(n);
    }

    public static String createVolumeTask(String n) {
        return TaskType.CREATE.name().toLowerCase() + toCamel(n);
    }

    public static String removeVolumeTask(String n) {
        return TaskType.REMOVE.name().toLowerCase() + toCamel(n);
    }

    public static String createNetworkTask(String n) {
        return TaskType.CREATE.name().toLowerCase() + toCamel(n);
    }

    public static String removeNetworkTask(String n) {
        return TaskType.REMOVE.name().toLowerCase() + toCamel(n);
    }
}
