package com.novamclabs.novastorage;

import java.util.Map;
import java.util.UUID;

/**
 * NovaStorage 通用接口（中英）
 * Unified storage provider API for Nova ecosystem
 */
public interface StorageProvider {
    void init() throws Exception;
    void close();

    // Key-Value 示例：玩家设置
    void setPlayerSetting(UUID uuid, String key, String value);
    String getPlayerSetting(UUID uuid, String key, String def);

    // 地点存储示例：warps/home 统一结构
    void setLocation(String category, String id, Map<String, Object> data);
    Map<String, Object> getLocation(String category, String id);
    void deleteLocation(String category, String id);
}
