package com.novamclabs.scripting;

import com.novamclabs.StarTeleport;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.Invocable;
import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * 脚本系统（JS 优先，通过 javax.script 反射加载）
 * Scripting system (JS first, via javax.script if available)
 */
public class ScriptingManager {
    private final StarTeleport plugin;
    private final File scriptDir;
    private ScriptEngine engine;

    public ScriptingManager(StarTeleport plugin) {
        this.plugin = plugin;
        this.scriptDir = new File(plugin.getDataFolder(), "scripts");
        if (!scriptDir.exists()) scriptDir.mkdirs();
        initEngine();
        loadBootstrap();
    }

    private void initEngine() {
        try {
            ScriptEngineManager mgr = new ScriptEngineManager();
            // Java 17 默认无 Nashorn，若服务器装了其他引擎（例如 GraalJS 插件）也可被发现
            this.engine = mgr.getEngineByName("nashorn");
            if (this.engine == null) this.engine = mgr.getEngineByName("javascript");
        } catch (Throwable ignored) {}
    }

    private void loadBootstrap() {
        if (engine == null) return;
        File bootstrap = new File(scriptDir, "teleport.js");
        if (!bootstrap.exists()) {
            try {
                String tpl = "// Teleport scripting (CN/EN)\n" +
                        "// 可选导出函数: onPreTeleport(ctx), onPostTeleport(ctx)\n" +
                        "// Optional functions: onPreTeleport(ctx), onPostTeleport(ctx)\n" +
                        "// ctx API: ctx.player(), ctx.target(), ctx.playSound(name,vol,pitch), ctx.title(title,subtitle), ctx.particle(type,count,dx,dy,dz,speed)\n" +
                        "\n" +
                        "function onPreTeleport(ctx){ /* 可以在此播放粒子等 */ }\n" +
                        "function onPostTeleport(ctx){ /* 到达后效果 */ }\n";
                Files.writeString(bootstrap.toPath(), tpl);
            } catch (Exception ignored) {}
        }
        try {
            engine.put("__plugin", plugin);
            engine.eval(Files.readString(bootstrap.toPath()));
        } catch (Throwable ignored) {}
    }

    public void callPre(Player player, Location target) {
        invoke("onPreTeleport", player, target);
    }

    public void callPost(Player player, Location target) {
        invoke("onPostTeleport", player, target);
    }

    private void invoke(String fn, Player player, Location target) {
        if (engine == null) return;
        try {
            Invocable inv = (Invocable) engine;
            Map<String, Object> ctx = new HashMap<>();
            ctx.put("player", player);
            ctx.put("target", target);
            Object api = new ScriptContext(plugin, player, target);
            inv.invokeFunction(fn, api);
        } catch (Throwable ignored) {}
    }

    /**
     * 脚本上下文 API | Script context API exposed to JS
     */
    public static class ScriptContext {
        private final StarTeleport plugin; private final Player player; private final Location target;
        public ScriptContext(StarTeleport plugin, Player player, Location target) { this.plugin = plugin; this.player = player; this.target = target; }
        public Player player() { return player; }
        public Location target() { return target; }
        public void playSound(String name, float vol, float pitch) {
            try {
                org.bukkit.Sound s = org.bukkit.Sound.valueOf(name);
                player.playSound(player.getLocation(), s, vol, pitch);
            } catch (Exception ignored) {}
        }
        public void title(String title, String sub) { player.sendTitle(title, sub, 10, 40, 10); }
        public void particle(String type, int count, double dx, double dy, double dz, double speed) {
            try {
                org.bukkit.Particle p = org.bukkit.Particle.valueOf(type);
                player.getWorld().spawnParticle(p, player.getLocation().add(0,1,0), count, dx, dy, dz, speed);
            } catch (Exception ignored) {}
        }
        // MythicMobs/MMOCore 技能桥 | MythicMobs/MMOCore skill bridge via reflection
        public void mythicSkill(String name) {
            try {
                Class<?> api = Class.forName("io.lumine.mythic.api.skills.SkillManager");
            } catch (Throwable ignored) {}
        }
        public void mmocoreSkill(String name) { /* 可在此扩展 */ }
    }
}
