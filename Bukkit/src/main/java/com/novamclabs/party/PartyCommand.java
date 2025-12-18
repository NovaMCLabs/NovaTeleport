package com.novamclabs.party;

import com.novamclabs.StarTeleport;
import com.novamclabs.party.adapter.PartyAdapter;
import com.novamclabs.party.adapter.PartyAdapterManager;
import com.novamclabs.util.TeleportUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 组队命令 /party
 * Party commands
 */
public class PartyCommand implements CommandExecutor {
    private final StarTeleport plugin;
    private final PartyManager manager;
    private final PartyAdapterManager externalAdapters;
    private final Map<UUID, Invite> invites = new ConcurrentHashMap<>();

    private static class Invite {
        UUID from;
        UUID to;
        long expireAt;
    }

    public PartyCommand(StarTeleport plugin, PartyManager manager, PartyAdapterManager externalAdapters) {
        this.plugin = plugin;
        this.manager = manager;
        this.externalAdapters = externalAdapters;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getLang().t("common.only_player"));
            return true;
        }
        Player p = (Player) sender;
        if (args.length == 0) {
            p.sendMessage(plugin.getLang().t("party.help"));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create" -> {
                if (manager.hasParty(p.getUniqueId())) {
                    p.sendMessage(plugin.getLang().t("party.already_in"));
                    return true;
                }
                manager.createParty(p);
                p.sendMessage(plugin.getLang().t("party.created"));
                return true;
            }
            case "invite" -> {
                if (!manager.isLeader(p.getUniqueId())) {
                    p.sendMessage(plugin.getLang().t("party.not_leader"));
                    return true;
                }
                if (args.length < 2) {
                    p.sendMessage(plugin.getLang().t("party.usage.invite"));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    p.sendMessage(plugin.getLang().t("common.no_online_player"));
                    return true;
                }
                Invite inv = new Invite();
                inv.from = p.getUniqueId();
                inv.to = target.getUniqueId();
                inv.expireAt = System.currentTimeMillis() + manager.getInviteExpireSeconds() * 1000L;
                invites.put(inv.to, inv);
                p.sendMessage(plugin.getLang().tr("party.invited.sender", "target", target.getName()));
                target.sendMessage(plugin.getLang().tr("party.invited.target", "sender", p.getName()));
                return true;
            }
            case "accept" -> {
                Invite inv = invites.remove(p.getUniqueId());
                if (inv == null || inv.expireAt < System.currentTimeMillis()) {
                    p.sendMessage(plugin.getLang().t("party.no_invite"));
                    return true;
                }
                PartyManager.Party party = manager.getParty(inv.from);
                if (party == null) {
                    p.sendMessage(plugin.getLang().t("party.not_found"));
                    return true;
                }
                if (!manager.addMember(party, p)) {
                    p.sendMessage(plugin.getLang().t("party.full"));
                    return true;
                }
                p.sendMessage(plugin.getLang().t("party.joined"));
                return true;
            }
            case "leave" -> {
                if (!manager.hasParty(p.getUniqueId())) {
                    p.sendMessage(plugin.getLang().t("party.no_party"));
                    return true;
                }
                manager.remove(p);
                p.sendMessage(plugin.getLang().t("party.left"));
                return true;
            }
            case "tp" -> {
                PartyManager.Party party = manager.getParty(p.getUniqueId());
                Set<UUID> targetMembers = new HashSet<>();
                boolean isLeader = false;

                if (party != null) {
                    isLeader = manager.isLeader(p.getUniqueId());
                    targetMembers.addAll(party.members);
                } else {
                    PartyAdapter active = externalAdapters != null ? externalAdapters.getActive() : null;
                    PartyAdapter.PartyInfo info = active != null ? active.getParty(p) : null;
                    if (info != null) {
                        isLeader = p.getUniqueId().equals(info.leader);
                        targetMembers.addAll(info.members);
                    }
                }

                if (!isLeader) {
                    p.sendMessage(plugin.getLang().t("party.not_leader"));
                    return true;
                }

                int delay = manager.getTeleportDelay();
                int idx = 0;
                for (UUID u : targetMembers) {
                    Player m = Bukkit.getPlayer(u);
                    if (m == null || m.getUniqueId().equals(p.getUniqueId())) continue;
                    double angle = (Math.PI * 2 / Math.max(1, targetMembers.size())) * (idx++);
                    Location dest = p.getLocation().clone().add(Math.cos(angle) * 1.5, 0, Math.sin(angle) * 1.5);
                    TeleportUtil.delayedTeleportWithAnimation(plugin, m, dest, delay, "party", () -> m.sendMessage(plugin.getLang().t("party.tp.done")));
                    m.sendMessage(plugin.getLang().t("party.tp.start"));
                }
                p.sendMessage(plugin.getLang().t("party.tp.leader"));
                return true;
            }
        }
        return true;
    }
}
