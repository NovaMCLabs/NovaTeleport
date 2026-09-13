package com.novamclabs.novastorage.jdbc;

import com.novamclabs.novastorage.api.StorageProvider;

import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 纯 JDBC SQL 存储实现（H2/MySQL/MariaDB/PostgreSQL）。
 *
 * 使用可移植的 upsert 方式（先 UPDATE，未命中再 INSERT），
 * 不再依赖 H2 专有的 MERGE ... KEY() 语法，因此同一个实现可在多种数据库上运行。
 *
 * 说明：本模块（NovaStorage）目前未被 NovaTeleport 主插件使用，作为可复用的存储库保留。
 */
public class SqlStorage implements StorageProvider {
    private final String jdbcUrl, user, pass;
    private Connection conn;

    public SqlStorage(String jdbcUrl, String user, String pass) {
        this.jdbcUrl = jdbcUrl;
        this.user = user;
        this.pass = pass;
    }

    @Override
    public void init() throws Exception {
        conn = DriverManager.getConnection(jdbcUrl, user, pass);
        conn.setAutoCommit(true);
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS ntp_settings (uuid VARCHAR(40) NOT NULL, k VARCHAR(64) NOT NULL, v TEXT, PRIMARY KEY (uuid, k))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS ntp_locations (category VARCHAR(32) NOT NULL, id VARCHAR(64) NOT NULL, v TEXT, PRIMARY KEY (category, id))");
        }
    }

    @Override
    public void close() {
        try { if (conn != null) conn.close(); } catch (SQLException ignored) {}
    }

    @Override
    public void setPlayerSetting(UUID uuid, String key, String value) {
        upsert("SELECT v FROM ntp_settings WHERE uuid=? AND k=?",
                new Object[]{uuid.toString(), key},
                "UPDATE ntp_settings SET v=? WHERE uuid=? AND k=?",
                new Object[]{value, uuid.toString(), key},
                "INSERT INTO ntp_settings (uuid,k,v) VALUES (?,?,?)",
                new Object[]{uuid.toString(), key, value});
    }

    @Override
    public String getPlayerSetting(UUID uuid, String key, String def) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT v FROM ntp_settings WHERE uuid=? AND k=?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String v = rs.getString(1);
                    return v != null ? v : def;
                }
            }
        } catch (SQLException ignored) {}
        return def;
    }

    @Override
    public void setLocation(String category, String id, Map<String, Object> data) {
        String dump = Json.encode(data == null ? new LinkedHashMap<>() : data);
        upsert("SELECT v FROM ntp_locations WHERE category=? AND id=?",
                new Object[]{category, id},
                "UPDATE ntp_locations SET v=? WHERE category=? AND id=?",
                new Object[]{dump, category, id},
                "INSERT INTO ntp_locations (category,id,v) VALUES (?,?,?)",
                new Object[]{category, id, dump});
    }

    @Override
    public Map<String, Object> getLocation(String category, String id) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT v FROM ntp_locations WHERE category=? AND id=?")) {
            ps.setString(1, category);
            ps.setString(2, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Json.decode(rs.getString(1));
            }
        } catch (SQLException ignored) {}
        return new LinkedHashMap<>();
    }

    @Override
    public void deleteLocation(String category, String id) {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM ntp_locations WHERE category=? AND id=?")) {
            ps.setString(1, category);
            ps.setString(2, id);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    /** 可移植 upsert：先判断存在性，再决定 UPDATE 或 INSERT | portable upsert */
    private void upsert(String existsSql, Object[] existsArgs,
                        String updateSql, Object[] updateArgs,
                        String insertSql, Object[] insertArgs) {
        try {
            boolean exists;
            try (PreparedStatement ps = conn.prepareStatement(existsSql)) {
                bind(ps, existsArgs);
                try (ResultSet rs = ps.executeQuery()) {
                    exists = rs.next();
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(exists ? updateSql : insertSql)) {
                bind(ps, exists ? updateArgs : insertArgs);
                ps.executeUpdate();
            }
        } catch (SQLException ignored) {}
    }

    private static void bind(PreparedStatement ps, Object[] args) throws SQLException {
        for (int i = 0; i < args.length; i++) {
            ps.setObject(i + 1, args[i]);
        }
    }

    /**
     * 极简 JSON 编解码（扁平 map，值只支持字符串与数字）。
     * 之前的 `key: value` 文本格式无法区分数字与字符串，坐标读回后会变成字符串。
     */
    static final class Json {
        private Json() {}

        static String encode(Map<String, Object> map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Object> e : map.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append('"').append(escape(e.getKey())).append("\":");
                Object v = e.getValue();
                if (v instanceof Number) {
                    sb.append(v);
                } else {
                    sb.append('"').append(escape(String.valueOf(v))).append('"');
                }
            }
            return sb.append('}').toString();
        }

        static Map<String, Object> decode(String json) {
            Map<String, Object> out = new LinkedHashMap<>();
            if (json == null) return out;
            int i = json.indexOf('{');
            if (i < 0) return out;
            i++;
            int n = json.length();
            while (i < n) {
                while (i < n && Character.isWhitespace(json.charAt(i))) i++;
                if (i >= n || json.charAt(i) == '}') break;
                if (json.charAt(i) != '"') break;
                StringBuilder key = new StringBuilder();
                i = readString(json, i, key);
                if (i < 0) break;
                while (i < n && (Character.isWhitespace(json.charAt(i)) || json.charAt(i) == ':')) i++;
                if (i >= n) break;
                if (json.charAt(i) == '"') {
                    StringBuilder val = new StringBuilder();
                    i = readString(json, i, val);
                    if (i < 0) break;
                    out.put(key.toString(), val.toString());
                } else {
                    int start = i;
                    while (i < n && json.charAt(i) != ',' && json.charAt(i) != '}') i++;
                    String raw = json.substring(start, i).trim();
                    out.put(key.toString(), parseNumber(raw));
                }
                while (i < n && (Character.isWhitespace(json.charAt(i)) || json.charAt(i) == ',')) i++;
            }
            return out;
        }

        private static Object parseNumber(String raw) {
            try {
                if (raw.indexOf('.') < 0 && raw.indexOf('e') < 0 && raw.indexOf('E') < 0) {
                    return Long.parseLong(raw);
                }
                return Double.parseDouble(raw);
            } catch (NumberFormatException e) {
                return raw;
            }
        }

        private static int readString(String json, int start, StringBuilder out) {
            int i = start + 1;
            int n = json.length();
            while (i < n) {
                char c = json.charAt(i);
                if (c == '\\' && i + 1 < n) {
                    char next = json.charAt(i + 1);
                    switch (next) {
                        case 'n': out.append('\n'); break;
                        case 'r': out.append('\r'); break;
                        case 't': out.append('\t'); break;
                        default: out.append(next);
                    }
                    i += 2;
                    continue;
                }
                if (c == '"') return i + 1;
                out.append(c);
                i++;
            }
            return -1;
        }

        private static String escape(String s) {
            if (s == null) return "";
            StringBuilder sb = new StringBuilder(s.length());
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '"': sb.append("\\\""); break;
                    case '\\': sb.append("\\\\"); break;
                    case '\n': sb.append("\\n"); break;
                    case '\r': sb.append("\\r"); break;
                    case '\t': sb.append("\\t"); break;
                    default: sb.append(c);
                }
            }
            return sb.toString();
        }
    }
}
