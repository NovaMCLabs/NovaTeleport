package net.sacredlabyrinth.phaed.simpleclans;

import net.sacredlabyrinth.phaed.simpleclans.managers.ClanManager;

/**
 * Minimal API stub for SimpleClans.
 */
public class SimpleClans {
    private static final SimpleClans INSTANCE = new SimpleClans();

    public static SimpleClans getInstance() {
        return INSTANCE;
    }

    public ClanManager getClanManager() {
        return new ClanManager();
    }
}
