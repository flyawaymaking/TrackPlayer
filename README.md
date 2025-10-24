# TrackPlayer - Плагин статистики для Minecraft

Плагин для отслеживания статистики игроков на серверах Minecraft Paper 1.21.8. Ведет учет смертей, убийств игроков и враждебных мобов.

## 📊 Функциональность

### Отслеживаемые показатели:
- **Количество смертей** игрока
- **Количество убийств** других игроков
- **Количество убитых враждебных мобов**

### Враждебные мобы которые отслеживаются:
```
Zombie, Drowned, Husk, Skeleton, Stray, Wither Skeleton, Spider, Cave Spider,
Creeper, Enderman, Witch, Blaze, Ghast, Magma Cube, Slime, Guardian, Elder Guardian,
Shulker, Endermite, Vex, Vindicator, Evoker, Pillager, Ravager, Hoglin, Zoglin,
Piglin Brute, Warden, Breeze, Ender Dragon, Wither, Phantom, Silverfish
```

## 🛠️ Установка

1. Скачайте **последний релиз** из раздела [Releases](../../releases)
2. Поместите его в папку /plugins
3. Перезапустите сервер
4. Настройте config.yml при необходимости
5. Используйте /trackplayer reload, чтобы применить изменения без перезапуска

**Зависимости:**
- Paper 1.21.8 (обязательно)
- PlaceholderAPI (рекомендуется)

## 💬 Placeholders

Плагин предоставляет следующие плейсхолдеры для использования в других плагинах:

```
%trackplayer_kills%       - количество убийств игроков
%trackplayer_deaths%      - количество смертей
%trackplayer_mob_kills%   - количество убитых враждебных мобов
```

### Примеры использования:
- В таблице лидеров: `%trackplayer_kills%`
- В голограммах: `"Убийств: %trackplayer_kills%"`
- В чат-плагинах: `"Ваша статистика: K:%trackplayer_kills% D:%trackplayer_deaths%"`

## 🔧 Команды

### Основные команды:
```
/trackplayer                    - посмотреть свою статистику
/trackplayer <ник>              - посмотреть статистику другого игрока
/trackplayer reload             - перезагрузить конфигурацию
```

### Админ-команды:
```
/trackplayer admin list         - список убийств мобов всеми игроками
/trackplayer admin resetmobs    - сбросить статистику мобов у всех игроков
/trackplayer admin save         - принудительно сохранить данные
/trackplayer admin status       - статус системы
/trackplayer admin papi         - диагностика плейсхолдеров
```

## 📡 API для разработчиков

Плагин предоставляет API для интеграции с другими плагинами (например его использует [TopsSystem](https://github.com/flyawaymaking/TopsSystem)):

### Получение экземпляра плагина:
```java
TrackPlayer trackPlugin = (TrackPlayer) Bukkit.getPluginManager().getPlugin("TrackPlayer");
```

### Основные методы API:
```java
// Получить статистику игрока
int deaths = trackPlugin.getDeaths(uuid);
int playerKills = trackPlugin.getPlayerKills(uuid);
int mobKills = trackPlugin.getMobKills(uuid);

// Получить список всех игроков и их убийств мобов
Map<UUID, Integer> mobKills = trackPlugin.getPlayerMobKills();

// Сбросить статистику мобов
trackPlugin.resetAllMobKills();
```

## ⚙️ Конфигурация

Файл `config.yml` автоматически создается при первом запуске:

```yaml
# Интервал автосохранения в минутах
auto-save-interval: 5

# Отладка
debug: false

# Настройки статистики
track-mob-kills: true
track-player-kills: true
track-deaths: true
```

## 🔄 Автосохранение

Плагин автоматически сохраняет данные каждые 5 минут (настраивается) для защиты от потери данных при сбоях сервера.

## 📊 Хранение данных

Данные хранятся в файле `plugins/TrackPlayer/playerdata.yml` в формате YAML.

## 🐛 Поддержка

При возникновении проблем:
1. Проверьте наличие PlaceholderAPI для работы плейсхолдеров
2. Используйте `/trackplayer admin papi` для диагностики
3. Включите `debug: true` в конфигурации для подробного лога

## 📄 Лицензия

Плагин распространяется под лицензией MIT.
