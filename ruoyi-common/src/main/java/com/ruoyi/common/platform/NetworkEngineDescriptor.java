package com.ruoyi.common.platform;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** Immutable, non-secret metadata for a Management-registered engine; not a server probe. */
public final class NetworkEngineDescriptor {
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9-]{0,63}");

    private final String engineId;
    private final String displayName;
    private final Set<String> capabilities;

    public NetworkEngineDescriptor(String engineId, String displayName, Set<String> capabilities) {
        this.engineId = requireId(engineId, "engineId");
        if (displayName == null || displayName.trim().isEmpty() || displayName.length() > 80) {
            throw new IllegalArgumentException("displayName 格式无效");
        }
        this.displayName = displayName.trim();
        if (capabilities == null) {
            throw new IllegalArgumentException("capabilities 不能为空");
        }
        Set<String> checked = new LinkedHashSet<>();
        for (String capability : capabilities) {
            checked.add(requireId(capability, "capability"));
        }
        this.capabilities = Collections.unmodifiableSet(checked);
    }

    private static String requireId(String value, String field) {
        if (value == null || !ID.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " 格式无效");
        }
        return value;
    }

    public String getEngineId() { return engineId; }
    public String getDisplayName() { return displayName; }
    public Set<String> getCapabilities() { return capabilities; }
}
