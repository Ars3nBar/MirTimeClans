package ru.mirtime.clans;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class ClansExpansion extends PlaceholderExpansion {

    private final MirTimeClans plugin;

    public ClansExpansion(MirTimeClans plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "mirtimeclans";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Ars3nBar";
    }

    @Override
    public @NotNull String getVersion() {
        return "2.1.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null || player.getName() == null) return "";
        return plugin.resolvePlaceholder(player.getName(), params.toLowerCase());
    }
}
