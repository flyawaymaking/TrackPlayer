package com.flyaway.trackplayer;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class StatsPlaceholder extends PlaceholderExpansion {

    private final TrackPlayer plugin;

    public StatsPlaceholder() {
        this.plugin = TrackPlayer.getInstance();
    }

    @Override
    public @NotNull String getIdentifier() {
        return "trackplayer";
    }

    @Override
    public @NotNull String getAuthor() {
        return plugin.getPluginMeta().getAuthors().getFirst();
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "0";

        UUID uuid = player.getUniqueId();

        return switch (params.toLowerCase()) {
            case "kills", "player_kills" -> String.valueOf(plugin.getPlayerKills(uuid));
            case "deaths" -> String.valueOf(plugin.getDeaths(uuid));
            case "mob_kills", "mobkills" -> String.valueOf(plugin.getMobKills(uuid));
            default -> null;
        };
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        return onRequest(player, params);
    }
}
