package com.ruoyi.common.platform;

/**
 * Declares an engine's identity and capabilities to the management platform.
 * Engine-specific configuration and runtime operations belong to separate adapters.
 */
public interface NetworkEngineProvider {
    NetworkEngineDescriptor descriptor();
}
