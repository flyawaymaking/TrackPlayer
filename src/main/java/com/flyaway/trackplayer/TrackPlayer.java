package com.flyaway.trackplayer;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.entity.EntityType;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class TrackPlayer extends JavaPlugin implements Listener {

    private static TrackPlayer instance;
    private File dataFile;
    private FileConfiguration playerData;
    private final Map<UUID, PlayerStats> statsCache = new ConcurrentHashMap<>();
    private int saveTaskId;
    private boolean needsSave = false;

    @Override
    public void onEnable() {
        instance = this;

        // Создание файла конфигурации
        saveDefaultConfig();

        // Загрузка данных игроков
        setupDataFile();

        // Запуск периодического сохранения
        startAutoSave();

        // Регистрация событий
        getServer().getPluginManager().registerEvents(this, this);

        // Инициализация онлайн игроков
        initializeOnlinePlayers();

        // Регистрация команд
        Objects.requireNonNull(getCommand("trackplayer")).setExecutor(new StatsCommand());

        // Регистрация PlaceholderAPI (если установлена)
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new StatsPlaceholder().register();
            getLogger().info("PlaceholderAPI расширения зарегистрированы!");
        } else {
            getLogger().warning("PlaceholderAPI не найден! Плейсхолдеры не будут работать.");
        }

        getLogger().info("TrackPlayer плагин включен!");
    }

    @Override
    public void onDisable() {
        // Останавливаем задачу автосохранения
        if (saveTaskId != 0) {
            getServer().getScheduler().cancelTask(saveTaskId);
        }

        // Сохраняем данные всех онлайн игроков и очищаем кэш
        saveAllOnlinePlayersData();
        getLogger().info("TrackPlayer плагин выключен!");
    }

    public static TrackPlayer getInstance() {
        return instance;
    }

    private void setupDataFile() {
        dataFile = new File(getDataFolder(), "playerdata.yml");
        if (!dataFile.exists()) {
            saveResource("playerdata.yml", false);
        }
        playerData = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void initializeOnlinePlayers() {
        for (Player player : getServer().getOnlinePlayers()) {
            getOrCreatePlayerStats(player.getUniqueId());
        }
        getLogger().info("Инициализированы данные для " + statsCache.size() + " онлайн игроков");
    }

    private PlayerStats loadPlayerData(UUID uuid) {
        String path = "players." + uuid.toString();
        if (playerData.contains(path)) {
            int deaths = playerData.getInt(path + ".deaths", 0);
            int playerKills = playerData.getInt(path + ".player_kills", 0);
            int mobKills = playerData.getInt(path + ".mob_kills", 0);
            return new PlayerStats(deaths, playerKills, mobKills);
        }
        return new PlayerStats();
    }

    public PlayerStats getOrCreatePlayerStats(UUID uuid) {
        synchronized (statsCache) {
            return statsCache.computeIfAbsent(uuid, this::loadPlayerData);
        }
    }

    public void removePlayerStats(UUID uuid) {
        synchronized (statsCache) {
            PlayerStats stats = statsCache.remove(uuid);
            if (stats != null) {
                savePlayerDataToFile(uuid, stats);
            }
        }
    }

    private void startAutoSave() {
        int saveInterval = getConfig().getInt("auto-save-interval", 5) * 60 * 20;

        saveTaskId = new BukkitRunnable() {
            @Override
            public void run() {
                if (needsSave) {
                    saveAllOnlinePlayersData();
                    if (getConfig().getBoolean("debug", false)) {
                        getLogger().info("Данные автоматически сохранены (" + statsCache.size() + " игроков)");
                    }
                }
            }
        }.runTaskTimer(this, saveInterval, saveInterval).getTaskId();

        getLogger().info("Автосохранение данных каждые " + getConfig().getInt("auto-save-interval", 5) + " минут");
    }

    private void saveAllOnlinePlayersData() {
        synchronized (statsCache) {
            for (Map.Entry<UUID, PlayerStats> entry : statsCache.entrySet()) {
                savePlayerDataToFile(entry.getKey(), entry.getValue());
            }
            needsSave = false;
        }
    }

    private void savePlayerDataToFile(UUID uuid, PlayerStats stats) {
        String path = "players." + uuid.toString();
        playerData.set(path + ".deaths", stats.getDeaths());
        playerData.set(path + ".player_kills", stats.getPlayerKills());
        playerData.set(path + ".mob_kills", stats.getMobKills());

        try {
            playerData.save(dataFile);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Ошибка сохранения данных игрока " + uuid, e);
        }
    }

    public void forceSave() {
        saveAllOnlinePlayersData();
        getLogger().info("Принудительное сохранение данных выполнено");
    }

    public void markDataDirty() {
        this.needsSave = true;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        getOrCreatePlayerStats(player.getUniqueId());
        if (getConfig().getBoolean("debug", false)) {
            getLogger().info("Данные загружены для игрока: " + player.getName());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        removePlayerStats(player.getUniqueId());
        if (getConfig().getBoolean("debug", false)) {
            getLogger().info("Данные сохранены и удалены из памяти для игрока: " + player.getName());
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Player killer = player.getKiller();

        incrementDeaths(player.getUniqueId());

        if (killer != null) {
            incrementPlayerKills(killer.getUniqueId());
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();

        if (killer != null && event.getEntityType() != EntityType.PLAYER) {
            if (isHostileMob(event.getEntityType())) {
                incrementMobKills(killer.getUniqueId());
            }
        }
    }

    private boolean isHostileMob(EntityType entityType) {
        return switch (entityType) {
            case ZOMBIE, DROWNED, HUSK, ZOMBIFIED_PIGLIN, ZOGLIN, SKELETON, STRAY, WITHER_SKELETON, PHANTOM, SPIDER,
                 CAVE_SPIDER, SILVERFISH, ENDERMITE, CREEPER, ENDERMAN, WITCH, BLAZE, GHAST, MAGMA_CUBE, SLIME,
                 GUARDIAN, ELDER_GUARDIAN, SHULKER, VEX, VINDICATOR, EVOKER, ILLUSIONER, PILLAGER, RAVAGER, HOGLIN,
                 PIGLIN_BRUTE, WARDEN, BREEZE, ENDER_DRAGON, WITHER, PIGLIN -> true;
            default -> {
                if (getConfig().getBoolean("debug", false)) {
                    getLogger().info("Неизвестный тип моба: " + entityType + " - не считается враждебным");
                }
                yield false;
            }
        };
    }

    // API методы

    public void incrementDeaths(UUID uuid) {
        synchronized (statsCache) {
            PlayerStats stats = getOrCreatePlayerStats(uuid);
            stats.incrementDeaths();
            markDataDirty();
        }
    }

    public void incrementPlayerKills(UUID uuid) {
        synchronized (statsCache) {
            PlayerStats stats = getOrCreatePlayerStats(uuid);
            stats.incrementPlayerKills();
            markDataDirty();
        }
    }

    public void incrementMobKills(UUID uuid) {
        synchronized (statsCache) {
            PlayerStats stats = getOrCreatePlayerStats(uuid);
            stats.incrementMobKills();
            markDataDirty();
        }
    }

    public int getDeaths(UUID uuid) {
        // Сначала проверяем онлайн игроков в кэше
        synchronized (statsCache) {
            PlayerStats stats = statsCache.get(uuid);
            if (stats != null) {
                return stats.getDeaths();
            }
        }

        // Если нет в кэше, проверяем файл (для офлайн игроков)
        return playerData.getInt("players." + uuid.toString() + ".deaths", 0);
    }

    public int getPlayerKills(UUID uuid) {
        // Сначала проверяем онлайн игроков в кэше
        synchronized (statsCache) {
            PlayerStats stats = statsCache.get(uuid);
            if (stats != null) {
                return stats.getPlayerKills();
            }
        }

        // Если нет в кэше, проверяем файл
        return playerData.getInt("players." + uuid.toString() + ".player_kills", 0);
    }

    public int getMobKills(UUID uuid) {
        // Сначала проверяем онлайн игроков в кэше
        synchronized (statsCache) {
            PlayerStats stats = statsCache.get(uuid);
            if (stats != null) {
                return stats.getMobKills();
            }
        }

        // Если нет в кэше, проверяем файл
        return playerData.getInt("players." + uuid.toString() + ".mob_kills", 0);
    }

    public Map<UUID, Integer> getPlayerMobKills() {
        Map<UUID, Integer> result = new HashMap<>();

        // Сначала добавляем онлайн игроков из кэша
        synchronized (statsCache) {
            for (Map.Entry<UUID, PlayerStats> entry : statsCache.entrySet()) {
                result.put(entry.getKey(), entry.getValue().getMobKills());
            }
        }

        // Затем добавляем офлайн игроков из файла (если их еще нет в результате)
        if (playerData.contains("players")) {
            for (String uuidStr : playerData.getConfigurationSection("players").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    // Добавляем только если игрок не онлайн (нет в кэше)
                    if (!result.containsKey(uuid)) {
                        int mobKills = playerData.getInt("players." + uuidStr + ".mob_kills", 0);
                        result.put(uuid, mobKills);
                    }
                } catch (IllegalArgumentException e) {
                    getLogger().warning("Неверный UUID в файле данных: " + uuidStr);
                }
            }
        }
        return result;
    }

    public Map<UUID, Integer> getAllDeaths() {
        Map<UUID, Integer> result = new HashMap<>();

        // Сначала онлайн игроки из кэша
        synchronized (statsCache) {
            for (Map.Entry<UUID, PlayerStats> entry : statsCache.entrySet()) {
                result.put(entry.getKey(), entry.getValue().getDeaths());
            }
        }

        // Затем офлайн игроки из файла
        if (playerData.contains("players")) {
            for (String uuidStr : playerData.getConfigurationSection("players").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    if (!result.containsKey(uuid)) {
                        int deaths = playerData.getInt("players." + uuidStr + ".deaths", 0);
                        result.put(uuid, deaths);
                    }
                } catch (IllegalArgumentException e) {
                    getLogger().warning("Неверный UUID в файле данных: " + uuidStr);
                }
            }
        }
        return result;
    }

    public Map<UUID, Integer> getAllPlayerKills() {
        Map<UUID, Integer> result = new HashMap<>();

        // Сначала онлайн игроки из кэша
        synchronized (statsCache) {
            for (Map.Entry<UUID, PlayerStats> entry : statsCache.entrySet()) {
                result.put(entry.getKey(), entry.getValue().getPlayerKills());
            }
        }

        // Затем офлайн игроки из файла
        if (playerData.contains("players")) {
            for (String uuidStr : playerData.getConfigurationSection("players").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    if (!result.containsKey(uuid)) {
                        int playerKills = playerData.getInt("players." + uuidStr + ".player_kills", 0);
                        result.put(uuid, playerKills);
                    }
                } catch (IllegalArgumentException e) {
                    getLogger().warning("Неверный UUID в файле данных: " + uuidStr);
                }
            }
        }
        return result;
    }

    public void resetAllMobKills() {
        // Сбрасываем для онлайн игроков в кэше
        synchronized (statsCache) {
            for (PlayerStats stats : statsCache.values()) {
                stats.setMobKills(0);
            }
        }

        // Сбрасываем для всех игроков в файле
        if (playerData.contains("players")) {
            for (String uuidStr : playerData.getConfigurationSection("players").getKeys(false)) {
                playerData.set("players." + uuidStr + ".mob_kills", 0);
            }
        }

        markDataDirty();
        forceSave();
    }

    public void resetAllDeaths() {
        synchronized (statsCache) {
            for (PlayerStats stats : statsCache.values()) {
                stats.setDeaths(0);
            }
        }

        if (playerData.contains("players")) {
            for (String uuidStr : playerData.getConfigurationSection("players").getKeys(false)) {
                playerData.set("players." + uuidStr + ".deaths", 0);
            }
        }

        markDataDirty();
        forceSave();
    }

    public void resetAllPlayerKills() {
        synchronized (statsCache) {
            for (PlayerStats stats : statsCache.values()) {
                stats.setPlayerKills(0);
            }
        }

        if (playerData.contains("players")) {
            for (String uuidStr : playerData.getConfigurationSection("players").getKeys(false)) {
                playerData.set("players." + uuidStr + ".player_kills", 0);
            }
        }

        markDataDirty();
        forceSave();
    }

    public int getCachedPlayersCount() {
        return statsCache.size();
    }

    public String getSaveStatus() {
        return needsSave ? "Требуется сохранение" : "Все данные сохранены";
    }

    // Класс для хранения статистики игрока
    public static class PlayerStats {
        private int deaths;
        private int playerKills;
        private int mobKills;

        public PlayerStats() {
            this(0, 0, 0);
        }

        public PlayerStats(int deaths, int playerKills, int mobKills) {
            this.deaths = deaths;
            this.playerKills = playerKills;
            this.mobKills = mobKills;
        }

        public void incrementDeaths() {
            deaths++;
        }

        public void incrementPlayerKills() {
            playerKills++;
        }

        public void incrementMobKills() {
            mobKills++;
        }

        public int getDeaths() {
            return deaths;
        }

        public int getPlayerKills() {
            return playerKills;
        }

        public int getMobKills() {
            return mobKills;
        }

        public void setDeaths(int deaths) {
            this.deaths = deaths;
        }

        public void setPlayerKills(int playerKills) {
            this.playerKills = playerKills;
        }

        public void setMobKills(int mobKills) {
            this.mobKills = mobKills;
        }
    }
}
