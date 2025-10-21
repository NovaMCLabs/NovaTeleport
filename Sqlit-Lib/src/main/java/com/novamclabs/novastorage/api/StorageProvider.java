package com.novamclabs.novastorage.api;

import java.util.Map;
import java.util.UUID;

/**
 * 存储接口（中英）Storage provider interface
 */
public interface StorageProvider {
    void init() throws Exception;
    void close();

    void setPlayerSetting(UUID uuid, String key, String value);
    String getPlayerSetting(UUID uuid, String key, String def);

    void setLocation(String category, String id, Map<String, Object> data);
    Map<String, Object> getLocation(String category, String id);
    void deleteLocation(String category, String id);
}
