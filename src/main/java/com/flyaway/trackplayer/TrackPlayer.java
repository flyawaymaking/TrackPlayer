package com.flyaway.trackplayer;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityDeathEvent;
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
    private Map<UUID, PlayerStats> statsCache;
    private int saveTaskId;
    private boolean needsSave = false;

    @Override
    public void onEnable() {
        instance = this;
        statsCache = new ConcurrentHashMap<>();

        // Создание файла конфигурации
        saveDefaultConfig();

        // Загрузка данных игроков
        loadPlayerData();

        // Запуск периодического сохранения
        startAutoSave();

        // Регистрация событий
        getServer().getPluginManager().registerEvents(this, this);

        // Регистрация команд
        Objects.requireNonNull(getCommand("trackstats")).setExecutor(new StatsCommand());

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

        // Принудительно сохраняем данные при выключении
        savePlayerData();
        getLogger().info("TrackPlayer плагин выключен!");
    }

    public static TrackPlayer getInstance() {
        return instance;
    }

    private void loadPlayerData() {
        dataFile = new File(getDataFolder(), "playerdata.yml");
        if (!dataFile.exists()) {
            saveResource("playerdata.yml", false);
        }
        playerData = YamlConfiguration.loadConfiguration(dataFile);

        // Загрузка данных в кэш
        if (playerData.contains("players")) {
            for (String uuidStr : playerData.getConfigurationSection("players").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    int deaths = playerData.getInt("players." + uuidStr + ".deaths", 0);
                    int playerKills = playerData.getInt("players." + uuidStr + ".player_kills", 0);
                    int mobKills = playerData.getInt("players." + uuidStr + ".mob_kills", 0);

                    statsCache.put(uuid, new PlayerStats(deaths, playerKills, mobKills));
                } catch (IllegalArgumentException e) {
                    getLogger().warning("Неверный UUID в файле данных: " + uuidStr);
                }
            }
        }
        getLogger().info("Загружены данные для " + statsCache.size() + " игроков");
    }

    private void startAutoSave() {
        // Получаем интервал сохранения из конфига (по умолчанию 5 минут)
        int saveInterval = getConfig().getInt("auto-save-interval", 5) * 60 * 20; // в тиках

        saveTaskId = new BukkitRunnable() {
            @Override
            public void run() {
                if (needsSave) {
                    savePlayerData();
                    getLogger().info("Данные автоматически сохранены (" + statsCache.size() + " игроков)");
                }
            }
        }.runTaskTimer(this, saveInterval, saveInterval).getTaskId();

        getLogger().info("Автосохранение данных каждые " + getConfig().getInt("auto-save-interval", 5) + " минут");
    }

    private void registerShutdownHook() {
        // Обработчик для безопасного выключения
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (instance != null) {
                instance.savePlayerData();
            }
        }));
    }

    public void savePlayerData() {
        if (playerData != null && dataFile != null) {
            try {
                // Создаем временную копию для сохранения, чтобы минимизировать блокировку
                Map<UUID, PlayerStats> snapshot = new HashMap<>(statsCache);

                // Сохранение данных из снимка
                for (Map.Entry<UUID, PlayerStats> entry : snapshot.entrySet()) {
                    String path = "players." + entry.getKey().toString();
                    PlayerStats stats = entry.getValue();
                    playerData.set(path + ".deaths", stats.getDeaths());
                    playerData.set(path + ".player_kills", stats.getPlayerKills());
                    playerData.set(path + ".mob_kills", stats.getMobKills());
                }

                playerData.save(dataFile);
                needsSave = false; // Сбрасываем флаг после успешного сохранения

            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Не удалось сохранить данные игроков!", e);
            }
        }
    }

    public void forceSave() {
        savePlayerData();
        getLogger().info("Принудительное сохранение данных выполнено");
    }

    public void markDataDirty() {
        this.needsSave = true;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Player killer = player.getKiller();

        // Увеличиваем счетчик смертей игрока
        incrementDeaths(player.getUniqueId());

        // Если убийца - другой игрок, увеличиваем его счетчик убийств игроков
        if (killer != null) {
            incrementPlayerKills(killer.getUniqueId());
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();

        // Если моб убит игроком и это не игрок
        if (killer != null && event.getEntityType() != EntityType.PLAYER) {
            // Проверяем, является ли моб враждебным
            if (isHostileMob(event.getEntityType())) {
                incrementMobKills(killer.getUniqueId());
            }
        }
    }

    /**
     * Проверяет, является ли тип сущности враждебным мобом
     */
    private boolean isHostileMob(EntityType entityType) {
        switch (entityType) {
            // === ОСНОВНЫЕ ВРАЖДЕБНЫЕ МОБЫ ===

            // Нежить
            case ZOMBIE:
            case DROWNED:
            case HUSK:
            case ZOMBIFIED_PIGLIN:
            case ZOGLIN:
            case SKELETON:
            case STRAY:
            case WITHER_SKELETON:
            case PHANTOM:

            // Пауки и насекомые
            case SPIDER:
            case CAVE_SPIDER:
            case SILVERFISH:
            case ENDERMITE:

            // Агрессивные
            case CREEPER:
            case ENDERMAN:
            case WITCH:
            case BLAZE:
            case GHAST:
            case MAGMA_CUBE:
            case SLIME:
            case GUARDIAN:
            case ELDER_GUARDIAN:
            case SHULKER:
            case SHULKER_BULLET:
            case VEX:
            case VINDICATOR:
            case EVOKER:
            case ILLUSIONER:
            case PILLAGER:
            case RAVAGER:
            case HOGLIN:
            case PIGLIN_BRUTE:
            case WARDEN:
            case BREEZE:

            // === БОССЫ ===
            case ENDER_DRAGON:
            case WITHER:

            // === НЕЙТРАЛЬНЫЕ, НО МОГУТ БЫТЬ ВРАЖДЕБНЫМИ ===
            case PIGLIN: // Может быть враждебным без золотой брони
                return true;

            // === МИРНЫЕ МОБЫ И ЖИВОТНЫЕ - НЕ СЧИТАЕМ ===

            // Животные фермы
            case COW:
            case PIG:
            case SHEEP:
            case CHICKEN:
            case RABBIT:
            case FOX:
            case PANDA:
            case BEE:
            case POLAR_BEAR:
            case GOAT:
            case FROG:
            case TADPOLE:
            case SNIFFER:
            case CAMEL:

            // Домашние животные
            case WOLF:
            case OCELOT:
            case CAT:
            case PARROT:
            case AXOLOTL:
            case HORSE:
            case DONKEY:
            case MULE:
            case SKELETON_HORSE:
            case ZOMBIE_HORSE:
            case LLAMA:
            case TRADER_LLAMA:

            // Водные обитатели
            case SQUID:
            case GLOW_SQUID:
            case DOLPHIN:
            case TURTLE:
            case COD:
            case SALMON:
            case TROPICAL_FISH:
            case PUFFERFISH:

            // Другие мирные
            case BAT:
            case STRIDER:
            case SNOW_GOLEM:
            case IRON_GOLEM:
            case VILLAGER:
            case WANDERING_TRADER:
            case ALLAY:

            // === TECHNICAL ENTITIES - НЕ СЧИТАЕМ ===
            case ARMOR_STAND:
            case ITEM_FRAME:
            case GLOW_ITEM_FRAME:
            case PAINTING:
            case MINECART:
            case BOAT:
            case END_CRYSTAL:
            case EXPERIENCE_ORB:
            case AREA_EFFECT_CLOUD:
            case EGG:
            case ENDER_PEARL:
            case EYE_OF_ENDER:
            case FALLING_BLOCK:
            case FIREWORK_ROCKET:
            case ITEM:
            case LIGHTNING_BOLT:
            case LLAMA_SPIT:
            case PLAYER:
            case POTION:
            case SMALL_FIREBALL:
            case SNOWBALL:
            case SPECTRAL_ARROW:
            case TNT:
            case TRIDENT:
            case WITHER_SKULL:
            case FISHING_BOBBER:
            case MARKER:
            case BLOCK_DISPLAY:
            case INTERACTION:
            case TEXT_DISPLAY:
            case ITEM_DISPLAY:
                return false;

            default:
                // Для неизвестных или новых мобов - по умолчанию не считаем
                // Можно добавить логирование для отладки
                if (getConfig().getBoolean("debug", false)) {
                    getLogger().info("Неизвестный тип моба: " + entityType + " - не считается враждебным");
                }
                return false;
        }
    }

    // API методы

    public void incrementDeaths(UUID uuid) {
        PlayerStats stats = statsCache.getOrDefault(uuid, new PlayerStats());
        stats.incrementDeaths();
        statsCache.put(uuid, stats);
        markDataDirty(); // Помечаем данные как измененные
    }

    public void incrementPlayerKills(UUID uuid) {
        PlayerStats stats = statsCache.getOrDefault(uuid, new PlayerStats());
        stats.incrementPlayerKills();
        statsCache.put(uuid, stats);
        markDataDirty(); // Помечаем данные как измененные
    }

    public void incrementMobKills(UUID uuid) {
        PlayerStats stats = statsCache.getOrDefault(uuid, new PlayerStats());
        stats.incrementMobKills();
        statsCache.put(uuid, stats);
        markDataDirty(); // Помечаем данные как измененные
    }

    public int getDeaths(UUID uuid) {
        PlayerStats stats = statsCache.get(uuid);
        return stats != null ? stats.getDeaths() : 0;
    }

    public int getPlayerKills(UUID uuid) {
        PlayerStats stats = statsCache.get(uuid);
        return stats != null ? stats.getPlayerKills() : 0;
    }

    public int getMobKills(UUID uuid) {
        PlayerStats stats = statsCache.get(uuid);
        return stats != null ? stats.getMobKills() : 0;
    }

    /**
     * API метод: Получить список UUID игроков и количество убитых мобов
     */
    public Map<UUID, Integer> getPlayerMobKills() {
        Map<UUID, Integer> result = new HashMap<>();
        for (Map.Entry<UUID, PlayerStats> entry : statsCache.entrySet()) {
            result.put(entry.getKey(), entry.getValue().getMobKills());
        }
        return result;
    }

    /**
     * API метод: Сбросить статистику по убитым мобам для всех игроков
     */
    public void resetAllMobKills() {
        for (PlayerStats stats : statsCache.values()) {
            stats.setMobKills(0);
        }
        markDataDirty(); // Помечаем данные как измененные
        savePlayerData(); // Немедленно сохраняем после сброса
    }

    /**
     * Получить количество игроков в кэше
     */
    public int getCachedPlayersCount() {
        return statsCache.size();
    }

    /**
     * Принудительно сохранить данные и получить статус
     */
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

        public void incrementDeaths() { deaths++; }
        public void incrementPlayerKills() { playerKills++; }
        public void incrementMobKills() { mobKills++; }

        // Геттеры и сеттеры
        public int getDeaths() { return deaths; }
        public int getPlayerKills() { return playerKills; }
        public int getMobKills() { return mobKills; }
        public void setDeaths(int deaths) { this.deaths = deaths; }
        public void setPlayerKills(int playerKills) { this.playerKills = playerKills; }
        public void setMobKills(int mobKills) { this.mobKills = mobKills; }
    }
}
