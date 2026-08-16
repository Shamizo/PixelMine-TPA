package top.craft_hello.tpa.utils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

import static java.util.Objects.isNull;

public class SQLiteUtil {
    private static final ReentrantLock LOCK = new ReentrantLock();
    private static final String PLAYER_UUID_REGEX = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";
    private static volatile Connection connection;
    private static Plugin PLUGIN;

    public static void init(Plugin plugin) {
        LOCK.lock();
        try {
            if (!isNull(connection)) return;
            PLUGIN = plugin;
            try {
                copyLegacyData(plugin);
                if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
                Class.forName("org.sqlite.JDBC");
                File dataFolder = new File(plugin.getDataFolder(), "data");
                if (!dataFolder.exists()) dataFolder.mkdirs();
                migrateDatabaseFile(plugin, dataFolder);
                File databaseFile = new File(dataFolder, "data.db");
                connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
                try (Statement statement = connection.createStatement()) {
                    // 开启 WAL 模式，允许并发读取且写入不阻塞读取
                    statement.execute("PRAGMA journal_mode = WAL;");
                    // 防止并发写入时因数据库被占用而抛 SQLITE_BUSY，等待而不是立刻失败，避免死锁
                    statement.execute("PRAGMA busy_timeout = 5000;");
                    // WAL 模式下 NORMAL 同步级别兼顾安全与性能
                    statement.execute("PRAGMA synchronous = NORMAL;");
                }
                createTables();
                migrateYmlData();
            } catch (Exception exception) {
                plugin.getLogger().warning("[PMS-TPA] SQLite 数据库初始化失败：" + exception.getMessage());
                connection = null;
            }
        } finally {
            LOCK.unlock();
        }
    }

    public static void close() {
        LOCK.lock();
        try {
            if (!isNull(connection) && !connection.isClosed()) connection.close();
        } catch (SQLException ignored) {
        } finally {
            connection = null;
            LOCK.unlock();
        }
    }

    private static void createTables() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS player_data (" +
                    "player_uuid TEXT PRIMARY KEY," +
                    "player_name TEXT," +
                    "language TEXT," +
                    "setlang INTEGER DEFAULT 0," +
                    "default_home TEXT," +
                    "deny_list TEXT," +
                    "last_world TEXT, last_x REAL, last_y REAL, last_z REAL, last_pitch REAL, last_yaw REAL," +
                    "logout_world TEXT, logout_x REAL, logout_y REAL, logout_z REAL, logout_pitch REAL, logout_yaw REAL);");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS homes (" +
                    "player_uuid TEXT NOT NULL," +
                    "home_name TEXT NOT NULL," +
                    "world TEXT NOT NULL," +
                    "x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL, pitch REAL, yaw REAL," +
                    "PRIMARY KEY (player_uuid, home_name));");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS warps (" +
                    "warp_name TEXT PRIMARY KEY," +
                    "world TEXT NOT NULL," +
                    "x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL, pitch REAL, yaw REAL);");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS spawn (" +
                    "id INTEGER PRIMARY KEY CHECK (id = 1)," +
                    "world TEXT NOT NULL," +
                    "x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL, pitch REAL, yaw REAL);");
        }
    }

    // ============================== 基础执行方法 ==============================

    private static void execute(String sql, Object... params) {
        if (isNull(connection)) return;
        LOCK.lock();
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            bind(preparedStatement, params);
            preparedStatement.executeUpdate();
        } catch (SQLException exception) {
            PLUGIN.getLogger().warning("[PMS-TPA] SQLite 写入失败：" + exception.getMessage());
        } finally {
            LOCK.unlock();
        }
    }

    private static void bind(PreparedStatement preparedStatement, Object... params) throws SQLException {
        for (int index = 0; index < params.length; index++) {
            preparedStatement.setObject(index + 1, params[index]);
        }
    }

    private static int count(String table) {
        if (isNull(connection)) return 0;
        LOCK.lock();
        try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT COUNT(*) FROM " + table);
             ResultSet resultSet = preparedStatement.executeQuery()) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        } catch (SQLException exception) {
            PLUGIN.getLogger().warning("[PMS-TPA] SQLite 查询失败：" + exception.getMessage());
            return 0;
        } finally {
            LOCK.unlock();
        }
    }

    private static Location buildLocation(ResultSet resultSet, String worldColumn, String xColumn, String yColumn, String zColumn, String pitchColumn, String yawColumn) throws SQLException {
        String worldName = resultSet.getString(worldColumn);
        if (isNull(worldName)) return null;
        World world = Bukkit.getWorld(worldName);
        if (isNull(world)) return null;
        return new Location(world, resultSet.getDouble(xColumn), resultSet.getDouble(yColumn), resultSet.getDouble(zColumn),
                (float) resultSet.getDouble(yawColumn), (float) resultSet.getDouble(pitchColumn));
    }

    private static Location readLocation(FileConfiguration configuration, String index) {
        String worldName = configuration.getString(index + ".world");
        if (isNull(worldName)) return null;
        World world = Bukkit.getWorld(worldName);
        if (isNull(world)) return null;
        return new Location(world, configuration.getDouble(index + ".x"), configuration.getDouble(index + ".y"), configuration.getDouble(index + ".z"),
                (float) configuration.getDouble(index + ".yaw"), (float) configuration.getDouble(index + ".pitch"));
    }

    private static String joinDenyList(List<String> denyList) {
        if (isNull(denyList) || denyList.isEmpty()) return "";
        return String.join(",", denyList);
    }

    // ============================== 玩家数据 ==============================

    public static boolean containsPlayerData(UUID playerUUID) {
        if (isNull(connection)) return false;
        LOCK.lock();
        try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT 1 FROM player_data WHERE player_uuid = ?")) {
            preparedStatement.setString(1, playerUUID.toString());
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            PLUGIN.getLogger().warning("[PMS-TPA] SQLite 查询失败：" + exception.getMessage());
            return false;
        } finally {
            LOCK.unlock();
        }
    }

    public static Map<String, Object> loadPlayerDataRow(UUID playerUUID) {
        if (isNull(connection)) return null;
        LOCK.lock();
        try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT * FROM player_data WHERE player_uuid = ?")) {
            preparedStatement.setString(1, playerUUID.toString());
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (!resultSet.next()) return null;
                Map<String, Object> data = new HashMap<>();
                data.put("player_name", resultSet.getString("player_name"));
                data.put("language", resultSet.getString("language"));
                data.put("setlang", resultSet.getBoolean("setlang"));
                data.put("default_home", resultSet.getString("default_home"));
                data.put("last_location", buildLocation(resultSet, "last_world", "last_x", "last_y", "last_z", "last_pitch", "last_yaw"));
                data.put("logout_location", buildLocation(resultSet, "logout_world", "logout_x", "logout_y", "logout_z", "logout_pitch", "logout_yaw"));
                List<String> denyList = new ArrayList<>();
                String denyListStr = resultSet.getString("deny_list");
                if (!isNull(denyListStr) && !denyListStr.isEmpty()) {
                    for (String deny : denyListStr.split(",")) {
                        if (!deny.isEmpty()) denyList.add(deny);
                    }
                }
                data.put("deny_list", denyList);
                return data;
            }
        } catch (SQLException exception) {
            PLUGIN.getLogger().warning("[PMS-TPA] SQLite 查询失败：" + exception.getMessage());
            return null;
        } finally {
            LOCK.unlock();
        }
    }

    public static void insertOrUpdatePlayerData(UUID playerUUID, String playerName, String language, boolean setlang, String defaultHome, Location lastLocation, Location logoutLocation, List<String> denyList) {
        if (isNull(playerUUID)) return;
        execute("INSERT INTO player_data (player_uuid, player_name, language, setlang, default_home, deny_list," +
                        " last_world, last_x, last_y, last_z, last_pitch, last_yaw," +
                        " logout_world, logout_x, logout_y, logout_z, logout_pitch, logout_yaw)" +
                        " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)" +
                        " ON CONFLICT(player_uuid) DO UPDATE SET" +
                        " player_name = excluded.player_name, language = excluded.language, setlang = excluded.setlang," +
                        " default_home = excluded.default_home, deny_list = excluded.deny_list," +
                        " last_world = excluded.last_world, last_x = excluded.last_x, last_y = excluded.last_y, last_z = excluded.last_z, last_pitch = excluded.last_pitch, last_yaw = excluded.last_yaw," +
                        " logout_world = excluded.logout_world, logout_x = excluded.logout_x, logout_y = excluded.logout_y, logout_z = excluded.logout_z, logout_pitch = excluded.logout_pitch, logout_yaw = excluded.logout_yaw;",
                playerUUID.toString(), playerName, language, setlang ? 1 : 0, defaultHome, joinDenyList(denyList),
                locationField(lastLocation, 0), locationField(lastLocation, 1), locationField(lastLocation, 2), locationField(lastLocation, 3), locationField(lastLocation, 4), locationField(lastLocation, 5),
                locationField(logoutLocation, 0), locationField(logoutLocation, 1), locationField(logoutLocation, 2), locationField(logoutLocation, 3), locationField(logoutLocation, 4), locationField(logoutLocation, 5));
    }

    private static Object locationField(Location location, int index) {
        if (isNull(location)) return null;
        switch (index) {
            case 0: return location.getWorld().getName();
            case 1: return location.getX();
            case 2: return location.getY();
            case 3: return location.getZ();
            case 4: return (double) location.getPitch();
            case 5: return (double) location.getYaw();
            default: return null;
        }
    }

    public static List<UUID> getAllPlayerUUIDs() {
        List<UUID> playerUUIDs = new ArrayList<>();
        if (isNull(connection)) return playerUUIDs;
        LOCK.lock();
        try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT player_uuid FROM player_data");
             ResultSet resultSet = preparedStatement.executeQuery()) {
            while (resultSet.next()) {
                try {
                    playerUUIDs.add(UUID.fromString(resultSet.getString("player_uuid")));
                } catch (IllegalArgumentException ignored) {
                }
            }
        } catch (SQLException exception) {
            PLUGIN.getLogger().warning("[PMS-TPA] SQLite 查询失败：" + exception.getMessage());
        } finally {
            LOCK.unlock();
        }
        return playerUUIDs;
    }

    // ============================== 家 ==============================

    public static void insertOrUpdateHome(UUID playerUUID, String homeName, Location location) {
        if (isNull(playerUUID) || isNull(homeName) || isNull(location)) return;
        execute("INSERT INTO homes (player_uuid, home_name, world, x, y, z, pitch, yaw) VALUES (?,?,?,?,?,?,?,?)" +
                        " ON CONFLICT(player_uuid, home_name) DO UPDATE SET world = excluded.world, x = excluded.x, y = excluded.y, z = excluded.z, pitch = excluded.pitch, yaw = excluded.yaw;",
                playerUUID.toString(), homeName, location.getWorld().getName(), location.getX(), location.getY(), location.getZ(),
                (double) location.getPitch(), (double) location.getYaw());
    }

    public static void deleteHome(UUID playerUUID, String homeName) {
        if (isNull(playerUUID) || isNull(homeName)) return;
        execute("DELETE FROM homes WHERE player_uuid = ? AND home_name = ?;", playerUUID.toString(), homeName);
    }

    public static Map<String, Location> loadHomes(UUID playerUUID) {
        Map<String, Location> homes = new HashMap<>();
        if (isNull(connection)) return homes;
        LOCK.lock();
        try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT * FROM homes WHERE player_uuid = ?")) {
            preparedStatement.setString(1, playerUUID.toString());
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    Location location = buildLocation(resultSet, "world", "x", "y", "z", "pitch", "yaw");
                    if (!isNull(location)) homes.put(resultSet.getString("home_name"), location);
                }
            }
        } catch (SQLException exception) {
            PLUGIN.getLogger().warning("[PMS-TPA] SQLite 查询失败：" + exception.getMessage());
        } finally {
            LOCK.unlock();
        }
        return homes;
    }

    // ============================== 传送点 ==============================

    public static void insertOrUpdateWarp(String warpName, Location location) {
        if (isNull(warpName) || isNull(location)) return;
        execute("INSERT INTO warps (warp_name, world, x, y, z, pitch, yaw) VALUES (?,?,?,?,?,?,?)" +
                        " ON CONFLICT(warp_name) DO UPDATE SET world = excluded.world, x = excluded.x, y = excluded.y, z = excluded.z, pitch = excluded.pitch, yaw = excluded.yaw;",
                warpName, location.getWorld().getName(), location.getX(), location.getY(), location.getZ(),
                (double) location.getPitch(), (double) location.getYaw());
    }

    public static void deleteWarp(String warpName) {
        if (isNull(warpName)) return;
        execute("DELETE FROM warps WHERE warp_name = ?;", warpName);
    }

    public static Map<String, Location> loadAllWarps() {
        Map<String, Location> warps = new HashMap<>();
        if (isNull(connection)) return warps;
        LOCK.lock();
        try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT * FROM warps");
             ResultSet resultSet = preparedStatement.executeQuery()) {
            while (resultSet.next()) {
                Location location = buildLocation(resultSet, "world", "x", "y", "z", "pitch", "yaw");
                if (!isNull(location)) warps.put(resultSet.getString("warp_name"), location);
            }
        } catch (SQLException exception) {
            PLUGIN.getLogger().warning("[PMS-TPA] SQLite 查询失败：" + exception.getMessage());
        } finally {
            LOCK.unlock();
        }
        return warps;
    }

    // ============================== 主城 ==============================

    public static void insertOrUpdateSpawn(Location location) {
        if (isNull(location)) return;
        execute("INSERT INTO spawn (id, world, x, y, z, pitch, yaw) VALUES (1,?,?,?,?,?,?)" +
                        " ON CONFLICT(id) DO UPDATE SET world = excluded.world, x = excluded.x, y = excluded.y, z = excluded.z, pitch = excluded.pitch, yaw = excluded.yaw;",
                location.getWorld().getName(), location.getX(), location.getY(), location.getZ(),
                (double) location.getPitch(), (double) location.getYaw());
    }

    public static void deleteSpawn() {
        execute("DELETE FROM spawn WHERE id = 1;");
    }

    public static Location loadSpawn() {
        if (isNull(connection)) return null;
        LOCK.lock();
        try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT * FROM spawn WHERE id = 1");
             ResultSet resultSet = preparedStatement.executeQuery()) {
            return resultSet.next() ? buildLocation(resultSet, "world", "x", "y", "z", "pitch", "yaw") : null;
        } catch (SQLException exception) {
            PLUGIN.getLogger().warning("[PMS-TPA] SQLite 查询失败：" + exception.getMessage());
            return null;
        } finally {
            LOCK.unlock();
        }
    }

    // ============================== 旧版 yml 数据迁移 ==============================

    private static void copyLegacyData(Plugin plugin) {
        File dataFolder = plugin.getDataFolder();
        File configFile = new File(dataFolder, "config.yml");
        if (configFile.exists()) return;
        File legacyFolder = new File(dataFolder.getParentFile(), "TPA");
        if (!legacyFolder.exists()) return;
        plugin.getLogger().info("[PMS-TPA] 检测到旧版插件文件夹 plugins/TPA，正在迁移其配置与数据文件...");
        copyRecursively(legacyFolder, dataFolder);
    }

    private static void copyRecursively(File source, File target) {
        if (source.isDirectory()) {
            if (!target.exists()) target.mkdirs();
            File[] children = source.listFiles();
            if (!isNull(children)) {
                for (File child : children) {
                    copyRecursively(child, new File(target, child.getName()));
                }
            }
        } else if (source.isFile()) {
            try {
                Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
            }
        }
    }

    private static void migrateDatabaseFile(Plugin plugin, File dataFolder) {
        File oldDatabaseFile = new File(plugin.getDataFolder(), "data.db");
        File newDatabaseFile = new File(dataFolder, "data.db");
        if (!oldDatabaseFile.exists() || newDatabaseFile.exists()) return;
        try {
            Files.move(oldDatabaseFile.toPath(), newDatabaseFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().info("[PMS-TPA] 检测到旧位置的数据库文件，已迁移到 plugins/PMS-TPA/data/data.db");
        } catch (IOException ignored) {
        }
    }

    private static void migrateYmlData() {
        migrateWarpYml();
        migrateSpawnYml();
        migratePlayerYmls();
        migrateOldHomeYml();
        migrateOldLastLocationYml();
    }

    private static void migrateWarpYml() {
        if (count("warps") > 0) return;
        File warpFile = new File(PLUGIN.getDataFolder(), "warp.yml");
        if (!warpFile.exists()) return;
        FileConfiguration configuration = YamlConfiguration.loadConfiguration(warpFile);
        for (String warpName : configuration.getKeys(false)) {
            Location location = readLocation(configuration, warpName);
            if (!isNull(location)) insertOrUpdateWarp(warpName, location);
        }
    }

    private static void migrateSpawnYml() {
        if (!isNull(loadSpawn())) return;
        File spawnFile = new File(PLUGIN.getDataFolder(), "spawn.yml");
        if (!spawnFile.exists()) return;
        FileConfiguration configuration = YamlConfiguration.loadConfiguration(spawnFile);
        Location location = readLocation(configuration, "spawn");
        if (!isNull(location)) insertOrUpdateSpawn(location);
    }

    private static void migratePlayerYmls() {
        File playerDataFolder = new File(PLUGIN.getDataFolder(), "playerdata");
        File[] files = playerDataFolder.listFiles();
        if (isNull(files)) return;
        for (File file : files) {
            String fileName = file.getName().replace(".yml", "");
            if (!fileName.matches(PLAYER_UUID_REGEX)) continue;
            UUID playerUUID;
            try {
                playerUUID = UUID.fromString(fileName);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            if (containsPlayerData(playerUUID)) continue;
            FileConfiguration configuration = YamlConfiguration.loadConfiguration(file);
            String language = configuration.getString("language");
            if (isNull(language)) language = configuration.getString("lang");
            String playerName = configuration.getString("player_name");
            boolean setlang = configuration.getBoolean("setlang");
            String defaultHome = configuration.getString("default_home");
            List<String> denyList = new ArrayList<>(configuration.getStringList("deny_list"));
            if (configuration.contains("denys")) {
                for (String key : configuration.getKeys(true)) {
                    if (key.startsWith("denys.")) {
                        String denied = key.substring("denys.".length());
                        if (!denied.contains(".")) denyList.add(denied);
                    }
                }
            }
            insertOrUpdatePlayerData(playerUUID, playerName, isNull(language) ? "zh_CN" : language, setlang, defaultHome,
                    readLocation(configuration, "last_location"), readLocation(configuration, "logout_location"), denyList);
            for (String key : configuration.getKeys(true)) {
                if (key.startsWith("homes.")) {
                    String homeName = key.substring("homes.".length());
                    if (!homeName.contains(".")) {
                        Location location = readLocation(configuration, "homes." + homeName);
                        if (!isNull(location)) insertOrUpdateHome(playerUUID, homeName, location);
                    }
                }
            }
        }
    }

    private static void migrateOldHomeYml() {
        if (count("player_data") > 0) return;
        File homeFile = new File(PLUGIN.getDataFolder(), "home.yml");
        if (!homeFile.exists()) return;
        FileConfiguration configuration = YamlConfiguration.loadConfiguration(homeFile);
        for (String playerName : configuration.getKeys(false)) {
            UUID playerUUID = Bukkit.getOfflinePlayer(playerName).getUniqueId();
            boolean defaultSet = false;
            for (String key : configuration.getKeys(true)) {
                String prefix = playerName + ".";
                if (!key.startsWith(prefix)) continue;
                String homeName = key.substring(prefix.length());
                if (homeName.contains(".")) continue;
                Location location = readLocation(configuration, prefix + homeName);
                if (isNull(location)) continue;
                if (!defaultSet && !containsPlayerData(playerUUID)) {
                    insertOrUpdatePlayerData(playerUUID, playerName, "zh_CN", false, homeName, null, null, new ArrayList<>());
                    defaultSet = true;
                }
                insertOrUpdateHome(playerUUID, homeName, location);
            }
        }
    }

    private static void migrateOldLastLocationYml() {
        if (count("player_data") > 0) return;
        File lastLocationFile = new File(PLUGIN.getDataFolder(), "last_location.yml");
        if (!lastLocationFile.exists()) return;
        FileConfiguration configuration = YamlConfiguration.loadConfiguration(lastLocationFile);
        for (String playerName : configuration.getKeys(false)) {
            Location location = readLocation(configuration, playerName);
            if (isNull(location)) continue;
            UUID playerUUID = Bukkit.getOfflinePlayer(playerName).getUniqueId();
            if (!containsPlayerData(playerUUID)) {
                insertOrUpdatePlayerData(playerUUID, playerName, "zh_CN", false, null, location, null, new ArrayList<>());
            }
        }
    }

    // ============================== HuskHomes 数据迁移 ==============================

    public static void migrateHuskHomesData(CommandSender sender) {
        List<File> databaseFiles = findHuskHomesDatabases();
        if (databaseFiles.isEmpty()) {
            SendMessageUtil.migrationNoFile(sender);
            return;
        }
        for (File databaseFile : databaseFiles) {
            migrateHuskHomesDatabase(sender, databaseFile);
        }
    }

    private static List<File> findHuskHomesDatabases() {
        List<File> files = new ArrayList<>();
        File dataFolder = new File(PLUGIN.getDataFolder(), "data");
        File huskFolder = new File(PLUGIN.getDataFolder().getParentFile(), "HuskHomes");
        File[] candidates = new File[]{
                new File(dataFolder, "HuskHomesData.db"),
                new File(dataFolder, "HuskHomesData.h2.mv.db"),
                new File(dataFolder, "HuskHomesData.h2.db"),
                new File(huskFolder, "HuskHomesData.db"),
                new File(huskFolder, "HuskHomesData.h2.mv.db"),
                new File(huskFolder, "HuskHomesData.h2.db")
        };
        for (File candidate : candidates) {
            if (candidate.exists() && !files.contains(candidate)) files.add(candidate);
        }
        return files;
    }

    private static boolean isH2Database(File databaseFile) {
        String name = databaseFile.getName().toLowerCase();
        return name.endsWith(".mv.db") || name.endsWith(".h2.db");
    }

    private static void migrateHuskHomesDatabase(CommandSender sender, File databaseFile) {
        SendMessageUtil.migrationStart(sender, databaseFile.getName());
        String url;
        try {
            if (isH2Database(databaseFile)) {
                Class.forName("org.h2.Driver");
                String path = databaseFile.getAbsolutePath();
                url = "jdbc:h2:" + path.substring(0, path.length() - 6);
            } else {
                Class.forName("org.sqlite.JDBC");
                url = "jdbc:sqlite:" + databaseFile.getAbsolutePath();
            }
        } catch (ClassNotFoundException exception) {
            SendMessageUtil.migrationError(sender, "数据库驱动加载失败：" + exception.getMessage());
            return;
        }
        int homes = 0, warps = 0, lastPositions = 0;
        try (Connection huskConnection = DriverManager.getConnection(url)) {
            if (!isH2Database(databaseFile)) {
                try (Statement statement = huskConnection.createStatement()) {
                    statement.execute("PRAGMA query_only = ON;");
                }
            }
            if (!hasTable(huskConnection, "huskhomes_position_data") || !hasTable(huskConnection, "huskhomes_saved_positions")
                    || !hasTable(huskConnection, "huskhomes_homes") || !hasTable(huskConnection, "huskhomes_warps")) {
                SendMessageUtil.migrationError(sender, "该文件不是 HuskHomes 数据库或表结构不完整，已跳过：" + databaseFile.getName());
                return;
            }
            if (count("homes") == 0) homes = migrateHuskHomesHomes(huskConnection);
            if (count("warps") == 0) warps = migrateHuskHomesWarps(huskConnection);
            lastPositions = migrateHuskHomesLastPositions(huskConnection);
        } catch (Exception exception) {
            SendMessageUtil.migrationError(sender, exception.getMessage());
            PLUGIN.getLogger().warning("[PMS-TPA] HuskHomes 数据迁移失败：" + exception.getMessage());
            return;
        }
        SendMessageUtil.migrationDone(sender, homes, warps, lastPositions);
    }

    private static boolean hasTable(Connection huskConnection, String tableName) {
        try (PreparedStatement preparedStatement = huskConnection.prepareStatement("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?")) {
            preparedStatement.setString(1, tableName);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            return false;
        }
    }

    private static int migrateHuskHomesHomes(Connection huskConnection) throws SQLException {
        int migrated = 0;
        String sql = "SELECT h.owner_uuid, sp.name, p.world_name, p.x, p.y, p.z, p.pitch, p.yaw " +
                "FROM huskhomes_homes h " +
                "INNER JOIN huskhomes_saved_positions sp ON h.saved_position_id = sp.id " +
                "INNER JOIN huskhomes_position_data p ON sp.position_id = p.id;";
        try (PreparedStatement preparedStatement = huskConnection.prepareStatement(sql);
             ResultSet resultSet = preparedStatement.executeQuery()) {
            while (resultSet.next()) {
                String ownerUuid = resultSet.getString("owner_uuid");
                String homeName = resultSet.getString("name");
                if (isNull(ownerUuid) || isNull(homeName)) continue;
                UUID playerUUID;
                try {
                    playerUUID = UUID.fromString(ownerUuid);
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                insertHuskHomesHome(playerUUID, homeName, resultSet.getString("world_name"),
                        resultSet.getDouble("x"), resultSet.getDouble("y"), resultSet.getDouble("z"),
                        resultSet.getDouble("pitch"), resultSet.getDouble("yaw"));
                migrated++;
            }
        }
        return migrated;
    }

    private static int migrateHuskHomesWarps(Connection huskConnection) throws SQLException {
        int migrated = 0;
        String sql = "SELECT sp.name, p.world_name, p.x, p.y, p.z, p.pitch, p.yaw " +
                "FROM huskhomes_warps w " +
                "INNER JOIN huskhomes_saved_positions sp ON w.saved_position_id = sp.id " +
                "INNER JOIN huskhomes_position_data p ON sp.position_id = p.id;";
        try (PreparedStatement preparedStatement = huskConnection.prepareStatement(sql);
             ResultSet resultSet = preparedStatement.executeQuery()) {
            while (resultSet.next()) {
                String warpName = resultSet.getString("name");
                if (isNull(warpName)) continue;
                insertHuskHomesWarp(warpName, resultSet.getString("world_name"),
                        resultSet.getDouble("x"), resultSet.getDouble("y"), resultSet.getDouble("z"),
                        resultSet.getDouble("pitch"), resultSet.getDouble("yaw"));
                migrated++;
            }
        }
        return migrated;
    }

    private static int migrateHuskHomesLastPositions(Connection huskConnection) throws SQLException {
        int migrated = 0;
        String sql = "SELECT u.uuid, u.username, p.world_name, p.x, p.y, p.z, p.pitch, p.yaw " +
                "FROM huskhomes_users u " +
                "INNER JOIN huskhomes_position_data p ON u.last_position = p.id;";
        try (PreparedStatement preparedStatement = huskConnection.prepareStatement(sql);
             ResultSet resultSet = preparedStatement.executeQuery()) {
            while (resultSet.next()) {
                String uuid = resultSet.getString("uuid");
                if (isNull(uuid)) continue;
                UUID playerUUID;
                try {
                    playerUUID = UUID.fromString(uuid);
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                insertHuskHomesLastPosition(playerUUID, resultSet.getString("username"), resultSet.getString("world_name"),
                        resultSet.getDouble("x"), resultSet.getDouble("y"), resultSet.getDouble("z"),
                        resultSet.getDouble("pitch"), resultSet.getDouble("yaw"));
                migrated++;
            }
        }
        return migrated;
    }

    private static void insertHuskHomesHome(UUID playerUUID, String homeName, String world, double x, double y, double z, double pitch, double yaw) {
        execute("INSERT INTO homes (player_uuid, home_name, world, x, y, z, pitch, yaw) VALUES (?,?,?,?,?,?,?,?)" +
                        " ON CONFLICT(player_uuid, home_name) DO UPDATE SET world = excluded.world, x = excluded.x, y = excluded.y, z = excluded.z, pitch = excluded.pitch, yaw = excluded.yaw;",
                playerUUID.toString(), homeName, world, x, y, z, pitch, yaw);
    }

    private static void insertHuskHomesWarp(String warpName, String world, double x, double y, double z, double pitch, double yaw) {
        execute("INSERT INTO warps (warp_name, world, x, y, z, pitch, yaw) VALUES (?,?,?,?,?,?,?)" +
                        " ON CONFLICT(warp_name) DO UPDATE SET world = excluded.world, x = excluded.x, y = excluded.y, z = excluded.z, pitch = excluded.pitch, yaw = excluded.yaw;",
                warpName, world, x, y, z, pitch, yaw);
    }

    private static void insertHuskHomesLastPosition(UUID playerUUID, String playerName, String world, double x, double y, double z, double pitch, double yaw) {
        execute("INSERT INTO player_data (player_uuid, player_name, last_world, last_x, last_y, last_z, last_pitch, last_yaw) VALUES (?,?,?,?,?,?,?,?)" +
                        " ON CONFLICT(player_uuid) DO UPDATE SET player_name = excluded.player_name," +
                        " last_world = excluded.last_world, last_x = excluded.last_x, last_y = excluded.last_y, last_z = excluded.last_z, last_pitch = excluded.last_pitch, last_yaw = excluded.last_yaw" +
                        " WHERE player_data.last_world IS NULL;",
                playerUUID.toString(), playerName, world, x, y, z, pitch, yaw);
    }
}
