package com.novamclabs.log;

import org.bukkit.Location;

/**
 * Teleport log entry.
 */
public record TeleportLogEntry(long timeMillis, String type, Location from, Location to) {
}
