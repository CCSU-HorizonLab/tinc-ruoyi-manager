package com.ruoyi.platform;

import com.ruoyi.common.platform.NetworkEngineDescriptor;
import com.ruoyi.common.platform.NetworkEngineProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/** Discovers Management providers without coupling engine choice to server placement. */
@Component
public class NetworkEngineRegistry {
    private final Map<String, NetworkEngineProvider> providers;
    private final List<NetworkEngineDescriptor> descriptors;

    public NetworkEngineRegistry(List<NetworkEngineProvider> installedProviders) {
        Map<String, NetworkEngineProvider> found = new TreeMap<>();
        for (NetworkEngineProvider provider : installedProviders) {
            if (provider == null || provider.descriptor() == null) {
                throw new IllegalStateException("Network engine provider descriptor 不能为空");
            }
            String id = provider.descriptor().getEngineId();
            if (found.putIfAbsent(id, provider) != null) {
                throw new IllegalStateException("重复的 Network engine ID: " + id);
            }
        }
        this.providers = Collections.unmodifiableMap(found);
        List<NetworkEngineDescriptor> metadata = new ArrayList<>();
        for (NetworkEngineProvider provider : found.values()) {
            metadata.add(provider.descriptor());
        }
        this.descriptors = Collections.unmodifiableList(metadata);
    }

    public List<NetworkEngineDescriptor> descriptors() { return descriptors; }

    public Optional<NetworkEngineProvider> find(String engineId) {
        if (engineId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(providers.get(engineId));
    }

    public NetworkEngineProvider require(String engineId) {
        return find(engineId).orElseThrow(
                () -> new IllegalArgumentException("未注册的网络引擎: " + engineId));
    }
}
