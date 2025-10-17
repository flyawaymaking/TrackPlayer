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
        return "trackplayer"; // Обратите внимание на название!
    }

    @Override
    public @NotNull String getAuthor() {
        return "FlyAway";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true; // Это важно для сохранения после перезагрузки
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "0";

        UUID uuid = player.getUniqueId();

        switch (params.toLowerCase()) {
            case "kills":
            case "player_kills":
                return String.valueOf(plugin.getPlayerKills(uuid));
            case "deaths":
                return String.valueOf(plugin.getDeaths(uuid));
            case "mob_kills":
            case "mobkills":
                return String.valueOf(plugin.getMobKills(uuid));
            default:
                return null;
        }
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        return onRequest(player, params);
    }
}
