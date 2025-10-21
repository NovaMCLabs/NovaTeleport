package com.novamclabs.novastorage.jdbc;

import com.novamclabs.novastorage.api.StorageProvider;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 纯 JDBC SQL 存储实现（H2/MySQL/MariaDB/PostgreSQL）
 * Pure JDBC SQL storage implementation.
 */
public class SqlStorage implements StorageProvider {
    private final String jdbcUrl, user, pass;
    private Connection conn;

    public SqlStorage(String jdbcUrl, String user, String pass) {
        this.jdbcUrl = jdbcUrl; this.user = user; this.pass = pass;
    }

    @Override
    public void init() throws Exception {
        conn = DriverManager.getConnection(jdbcUrl, user, pass);
        // Using ANSI SQL compatible DDL if possible
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS ntp_settings (uuid VARCHAR(40), k VARCHAR(64), v TEXT, PRIMARY KEY(uuid,k))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS ntp_locations (category VARCHAR(32), id VARCHAR(64), v TEXT, PRIMARY KEY(category,id))");
        }
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
            ps.setString(1, uuid.toString()); ps.setString(2, key); ResultSet rs = ps.executeQuery(); if (rs.next()) return rs.getString(1);
        } catch (SQLException ignored) {}
        return def;
    }

    @Override
    public void setLocation(String category, String id, Map<String, Object> data) {
        String dump = mapToString(data);
        try (PreparedStatement ps = conn.prepareStatement("MERGE INTO ntp_locations (category,id,v) KEY(category,id) VALUES (?,?,?)")) {
            ps.setString(1, category); ps.setString(2, id); ps.setString(3, dump); ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    @Override
    public Map<String, Object> getLocation(String category, String id) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT v FROM ntp_locations WHERE category=? AND id=?")) {
            ps.setString(1, category); ps.setString(2, id); ResultSet rs = ps.executeQuery();
            if (rs.next()) return stringToMap(rs.getString(1));
        } catch (SQLException ignored) {}
        return new HashMap<>();
    }

    @Override
    public void deleteLocation(String category, String id) {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM ntp_locations WHERE category=? AND id=?")) {
            ps.setString(1, category); ps.setString(2, id); ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    // Simple YAML-like line format key: value for flat maps
    private String mapToString(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> e : map.entrySet()) {
            sb.append(e.getKey()).append(": ").append(String.valueOf(e.getValue())).append('\n');
        }
        return sb.toString();
    }

    private Map<String, Object> stringToMap(String s) {
        Map<String, Object> m = new HashMap<>();
        if (s == null) return m;
        for (String line : s.split("\n")) {
            int i = line.indexOf(':');
            if (i <= 0) continue;
            String k = line.substring(0, i).trim();
            String v = line.substring(i + 1).trim();
            m.put(k, v);
        }
        return m;
    }
}
