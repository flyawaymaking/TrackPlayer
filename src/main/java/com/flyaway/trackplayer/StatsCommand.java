package com.flyaway.trackplayer;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import java.util.Map;
import java.util.UUID;

public class StatsCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player) {
                showStats(sender, (Player) sender);
            } else {
                sender.sendMessage("Используйте: /trackstats <player> или /trackstats admin <subcommand>");
            }
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "admin":
                if (!sender.hasPermission("trackplayer.admin")) {
                    sender.sendMessage("§cУ вас нет прав для использования этой команды!");
                    return true;
                }
                handleAdminCommands(sender, args);
                break;
            case "reload":
                if (!sender.hasPermission("trackplayer.reload")) {
                    sender.sendMessage("§cУ вас нет прав для использования этой команды!");
                    return true;
                }
                TrackPlayer.getInstance().reloadConfig();
                sender.sendMessage("§aКонфигурация перезагружена!");
                break;
            case "save":
                if (!sender.hasPermission("trackplayer.admin")) {
                    sender.sendMessage("§cУ вас нет прав для использования этой команды!");
                    return true;
                }
                TrackPlayer.getInstance().forceSave();
                sender.sendMessage("§aДанные принудительно сохранены!");
                break;
            case "status":
                if (!sender.hasPermission("trackplayer.admin")) {
                    sender.sendMessage("§cУ вас нет прав для использования этой команды!");
                    return true;
                }
                showStatus(sender);
                break;
            default:
                Player target = Bukkit.getPlayer(args[0]);
                if (target != null) {
                    showStats(sender, target);
                } else {
                    sender.sendMessage("§cИгрок не найден!");
                }
                break;
        }

        return true;
    }

    private void showStats(CommandSender sender, Player player) {
        TrackPlayer plugin = TrackPlayer.getInstance();
        UUID uuid = player.getUniqueId();

        sender.sendMessage("§6=== Статистика игрока " + player.getName() + " ===");
        sender.sendMessage("§7Смерти: §f" + plugin.getDeaths(uuid));
        sender.sendMessage("§7Убийств игроков: §f" + plugin.getPlayerKills(uuid));
        sender.sendMessage("§7Убийств мобов: §f" + plugin.getMobKills(uuid));
    }

    private void showStatus(CommandSender sender) {
        TrackPlayer plugin = TrackPlayer.getInstance();

        sender.sendMessage("§6=== Статус TrackPlayer ===");
        sender.sendMessage("§7Игроков в кэше: §f" + plugin.getCachedPlayersCount());
        sender.sendMessage("§7Статус сохранения: §f" + plugin.getSaveStatus());
        sender.sendMessage("§7Автосохранение: §fкаждые " +
            plugin.getConfig().getInt("auto-save-interval", 5) + " минут");
    }

    private void handleAdminCommands(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cИспользуйте: /trackstats admin <list|resetmobs|save|status>");
            return;
        }

        switch (args[1].toLowerCase()) {
            case "list":
                Map<UUID, Integer> mobKills = TrackPlayer.getInstance().getPlayerMobKills();
                sender.sendMessage("§6=== Список убийств мобов игроками ===");
                mobKills.entrySet().stream()
                    .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                    .forEach(entry -> {
                        String playerName = Bukkit.getOfflinePlayer(entry.getKey()).getName();
                        sender.sendMessage("§7" + (playerName != null ? playerName : "Unknown") +
                                          ": §f" + entry.getValue());
                    });
                break;
            case "resetmobs":
                TrackPlayer.getInstance().resetAllMobKills();
                sender.sendMessage("§aСтатистика убийств мобов сброшена для всех игроков!");
                break;
            case "save":
                TrackPlayer.getInstance().forceSave();
                sender.sendMessage("§aДанные принудительно сохранены!");
                break;
            case "status":
                showStatus(sender);
                break;
            default:
                sender.sendMessage("§cНеизвестная подкоманда!");
                break;
        }
    }
}
