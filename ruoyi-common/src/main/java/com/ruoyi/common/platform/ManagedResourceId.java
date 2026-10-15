package com.ruoyi.common.platform;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Stable platform identity for an engine-owned resource. The numeric ID is only
 * unique within its engine and resource type; it is not a Tinc runtime name.
 */
public final class ManagedResourceId {
    private static final Pattern PART = Pattern.compile("[a-z][a-z0-9-]{0,63}");

    private final String engineId;
    private final String resourceType;
    private final Long resourceId;

    private ManagedResourceId(String engineId, String resourceType, Long resourceId) {
        this.engineId = requirePart(engineId, "engineId");
        this.resourceType = requirePart(resourceType, "resourceType");
        if (resourceId == null || resourceId <= 0) {
            throw new IllegalArgumentException("resourceId 必须是正整数");
        }
        this.resourceId = resourceId;
    }

    public static ManagedResourceId of(String engineId, String resourceType, Long resourceId) {
        return new ManagedResourceId(engineId, resourceType, resourceId);
    }

    private static String requirePart(String value, String name) {
        if (value == null || !PART.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " 格式无效");
        }
        return value;
    }

    public String getEngineId() { return engineId; }
    public String getResourceType() { return resourceType; }
    public Long getResourceId() { return resourceId; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ManagedResourceId)) return false;
        ManagedResourceId that = (ManagedResourceId) other;
        return engineId.equals(that.engineId)
                && resourceType.equals(that.resourceType)
                && resourceId.equals(that.resourceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(engineId, resourceType, resourceId);
    }

    @Override
    public String toString() {
        return engineId + "/" + resourceType + "/" + resourceId;
    }
}
