package com.ruoyi.tinc.platform;

import com.ruoyi.common.platform.NetworkEngineDescriptor;
import com.ruoyi.common.platform.NetworkEngineProvider;
import java.util.Arrays;
import java.util.LinkedHashSet;
import org.springframework.stereotype.Component;

/** Registers the existing Tinc implementation without changing its API or runtime routing. */
@Component
public class TincNetworkEngineProvider implements NetworkEngineProvider {
    private static final NetworkEngineDescriptor DESCRIPTOR = new NetworkEngineDescriptor(
            "tinc", "Tinc", new LinkedHashSet<>(Arrays.asList(
                    "overlay-network", "peer-key-exchange", "runtime-status")));

    @Override
    public NetworkEngineDescriptor descriptor() {
        return DESCRIPTOR;
    }
}
