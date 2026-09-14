package com.novamclabs.scripting;

import com.novamclabs.StarTeleport;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.File;
import java.nio.file.Files;
import java.util.logging.Level;

/**
 * 脚本系统（可选特性）。
 *
 * Java 15+ 已移除内置 Nashorn，因此本功能只有在服务器环境中存在 JS 脚本引擎
 * （例如服务端自带的 GraalJS，或额外提供的 nashorn/graal 引擎插件）时才会生效。
 * 引擎不可用时会在启动日志中明确说明，而不是静默失效。
 */
public class ScriptingManager {
    private final StarTeleport plugin;
    private final File scriptDir;
    private final File bootstrap;
    private ScriptEngine engine;

    public ScriptingManager(StarTeleport plugin) {
        this.plugin = plugin;
        this.scriptDir = new File(plugin.getDataFolder(), "scripts");
        if (!scriptDir.exists()) scriptDir.mkdirs();
        this.bootstrap = new File(scriptDir, "teleport.js");
        reload();
    }

    public void reload() {
        initEngine();
        // 无论引擎是否可用都写出模板，方便管理员看到可用的钩子与 API
        writeTemplateIfMissing();
        if (engine == null) return;
        try {
            engine.eval(Files.readString(bootstrap.toPath()));
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "[Scripting] Failed to load " + bootstrap.getName() + ": " + t.getMessage());
        }
    }

    private void initEngine() {
        try {
            ScriptEngineManager mgr = new ScriptEngineManager();
            this.engine = mgr.getEngineByName("nashorn");
            if (this.engine == null) this.engine = mgr.getEngineByName("Graal.js");
            if (this.engine == null) this.engine = mgr.getEngineByName("javascript");
        } catch (Throwable ignored) {
            this.engine = null;
        }
        if (this.engine == null) {
            plugin.getLogger().info("[Scripting] No JavaScript engine available on this server — "
                    + "scripts/teleport.js is disabled. Install a Nashorn/GraalJS engine provider to enable it.");
        } else {
            plugin.getLogger().info("[Scripting] Using engine: " + engine.getFactory().getEngineName());
        }
    }

    private void writeTemplateIfMissing() {
        if (bootstrap.exists()) return;
        try {
            String tpl = "// Teleport scripting (CN/EN)\n" +
                    "// 可选导出函数: onPreTeleport(ctx), onPostTeleport(ctx)\n" +
                    "// Optional functions: onPreTeleport(ctx), onPostTeleport(ctx)\n" +
                    "// ctx API: ctx.player(), ctx.target(), ctx.playSound(name,vol,pitch), ctx.title(title,subtitle), ctx.particle(type,count,dx,dy,dz,speed)\n" +
                    "\n" +
                    "function onPreTeleport(ctx){ /* 可以在此播放粒子等 */ }\n" +
                    "function onPostTeleport(ctx){ /* 到达后效果 */ }\n";
            Files.writeString(bootstrap.toPath(), tpl);
        } catch (Exception ignored) {
        }
    }

    public void callPre(Player player, Location target) {
        invoke("onPreTeleport", player, target);
    }

    public void callPost(Player player, Location target) {
        invoke("onPostTeleport", player, target);
    }

    private void invoke(String fn, Player player, Location target) {
        ScriptEngine e = this.engine;
        if (e == null || !(e instanceof Invocable inv)) return;
        try {
            inv.invokeFunction(fn, new ScriptContext(plugin, player, target));
        } catch (NoSuchMethodException ignored) {
            // 脚本未定义该钩子，属正常情况
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "[Scripting] " + fn + " threw: " + t.getMessage());
        }
    }

    public boolean isEnabled() {
        return engine != null;
    }

    /**
     * 脚本上下文 API | Script context API exposed to JS
     */
    public static class ScriptContext {
        private final StarTeleport plugin; private final Player player; private final Location target;
        public ScriptContext(StarTeleport plugin, Player player, Location target) { this.plugin = plugin; this.player = player; this.target = target; }
        public Player player() { return player; }
        public Location target() { return target; }
        // 与 TeleportUtil 的自有效果同一开关：总开关关闭时脚本也不得发出任何视效
        private boolean animationEnabled() {
            return plugin.getConfig().getBoolean("features.animation_enabled", true);
        }
        public void playSound(String name, float vol, float pitch) {
            if (!animationEnabled()) return;
            try {
                org.bukkit.Sound s = org.bukkit.Sound.valueOf(name);
                player.playSound(player.getLocation(), s, vol, pitch);
            } catch (Exception ignored) {}
        }
        public void title(String title, String sub) {
            if (!animationEnabled()) return;
            player.sendTitle(title, sub, 10, 40, 10);
        }
        public void particle(String type, int count, double dx, double dy, double dz, double speed) {
            if (!animationEnabled()) return;
            // 粒子常量在 1.20.5 被改名，脚本里两种命名都接受
            org.bukkit.Particle p = com.novamclabs.util.ParticleCompat.parse(type);
            if (p == null) return;
            player.getWorld().spawnParticle(p, player.getLocation().add(0,1,0), count, dx, dy, dz, speed);
        }
        // MythicMobs/MMOCore 技能桥 | MythicMobs/MMOCore skill bridge via reflection
        public void mythicSkill(String name) {
            try {
                Class<?> mythic = Class.forName("io.lumine.xikage.mythicmobs.MythicMobs");
                Object inst = mythic.getMethod("inst").invoke(null);
                Object apiHelper = inst.getClass().getMethod("getAPIHelper").invoke(inst);
                apiHelper.getClass().getMethod("castSkill", org.bukkit.entity.Entity.class, String.class).invoke(apiHelper, player, name);
            } catch (Throwable t) {
                try {
                    Class<?> mythic = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
                    Object inst = mythic.getMethod("inst").invoke(null);
                    Object apiHelper = inst.getClass().getMethod("getAPIHelper").invoke(inst);
                    apiHelper.getClass().getMethod("castSkill", org.bukkit.entity.Entity.class, String.class).invoke(apiHelper, player, name);
                } catch (Throwable ignored) {}
            }
        }
        public void mmocoreSkill(String name) {
            try {
                Class<?> api = Class.forName("net.Indyuce.mmocore.api.player.PlayerData");
                Object pd = api.getMethod("get", java.util.UUID.class).invoke(null, player.getUniqueId());
                pd.getClass().getMethod("castSkill", String.class).invoke(pd, name);
            } catch (Throwable ignored) {}
        }
    }
}
