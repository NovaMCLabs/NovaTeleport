package com.novamclabs.novastorage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SQL 存储（H2/MySQL/MariaDB/PostgreSQL）基于 JDBC，外部需提供驱动
 * SQL storage via JDBC; Drivers must be provided by server (no compile dep)
 */
public class SqlStorageProvider implements StorageProvider {
    private final String jdbcUrl; private final String user; private final String pass;
    private Connection conn;

    public SqlStorageProvider(String jdbcUrl, String user, String pass) {
        this.jdbcUrl = jdbcUrl; this.user = user; this.pass = pass;
    }

    @Override
    public void init() throws Exception {
        conn = java.sql.DriverManager.getConnection(jdbcUrl, user, pass);
        conn.createStatement().executeUpdate("CREATE TABLE IF NOT EXISTS ntp_settings (uuid VARCHAR(40), k VARCHAR(64), v TEXT, PRIMARY KEY(uuid,k))");
        conn.createStatement().executeUpdate("CREATE TABLE IF NOT EXISTS ntp_locations (category VARCHAR(32), id VARCHAR(64), v TEXT, PRIMARY KEY(category,id))");
    }

    @Override
    public void close() {
        try { if (conn != null) conn.close(); } catch (SQLException ignored) {}
    }

    @Override
    public void setPlayerSetting(UUID uuid, String key, String value) {
        try (PreparedStatement ps = conn.prepareStatement("MERGE INTO ntp_settings (uuid,k,v) KEY(uuid,k) VALUES (?,?,?)")) {
            ps.setString(1, uuid.toString()); ps.setString(2, key); ps.setString(3, value); ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    @Override
    public String getPlayerSetting(UUID uuid, String key, String def) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT v FROM ntp_settings WHERE uuid=? AND k=?")) {
            ps.setString(1, uuid.toString()); ps.setString(2, key); ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString(1);
        } catch (SQLException ignored) {}
        return def;
    }

    @Override
    public void setLocation(String category, String id, Map<String, Object> data) {
        try (PreparedStatement ps = conn.prepareStatement("MERGE INTO ntp_locations (category,id,v) KEY(category,id) VALUES (?,?,?)")) {
            org.bukkit.configuration.file.YamlConfiguration y = new org.bukkit.configuration.file.YamlConfiguration();
            for (Map.Entry<String, Object> en : data.entrySet()) y.set(en.getKey(), en.getValue());
            String dump = y.saveToString();
            ps.setString(1, category); ps.setString(2, id); ps.setString(3, dump);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    @Override
    public Map<String, Object> getLocation(String category, String id) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT v FROM ntp_locations WHERE category=? AND id=?")) {
            ps.setString(1, category); ps.setString(2, id); ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String yaml = rs.getString(1);
                org.bukkit.configuration.file.YamlConfiguration y = new org.bukkit.configuration.file.YamlConfiguration();
                try { y.loadFromString(yaml);} catch (Exception ignored) {}
                Map<String,Object> map = new HashMap<>();
                for (String k: y.getKeys(false)) map.put(k, y.get(k));
                return map;
            }
        } catch (SQLException ignored) {}
        return new HashMap<>();
    }

    @Override
    public void deleteLocation(String category, String id) {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM ntp_locations WHERE category=? AND id=?")) {
            ps.setString(1, category); ps.setString(2, id); ps.executeUpdate();
        } catch (SQLException ignored) {}
    }
}
